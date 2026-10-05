package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhOrderExecMapper;
import com.fc.v2.mapper.auto.TMhOrderMapper;
import com.fc.v2.mapper.auto.TMhOrderTransferMapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.auto.TMhOrderTransfer;
import com.fc.v2.model.custom.OrderResult;
import com.fc.v2.service.ITMhOrderService;
import com.fc.v2.util.SnowflakeIdWorker;

/**
 * 住院医嘱执行闭环 Service 业务层处理。
 *
 * <p>全院口径就这一处，定死如下，页面、定时任务、补登都走这里：</p>
 * <ul>
 *   <li>执行笔幂等：一次计划执行 = (order_no, dose_seq)，全库唯一键兜底。点两次、
 *       交接班两人同时提交、重试，撞的都是同一个键；撞键按重放返回已落的那笔。</li>
 *   <li>闭环判定单一口径：一张单只适用一种算法（order_type 定死）。按次数型看
 *       有效执行笔数（执行人非空）达没达 need_count；按时段型看过没过 period_end
 *       且期内有没有有效执行。判定只有 {@link #deriveCloseStatus} 一处。</li>
 *   <li>转科归属：医嘱随病人走，owner_dept 是唯一认领方，条件更新换边并写移交流水，
 *       任何时点一张单只挂一头；执行笔 dept_no 是发生时快照，不随转科改。</li>
 *   <li>审方驳回后坚持要发：必须强制放行，审批人/时点/理由三样落单才准发药执行。</li>
 *   <li>往年补录：缺执行人的补录笔不算闭环进度，单进补录待核，补登后按同一口径重算。</li>
 *   <li>执行落笔与闭环推进同一事务；闭环状态由执行笔派生重算、条件更新兜底，
 *       重试重放不改变终态。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-05
 */
@Service
public class TMhOrderServiceImpl implements ITMhOrderService {

    @javax.annotation.Resource
    private TMhOrderMapper mhOrderMapper;

    @javax.annotation.Resource
    private TMhOrderExecMapper mhOrderExecMapper;

    @javax.annotation.Resource
    private TMhOrderTransferMapper mhOrderTransferMapper;

    /**
     * 时段比较统一走墙钟字符串（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TMhOrder selectByOrderNo(String orderNo) {
        if (isBlank(orderNo)) {
            return null;
        }
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        return this.mhOrderMapper.selectOne(q);
    }

    @Override
    public List<TMhOrder> selectOrderList(QueryWrapper<TMhOrder> queryWrapper) {
        List<TMhOrder> rows = this.mhOrderMapper.selectList(queryWrapper);
        return rows == null ? new ArrayList<TMhOrder>() : rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult createOrder(String orderNo, String patientNo, String ownerDept, Integer orderType,
                                   Integer needCount, Date periodStart, Date periodEnd,
                                   boolean backfill, String operator) {
        if (isBlank(patientNo)) {
            return OrderResult.rejected(orderNo, "退回：患者底档代号必填");
        }
        if (isBlank(ownerDept)) {
            return OrderResult.rejected(orderNo, "退回：归属科室必填，医嘱必须有人认领");
        }
        if (orderType == null
                || (orderType.intValue() != TMhOrder.TYPE_COUNT && orderType.intValue() != TMhOrder.TYPE_PERIOD)) {
            return OrderResult.rejected(orderNo, "退回：闭环算法只认 1按次数 / 2按时段，一张单定死一种");
        }
        if (orderType.intValue() == TMhOrder.TYPE_COUNT && (needCount == null || needCount.intValue() < 1)) {
            return OrderResult.rejected(orderNo, "退回：按次数型应执行次数必须 >= 1");
        }
        if (orderType.intValue() == TMhOrder.TYPE_PERIOD) {
            if (periodStart == null || periodEnd == null) {
                return OrderResult.rejected(orderNo, "退回：按时段型时段起止必填");
            }
            if (ts(periodStart).compareTo(ts(periodEnd)) >= 0) {
                return OrderResult.rejected(orderNo, "退回：时段起必须早于时段止");
            }
        }

        TMhOrder order = new TMhOrder();
        order.setOrderNo(isBlank(orderNo) ? nextOrderNo() : orderNo.trim());
        order.setPatientNo(patientNo.trim());
        order.setOwnerDept(ownerDept.trim());
        order.setOrderType(orderType);
        order.setNeedCount(orderType.intValue() == TMhOrder.TYPE_COUNT ? needCount : null);
        order.setPeriodStart(orderType.intValue() == TMhOrder.TYPE_PERIOD ? periodStart : null);
        order.setPeriodEnd(orderType.intValue() == TMhOrder.TYPE_PERIOD ? periodEnd : null);
        order.setBackfill(backfill ? 1 : 0);
        order.setCloseStatus(TMhOrder.CLOSE_OPEN);
        order.setStatus(TMhOrder.STATUS_ACTIVE);
        order.setDelFlag(0);
        if (backfill) {
            // 往年补录：审方发药从略置妥并在备注明写，执行人靠补登，缺着不自动闭环
            order.setReviewStatus(TMhOrder.REVIEW_PASS);
            order.setDispenseStatus(TMhOrder.DISPENSE_YES);
            order.setRemark("往年补录，审方发药从略");
        } else {
            order.setReviewStatus(TMhOrder.REVIEW_PENDING);
            order.setDispenseStatus(TMhOrder.DISPENSE_NO);
        }
        order.setCreateBy(operator);

        try {
            this.mhOrderMapper.insert(order);
        } catch (RuntimeException dup) {
            // 单号唯一键撞了：不许另起一单，退回让护士用原单
            if (selectByOrderNo(order.getOrderNo()) != null) {
                return OrderResult.rejected(order.getOrderNo(), "退回：单号已存在（" + order.getOrderNo() + "）");
            }
            throw dup;
        }
        OrderResult r = OrderResult.ok(order.getOrderNo(),
                backfill ? "补录单已落：审方发药从略，执行人补登前不自动闭环" : "医嘱已落：待审方");
        r.setCloseStatus(order.getCloseStatus());
        r.setOwnerDept(order.getOwnerDept());
        r.setExecCount(0L);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult review(String orderNo, boolean pass, String auditor, String comment) {
        TMhOrder order = selectByOrderNo(orderNo);
        if (order == null) {
            return OrderResult.rejected(orderNo, "退回：医嘱单不存在（" + orderNo + "）");
        }
        if (order.getReviewStatus() == null
                || order.getReviewStatus().intValue() != TMhOrder.REVIEW_PENDING) {
            return OrderResult.rejected(orderNo, "退回：该单已审讫，重复审方不收");
        }
        if (isBlank(auditor)) {
            return OrderResult.rejected(orderNo, "退回：审方人必填，谁审的必须落单");
        }
        order.setReviewStatus(pass ? TMhOrder.REVIEW_PASS : TMhOrder.REVIEW_REJECT);
        order.setReviewBy(auditor.trim());
        order.setReviewAt(new Date());
        order.setReviewComment(comment);
        order.setUpdateBy(auditor.trim());
        this.mhOrderMapper.updateById(order);
        return fill(OrderResult.ok(orderNo, pass ? "审方通过" : "审方已驳回"), order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult overrideRelease(String orderNo, String operator, String reason) {
        TMhOrder order = selectByOrderNo(orderNo);
        if (order == null) {
            return OrderResult.rejected(orderNo, "退回：医嘱单不存在（" + orderNo + "）");
        }
        if (order.getReviewStatus() == null
                || order.getReviewStatus().intValue() != TMhOrder.REVIEW_REJECT) {
            return OrderResult.rejected(orderNo, "退回：只有被驳回的单才需要强制放行");
        }
        // 审计就卡在这里：谁批的、为什么，缺一样都不放
        if (isBlank(operator)) {
            return OrderResult.rejected(orderNo, "退回：强制放行必须落审批人，事后要查得到谁批的");
        }
        if (isBlank(reason)) {
            return OrderResult.rejected(orderNo, "退回：强制放行必须写明理由");
        }
        order.setReviewStatus(TMhOrder.REVIEW_OVERRIDE);
        order.setOverrideBy(operator.trim());
        order.setOverrideAt(new Date());
        order.setOverrideReason(reason.trim());
        order.setUpdateBy(operator.trim());
        this.mhOrderMapper.updateById(order);
        return fill(OrderResult.ok(orderNo, "已强制放行：审批人 " + operator.trim() + " 落单"), order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult dispense(String orderNo, String pharmacist) {
        TMhOrder order = selectByOrderNo(orderNo);
        if (order == null) {
            return OrderResult.rejected(orderNo, "退回：医嘱单不存在（" + orderNo + "）");
        }
        if (!reviewPassed(order)) {
            return OrderResult.rejected(orderNo, "退回：审方未通过，不许发药");
        }
        if (order.getDispenseStatus() != null
                && order.getDispenseStatus().intValue() == TMhOrder.DISPENSE_YES) {
            return fill(OrderResult.replayed(orderNo, "重放：该单已发过药"), order);
        }
        if (isBlank(pharmacist)) {
            return OrderResult.rejected(orderNo, "退回：发药人必填");
        }
        order.setDispenseStatus(TMhOrder.DISPENSE_YES);
        order.setDispenseBy(pharmacist.trim());
        order.setDispensedAt(new Date());
        order.setUpdateBy(pharmacist.trim());
        this.mhOrderMapper.updateById(order);
        return fill(OrderResult.ok(orderNo, "已发药"), order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult execute(String orderNo, Integer doseSeq, String execBy, Date execAt) {
        TMhOrder order = selectByOrderNo(orderNo);
        if (order == null) {
            return OrderResult.rejected(orderNo, "退回：医嘱单不存在（" + orderNo + "）");
        }
        if (order.getStatus() == null || order.getStatus().intValue() != TMhOrder.STATUS_ACTIVE) {
            return OrderResult.rejected(orderNo, "退回：该单已停嘱，不许再执行");
        }
        if (!reviewPassed(order)) {
            return OrderResult.rejected(orderNo, order.getReviewStatus() != null
                    && order.getReviewStatus().intValue() == TMhOrder.REVIEW_REJECT
                    ? "退回：审方已驳回，未走强制放行不许执行"
                    : "退回：审方未通过，不许执行");
        }
        if (order.getDispenseStatus() == null
                || order.getDispenseStatus().intValue() != TMhOrder.DISPENSE_YES) {
            return OrderResult.rejected(orderNo, "退回：未发药，不许执行");
        }
        if (doseSeq == null || doseSeq.intValue() <= 0) {
            return OrderResult.rejected(orderNo, "退回：计划内第几次必填且从 1 起");
        }
        if (order.getOrderType() != null && order.getOrderType().intValue() == TMhOrder.TYPE_COUNT
                && order.getNeedCount() != null && doseSeq.intValue() > order.getNeedCount().intValue()) {
            return OrderResult.rejected(orderNo,
                    "退回：第 " + doseSeq + " 次超出应执行次数（" + order.getNeedCount() + "）");
        }
        boolean backfillOrder = order.getBackfill() != null && order.getBackfill().intValue() == 1;
        if (isBlank(execBy) && !backfillOrder) {
            return OrderResult.rejected(orderNo, "退回：执行人必填（往年补录单才允许空着后补）");
        }

        // 幂等闸口：同一计划次数已落过，原样返回那笔，绝不落第二笔
        TMhOrderExec existing = findExec(orderNo, doseSeq);
        if (existing != null) {
            return replayedExec(order, existing);
        }

        TMhOrderExec row = new TMhOrderExec();
        row.setOrderNo(orderNo);
        row.setDoseSeq(doseSeq);
        row.setExecBy(isBlank(execBy) ? null : execBy.trim());
        row.setExecAt(execAt == null ? new Date() : execAt);
        // 归属快照：执行发生在哪头就记哪头，转科不改历史，交接班对账看这里
        row.setDeptNo(order.getOwnerDept());
        row.setBackfill(backfillOrder ? 1 : 0);
        row.setDelFlag(0);
        row.setCreateBy(isBlank(execBy) ? "backfill" : execBy.trim());
        try {
            this.mhOrderExecMapper.insert(row);
        } catch (RuntimeException dup) {
            // 交接班两人同时提交同一针：唯一键只放一笔过去，撞键的按重放收场
            TMhOrderExec again = findExec(orderNo, doseSeq);
            if (again != null) {
                return replayedExec(order, again);
            }
            throw dup;
        }

        // 落笔与闭环推进同一事务：闭环状态由执行笔派生重算，条件更新兜底并发
        int closeStatus = recomputeClose(order, new Date());
        OrderResult r = OrderResult.ok(orderNo, closeStatus == TMhOrder.CLOSE_DONE
                ? "执行落笔，医嘱闭环"
                : closeStatus == TMhOrder.CLOSE_BACKFILL_PENDING
                ? "执行落笔：补录笔缺执行人，单进补录待核"
                : "执行落笔");
        r.setExecId(row.getId());
        r.setDoseSeq(doseSeq);
        r.setCloseStatus(closeStatus);
        r.setExecCount(countExec(orderNo));
        r.setOwnerDept(order.getOwnerDept());
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult transfer(String orderNo, String toDept, String operator) {
        TMhOrder order = selectByOrderNo(orderNo);
        if (order == null) {
            return OrderResult.rejected(orderNo, "退回：医嘱单不存在（" + orderNo + "）");
        }
        if (order.getStatus() == null || order.getStatus().intValue() != TMhOrder.STATUS_ACTIVE) {
            return OrderResult.rejected(orderNo, "退回：已停嘱的单不转科");
        }
        if (isBlank(toDept)) {
            return OrderResult.rejected(orderNo, "退回：承接科室必填");
        }
        String fromDept = order.getOwnerDept();
        if (toDept.trim().equals(fromDept)) {
            OrderResult r = OrderResult.replayed(orderNo, "重放：该单已在 " + toDept.trim() + " 名下");
            r.setOwnerDept(fromDept);
            r.setCloseStatus(order.getCloseStatus());
            r.setExecCount(countExec(orderNo));
            return r;
        }
        // 归属取舍定死为"医嘱随病人走"：owner_dept 换边，条件更新防两科同时抢单
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", order.getId());
        uw.eq("owner_dept", fromDept);
        uw.eq("status", TMhOrder.STATUS_ACTIVE);
        uw.eq("del_flag", 0);
        uw.set("owner_dept", toDept.trim());
        uw.set("update_by", isBlank(operator) ? null : operator.trim());
        int moved = this.mhOrderMapper.update(null, uw);
        if (moved == 0) {
            return OrderResult.rejected(orderNo, "退回：归属刚被他人改动，请刷新后重试");
        }

        TMhOrderTransfer flow = new TMhOrderTransfer();
        flow.setOrderNo(orderNo);
        flow.setFromDept(fromDept);
        flow.setToDept(toDept.trim());
        flow.setMovedBy(isBlank(operator) ? null : operator.trim());
        flow.setMovedAt(new Date());
        flow.setDelFlag(0);
        flow.setCreateBy(isBlank(operator) ? null : operator.trim());
        this.mhOrderTransferMapper.insert(flow);

        OrderResult r = OrderResult.ok(orderNo, "已移交：" + fromDept + " → " + toDept.trim()
                + "，原科室即刻摘除，承接科室认领");
        r.setOwnerDept(toDept.trim());
        r.setCloseStatus(order.getCloseStatus());
        r.setExecCount(countExec(orderNo));
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult amendExecutor(Long execId, String execBy, String operator) {
        if (execId == null) {
            return OrderResult.rejected(null, "退回：执行笔主键必填");
        }
        TMhOrderExec row = this.mhOrderExecMapper.selectById(execId);
        if (row == null || row.getDelFlag() == null || row.getDelFlag().intValue() != 0) {
            return OrderResult.rejected(null, "退回：执行笔不存在（" + execId + "）");
        }
        if (row.getBackfill() == null || row.getBackfill().intValue() != 1) {
            return OrderResult.rejected(row.getOrderNo(), "退回：只有补录笔允许补登执行人，正常笔谁执行就是谁");
        }
        if (!isBlank(row.getExecBy())) {
            OrderResult replay = OrderResult.replayed(row.getOrderNo(), "重放：该笔已有执行人（" + row.getExecBy() + "）");
            replay.setExecId(row.getId());
            return replay;
        }
        if (isBlank(execBy)) {
            return OrderResult.rejected(row.getOrderNo(), "退回：补登的执行人必填");
        }
        // 条件更新：两人同时补登同一笔，只放一个过去
        UpdateWrapper<TMhOrderExec> uw = new UpdateWrapper<TMhOrderExec>();
        uw.eq("id", execId);
        uw.eq("del_flag", 0);
        uw.and(w -> w.isNull("exec_by").or().eq("exec_by", ""));
        uw.set("exec_by", execBy.trim());
        uw.set("update_by", isBlank(operator) ? null : operator.trim());
        int amended = this.mhOrderExecMapper.update(null, uw);
        if (amended == 0) {
            OrderResult replay = OrderResult.replayed(row.getOrderNo(), "重放：该笔执行人刚被他人补登");
            replay.setExecId(execId);
            return replay;
        }
        // 补登后按同一口径重算闭环：该闭环闭环，该待着继续待着
        TMhOrder order = selectByOrderNo(row.getOrderNo());
        int closeStatus = recomputeClose(order, new Date());
        OrderResult r = OrderResult.ok(row.getOrderNo(), "执行人已补登");
        r.setExecId(execId);
        r.setCloseStatus(closeStatus);
        r.setExecCount(countExec(row.getOrderNo()));
        r.setOwnerDept(order == null ? null : order.getOwnerDept());
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runCloseJob(Date at) {
        if (at == null) {
            return 0;
        }
        // 只收按时段型、在办、未闭环的到期单；按次数型闭环只由执行动作推进，补录待核留人工
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("del_flag", 0);
        q.eq("status", TMhOrder.STATUS_ACTIVE);
        q.eq("close_status", TMhOrder.CLOSE_OPEN);
        q.eq("order_type", TMhOrder.TYPE_PERIOD);
        List<TMhOrder> candidates = this.mhOrderMapper.selectList(q);
        int closed = 0;
        for (TMhOrder order : candidates) {
            if (order.getPeriodEnd() == null || ts(order.getPeriodEnd()).compareTo(ts(at)) > 0) {
                continue;
            }
            if (recomputeClose(order, at) == TMhOrder.CLOSE_DONE) {
                closed++;
            }
        }
        return closed;
    }

    @Override
    public long countExec(String orderNo) {
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        Integer n = this.mhOrderExecMapper.selectCount(q);
        return n == null ? 0L : n.longValue();
    }

    @Override
    public Map<String, Long> countExecMap(Collection<String> orderNos) {
        Map<String, Long> out = new HashMap<String, Long>();
        if (orderNos == null || orderNos.isEmpty()) {
            return out;
        }
        // 与详情同一条 COUNT 的分组版：列表的数就是详情的数，不经 JOIN 放大
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.select("order_no, COUNT(*) AS c");
        q.in("order_no", orderNos);
        q.eq("del_flag", 0);
        q.groupBy("order_no");
        for (Map<String, Object> row : this.mhOrderExecMapper.selectMaps(q)) {
            // H2 未加引号的列标转大写、MySQL 原样返回，两处都认，别让口径栽在大小写上
            Object no = row.get("order_no") != null ? row.get("order_no") : row.get("ORDER_NO");
            Object c = row.get("c") != null ? row.get("c") : row.get("C");
            if (no != null && c instanceof Number) {
                out.put(String.valueOf(no), Long.valueOf(((Number) c).longValue()));
            }
        }
        return out;
    }

    @Override
    public List<TMhOrderExec> listExec(String orderNo) {
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        q.orderByAsc("dose_seq");
        List<TMhOrderExec> rows = this.mhOrderExecMapper.selectList(q);
        return rows == null ? new ArrayList<TMhOrderExec>() : rows;
    }

    @Override
    public List<TMhOrder> listByDept(String ownerDept) {
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("del_flag", 0);
        q.eq("status", TMhOrder.STATUS_ACTIVE);
        if (!isBlank(ownerDept)) {
            // 归属只看 owner_dept 这一处：转科换边后，原科室清单即刻没有这张单
            q.eq("owner_dept", ownerDept.trim());
        }
        q.orderByAsc("id");
        List<TMhOrder> rows = this.mhOrderMapper.selectList(q);
        return rows == null ? new ArrayList<TMhOrder>() : rows;
    }

    @Override
    public List<TMhOrderTransfer> listTransfers(String orderNo) {
        QueryWrapper<TMhOrderTransfer> q = new QueryWrapper<TMhOrderTransfer>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        q.orderByAsc("moved_at");
        List<TMhOrderTransfer> rows = this.mhOrderTransferMapper.selectList(q);
        return rows == null ? new ArrayList<TMhOrderTransfer>() : rows;
    }

    // ---------------------------------------------------------------------
    // 内部口径
    // ---------------------------------------------------------------------

    /**
     * 闭环判定全院就这一处，一张单只适用一种算法：
     * 有缺执行人的补录笔 → 补录待核（不自动闭环，自动任务也不碰）；
     * 按次数型 → 有效执行笔数（执行人非空）达 need_count 才闭环；
     * 按时段型 → 过了 period_end（含到期那一刻）且期内有有效执行才闭环，期内零有效执行到期也不闭环。
     */
    private int deriveCloseStatus(TMhOrder order, Date at) {
        String orderNo = order.getOrderNo();
        if (hasMissingExecutorExec(orderNo)) {
            return TMhOrder.CLOSE_BACKFILL_PENDING;
        }
        long valid = countValidExec(orderNo);
        if (order.getOrderType() != null && order.getOrderType().intValue() == TMhOrder.TYPE_COUNT) {
            int need = order.getNeedCount() == null ? 0 : order.getNeedCount().intValue();
            return need > 0 && valid >= need ? TMhOrder.CLOSE_DONE : TMhOrder.CLOSE_OPEN;
        }
        if (order.getOrderType() != null && order.getOrderType().intValue() == TMhOrder.TYPE_PERIOD) {
            boolean due = order.getPeriodEnd() != null && ts(order.getPeriodEnd()).compareTo(ts(at)) <= 0;
            return due && valid >= 1 ? TMhOrder.CLOSE_DONE : TMhOrder.CLOSE_OPEN;
        }
        return TMhOrder.CLOSE_OPEN;
    }

    /**
     * 派生重算并条件更新：并发下两个事务算出的都是同一个派生值，谁写都一样；
     * 状态已被别人改成目标值时不再重复写。返回库里最新的闭环情形。
     */
    private int recomputeClose(TMhOrder order, Date at) {
        if (order == null) {
            return TMhOrder.CLOSE_OPEN;
        }
        int derived = deriveCloseStatus(order, at);
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", order.getId());
        uw.eq("del_flag", 0);
        uw.ne("close_status", derived);
        uw.set("close_status", derived);
        uw.set("closed_at", derived == TMhOrder.CLOSE_DONE ? at : null);
        this.mhOrderMapper.update(null, uw);
        return derived;
    }

    /** 审方通过或驳回后强制放行，才允许发药、执行 */
    private boolean reviewPassed(TMhOrder order) {
        return order.getReviewStatus() != null
                && (order.getReviewStatus().intValue() == TMhOrder.REVIEW_PASS
                || order.getReviewStatus().intValue() == TMhOrder.REVIEW_OVERRIDE);
    }

    /** 同一计划次数已落过的那笔（未删除），没有则 null */
    private TMhOrderExec findExec(String orderNo, Integer doseSeq) {
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_no", orderNo);
        q.eq("dose_seq", doseSeq);
        q.eq("del_flag", 0);
        return this.mhOrderExecMapper.selectOne(q);
    }

    /** 有效执行笔数：执行人非空的笔才算闭环进度（补录缺执行人的不算） */
    private long countValidExec(String orderNo) {
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        q.isNotNull("exec_by");
        q.ne("exec_by", "");
        Integer n = this.mhOrderExecMapper.selectCount(q);
        return n == null ? 0L : n.longValue();
    }

    /** 是否存在缺执行人的补录笔：有则单进补录待核，不自动闭环 */
    private boolean hasMissingExecutorExec(String orderNo) {
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_no", orderNo);
        q.eq("del_flag", 0);
        q.eq("backfill", 1);
        q.and(w -> w.isNull("exec_by").or().eq("exec_by", ""));
        Integer n = this.mhOrderExecMapper.selectCount(q);
        return n != null && n.longValue() > 0;
    }

    /** 重放收场：返回已落的那笔与库里最新闭环情形，调用方当成功处理但不重复落账 */
    private OrderResult replayedExec(TMhOrder order, TMhOrderExec existing) {
        // 并发下手上这份单可能是撞键前的旧快照，闭环情形以库里最新为准
        TMhOrder fresh = selectByOrderNo(order.getOrderNo());
        OrderResult r = OrderResult.replayed(order.getOrderNo(),
                "重放：第 " + existing.getDoseSeq() + " 次早已落笔，不再重复记");
        r.setExecId(existing.getId());
        r.setDoseSeq(existing.getDoseSeq());
        r.setCloseStatus(fresh == null ? order.getCloseStatus() : fresh.getCloseStatus());
        r.setExecCount(countExec(order.getOrderNo()));
        r.setOwnerDept(fresh == null ? order.getOwnerDept() : fresh.getOwnerDept());
        return r;
    }

    /** 结果对象补齐单上当前情形 */
    private OrderResult fill(OrderResult r, TMhOrder order) {
        r.setCloseStatus(order.getCloseStatus());
        r.setOwnerDept(order.getOwnerDept());
        r.setExecCount(countExec(order.getOrderNo()));
        return r;
    }

    /** 医嘱单号：MO + 雪花号，全局不撞 */
    private String nextOrderNo() {
        return "MO" + SnowflakeIdWorker.getUUID();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
