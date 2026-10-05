package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhOrderMapper;
import com.fc.v2.mapper.auto.TMhOrderTransferMapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.auto.TMhOrderTransfer;
import com.fc.v2.model.custom.OrderActionResult;
import com.fc.v2.model.custom.OrderExecResult;
import com.fc.v2.model.custom.OrderSummary;
import com.fc.v2.service.ITMhOrderExecService;
import com.fc.v2.service.ITMhOrderService;
import com.fc.v2.util.SnowflakeIdWorker;

/**
 * 医嘱单闭环 Service 业务层处理。
 *
 * <p>全院口径就这一处，护士长对账就按这几条（与 doc/schema/mh.sql 表头同步）：</p>
 * <ul>
 *   <li>一张单只认一种闭环算法，落单定死：次数型数全部有效执行，时段型只数窗口内有效执行，
 *       顶到 plan_count 即闭环。不存在"两种算法都算数"的单。</li>
 *   <li>有效执行只认 {@link ITMhOrderExecService#countEffectiveExec} 这一个口径：
 *       缺执行人的笔永远不计入，闭环没有第二个"自动算完成"的入口。</li>
 *   <li>归属只有一个 dept_id：转科原子改归属+写轨迹，已执行的账留在执行时科室，
 *       未执行的剂次随单走，一单同时只属于一个科室。</li>
 *   <li>审方驳回后坚持要发的，只能走坚持放行，override 三件套必填落账，
 *       事后查"谁批的"就查 override_by。</li>
 *   <li>所有状态推进都是条件更新（CAS where 旧情形），并发与重试叠在一起也只有一方改得动，
 *       改不动的那方拿到"已被他人改动"的明话，重读单态再来。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@Service
public class TMhOrderServiceImpl implements ITMhOrderService {

    @javax.annotation.Resource
    private TMhOrderMapper mhOrderMapper;

    @javax.annotation.Resource
    private TMhOrderTransferMapper mhOrderTransferMapper;

    @javax.annotation.Resource
    private ITMhOrderExecService orderExecService;

    @Override
    public TMhOrder selectTMhOrderById(Long id) {
        return this.mhOrderMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderActionResult createOrder(String patientNo, Integer orderType, Integer planCount,
                                         Date windowStart, Date windowEnd, String deptId,
                                         String operator, String remark) {
        if (isBlank(patientNo)) {
            return OrderActionResult.rejected(null, "拒收：患者底档代号必填");
        }
        if (orderType == null || (orderType.intValue() != TMhOrder.TYPE_COUNT
                && orderType.intValue() != TMhOrder.TYPE_WINDOW)) {
            return OrderActionResult.rejected(null, "拒收：闭环算法型别只认 1次数型/2时段型，落单定死");
        }
        if (planCount == null || planCount.intValue() <= 0) {
            return OrderActionResult.rejected(null, "拒收：计划剂次数必须大于 0");
        }
        if (orderType.intValue() == TMhOrder.TYPE_WINDOW) {
            if (windowStart == null || windowEnd == null || !windowStart.before(windowEnd)) {
                return OrderActionResult.rejected(null, "拒收：时段型必须给全窗口且起早于止（含起不含止）");
            }
        } else {
            // 次数型不看时间：窗口一律置空，不许留脏值误导闭环判定
            windowStart = null;
            windowEnd = null;
        }
        if (isBlank(deptId)) {
            return OrderActionResult.rejected(null, "拒收：归属科室必填，单从一落生就得有人认领");
        }
        if (isBlank(operator)) {
            return OrderActionResult.rejected(null, "拒收：录入人必填");
        }

        Date now = new Date();
        TMhOrder order = new TMhOrder();
        order.setOrderNo(nextOrderNo(now));
        order.setPatientNo(patientNo.trim());
        order.setOrderType(orderType);
        order.setPlanCount(planCount);
        order.setWindowStart(windowStart);
        order.setWindowEnd(windowEnd);
        order.setDeptId(deptId.trim());
        order.setStatus(TMhOrder.STATUS_DRAFT);
        order.setDelFlag(0);
        order.setCreateBy(operator.trim());
        order.setCreateTime(now);
        order.setRemark(remark);
        this.mhOrderMapper.insert(order);
        return OrderActionResult.ok(order, "医嘱已录入，待审方");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderActionResult review(Long id, String approver, boolean pass, String comment) {
        if (isBlank(approver)) {
            return OrderActionResult.rejected(null, "拒收：审方药剂师必填，谁审的必须落账");
        }
        if (!pass && isBlank(comment)) {
            return OrderActionResult.rejected(null, "拒收：驳回必写缘由");
        }
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return OrderActionResult.rejected(null, "医嘱单不存在：" + id);
        }

        Date now = new Date();
        TMhOrder upd = new TMhOrder();
        upd.setStatus(pass ? TMhOrder.STATUS_REVIEWED : TMhOrder.STATUS_REJECTED);
        upd.setReviewBy(approver.trim());
        upd.setReviewAt(now);
        upd.setReviewComment(comment);
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", id).eq("status", TMhOrder.STATUS_DRAFT).eq("del_flag", 0);
        if (this.mhOrderMapper.update(upd, uw) == 0) {
            return OrderActionResult.rejected(order,
                    "单已不在待审方情形（可能已被他人审过），当前情形 " + statusName(order.getStatus()) + "，请重读单态");
        }
        return OrderActionResult.ok(this.mhOrderMapper.selectById(id),
                pass ? "审方通过，待发药" : "审方驳回，缘由已落账");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderActionResult overrideRelease(Long id, String operator, String reason) {
        if (isBlank(operator)) {
            return OrderActionResult.rejected(null, "拒收：坚持放行必须落操作人，事后查谁批的就认这个");
        }
        if (isBlank(reason)) {
            return OrderActionResult.rejected(null, "拒收：坚持放行必须写明理由，不许空着放");
        }
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return OrderActionResult.rejected(null, "医嘱单不存在：" + id);
        }

        Date now = new Date();
        TMhOrder upd = new TMhOrder();
        upd.setStatus(TMhOrder.STATUS_REVIEWED);
        upd.setOverrideBy(operator.trim());
        upd.setOverrideAt(now);
        upd.setOverrideReason(reason.trim());
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", id).eq("status", TMhOrder.STATUS_REJECTED).eq("del_flag", 0);
        if (this.mhOrderMapper.update(upd, uw) == 0) {
            return OrderActionResult.rejected(order,
                    "只有已驳回的单才能坚持放行，当前情形 " + statusName(order.getStatus()));
        }
        return OrderActionResult.ok(this.mhOrderMapper.selectById(id),
                "坚持放行已落账：" + operator.trim() + "，事后查谁批的认 override_by");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderActionResult dispense(Long id, String operator) {
        if (isBlank(operator)) {
            return OrderActionResult.rejected(null, "拒收：发药人必填");
        }
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return OrderActionResult.rejected(null, "医嘱单不存在：" + id);
        }

        Date now = new Date();
        TMhOrder upd = new TMhOrder();
        upd.setStatus(TMhOrder.STATUS_EXECUTING);
        upd.setDispenseBy(operator.trim());
        upd.setDispenseAt(now);
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", id).eq("status", TMhOrder.STATUS_REVIEWED).eq("del_flag", 0);
        if (this.mhOrderMapper.update(upd, uw) == 0) {
            return OrderActionResult.rejected(order,
                    "只有审方通过的单才能发药，当前情形 " + statusName(order.getStatus()));
        }
        return OrderActionResult.ok(this.mhOrderMapper.selectById(id), "已发药，转执行中");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderExecResult execute(Long id, Integer doseSeq, String executor, Date executedAt,
                                   String idemKey, boolean backfilled) {
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return OrderExecResult.rejected("医嘱单不存在：" + id);
        }

        // 幂等预查先于状态校验：已闭环单的重试要拿到已落的那笔，而不是吃"已闭环"的拒收
        String key = isBlank(idemKey)
                ? TMhOrderExecServiceImpl.fallbackIdemKey(id, doseSeq)
                : idemKey.trim();
        TMhOrderExec existing = this.orderExecService.findByIdemKey(key);
        if (existing == null) {
            existing = this.orderExecService.findDose(id, doseSeq);
        }
        if (existing != null && id.equals(existing.getOrderId())) {
            return OrderExecResult.duplicated(existing, "该次执行已落过笔，库里只有一笔，返回已有记录");
        }

        if (order.getStatus() == null || order.getStatus().intValue() != TMhOrder.STATUS_EXECUTING) {
            return OrderExecResult.rejected("只有执行中的单才能落执行，当前情形 "
                    + statusName(order.getStatus()) + "（未发药先审方发药，已闭环不收计划外执行）");
        }

        OrderExecResult result = this.orderExecService.recordExec(order, doseSeq, executor, executedAt,
                key, backfilled);
        if (!result.isSuccess()) {
            return result;
        }

        // 落笔成功当场重算闭环：闭环只由有效执行顶出来，没有第二个入口
        TMhOrder after = recheckClosure(id);
        boolean closed = after != null && after.getStatus() != null
                && after.getStatus().intValue() == TMhOrder.STATUS_CLOSED;
        result.setOrderClosed(closed);
        if (closed) {
            result.setMessage(result.getMessage() + "；有效执行已顶到达标线，医嘱闭环");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderExecResult voidExec(Long execId, String operator, String reason) {
        OrderExecResult voided = this.orderExecService.voidExecRow(execId, operator, reason);
        if (!voided.isSuccess()) {
            return voided;
        }
        // 作废可能把闭环单拽下达标线：当场双向重算，闭环状态永远跟着有效执行走
        TMhOrder after = recheckClosure(voided.getExec().getOrderId());
        voided.setOrderClosed(after != null && after.getStatus() != null
                && after.getStatus().intValue() == TMhOrder.STATUS_CLOSED);
        return voided;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderActionResult transfer(Long id, String toDept, String operator) {
        if (isBlank(operator)) {
            return OrderActionResult.rejected(null, "拒收：转科经手人必填，事后对账认这个人");
        }
        if (isBlank(toDept)) {
            return OrderActionResult.rejected(null, "拒收：接收科室必填");
        }
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return OrderActionResult.rejected(null, "医嘱单不存在：" + id);
        }
        if (order.getStatus() != null && order.getStatus().intValue() == TMhOrder.STATUS_CLOSED) {
            return OrderActionResult.rejected(order, "已闭环的单没有待执行，不存在归属问题，不许转");
        }
        String fromDept = order.getDeptId();
        if (toDept.trim().equals(fromDept)) {
            return OrderActionResult.rejected(order, "原地转不算转：单已在 " + fromDept);
        }

        // 原子改归属：CAS 带上读单时的归属，并发两个转科只有一个赢
        Date now = new Date();
        TMhOrder upd = new TMhOrder();
        upd.setDeptId(toDept.trim());
        UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
        uw.eq("id", id).eq("del_flag", 0).ne("status", TMhOrder.STATUS_CLOSED);
        if (fromDept == null) {
            uw.isNull("dept_id");
        } else {
            uw.eq("dept_id", fromDept);
        }
        if (this.mhOrderMapper.update(upd, uw) == 0) {
            return OrderActionResult.rejected(order, "归属已被他人改动（并发转科/已闭环），请重读单态再来");
        }

        // 轨迹与改归属同事务：事后对"转科那一刻单在谁头上"只认这一笔
        TMhOrderTransfer log = new TMhOrderTransfer();
        log.setOrderId(id);
        log.setFromDept(fromDept);
        log.setToDept(toDept.trim());
        log.setMovedBy(operator.trim());
        log.setMovedAt(now);
        log.setRemark("未执行剂次随单归 " + toDept.trim() + "，已执行的账留在执行时科室");
        this.mhOrderTransferMapper.insert(log);

        return OrderActionResult.ok(this.mhOrderMapper.selectById(id),
                "已转科：" + fromDept + " → " + toDept.trim() + "，待执行只挂接收科室");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMhOrder recheckClosure(Long id) {
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return order;
        }
        boolean closed = isClosed(order);
        if (order.getStatus() != null && order.getStatus().intValue() == TMhOrder.STATUS_EXECUTING
                && closed) {
            // 有效执行顶到达标线：2→3，闭环时点写死。CAS 保证并发/重试只有一方改得动
            Date now = new Date();
            TMhOrder upd = new TMhOrder();
            upd.setStatus(TMhOrder.STATUS_CLOSED);
            upd.setClosedAt(now);
            UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
            uw.eq("id", id).eq("status", TMhOrder.STATUS_EXECUTING).eq("del_flag", 0);
            if (this.mhOrderMapper.update(upd, uw) != 0) {
                return this.mhOrderMapper.selectById(id);
            }
            return order;
        }
        if (order.getStatus() != null && order.getStatus().intValue() == TMhOrder.STATUS_CLOSED
                && !closed) {
            // 有效执行被作废、跌下达标线：3→2 重开，闭环时点清空。
            // 闭环只由有效执行推导，两个方向都收口，不许挂假账
            UpdateWrapper<TMhOrder> uw = new UpdateWrapper<TMhOrder>();
            uw.set("status", TMhOrder.STATUS_EXECUTING);
            uw.set("closed_at", null);
            uw.eq("id", id).eq("status", TMhOrder.STATUS_CLOSED).eq("del_flag", 0);
            if (this.mhOrderMapper.update(null, uw) != 0) {
                return this.mhOrderMapper.selectById(id);
            }
        }
        return order;
    }

    @Override
    public boolean isClosed(TMhOrder order) {
        if (order == null || order.getPlanCount() == null || order.getPlanCount().intValue() <= 0) {
            return false;
        }
        // 唯一口径：有效执行顶到计划剂次即闭环；型别分派在 countEffectiveExec 里，一张单只走一支
        return this.orderExecService.countEffectiveExec(order) >= order.getPlanCount().intValue();
    }

    @Override
    public OrderSummary summarize(TMhOrder order, boolean withExecs) {
        if (order == null) {
            return null;
        }
        // 列表、详情、闭环判定全走这一个汇总：两处对不上账就是另写口径写出来的
        OrderSummary s = new OrderSummary(order,
                this.orderExecService.countEffectiveExec(order), isClosed(order));
        if (withExecs) {
            s.setExecs(this.orderExecService.listExecOfOrder(order.getId()));
        }
        return s;
    }

    @Override
    public List<OrderSummary> listDeptSummaries(String deptId) {
        List<OrderSummary> out = new ArrayList<OrderSummary>();
        if (isBlank(deptId)) {
            return out;
        }
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("del_flag", 0);
        q.eq("dept_id", deptId.trim());
        q.orderByDesc("id");
        List<TMhOrder> rows = this.mhOrderMapper.selectList(q);
        if (rows == null) {
            return out;
        }
        for (TMhOrder o : rows) {
            out.add(summarize(o, false));
        }
        return out;
    }

    @Override
    public OrderSummary detailOf(Long id) {
        TMhOrder order = this.mhOrderMapper.selectById(id);
        if (order == null || isDeleted(order)) {
            return null;
        }
        return summarize(order, true);
    }

    @Override
    public List<TMhOrder> selectTMhOrderList(com.baomidou.mybatisplus.core.conditions.Wrapper<TMhOrder> queryWrapper) {
        return this.mhOrderMapper.selectList(queryWrapper);
    }

    /** 情形明称，回话与页面都认这一套 */
    public static String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status.intValue()) {
            case TMhOrder.STATUS_DRAFT:
                return "待审方";
            case TMhOrder.STATUS_REVIEWED:
                return "待发药";
            case TMhOrder.STATUS_EXECUTING:
                return "执行中";
            case TMhOrder.STATUS_CLOSED:
                return "已闭环";
            case TMhOrder.STATUS_REJECTED:
                return "已驳回";
            default:
                return "未知";
        }
    }

    /** 医嘱单号：MO + 落单时刻紧凑串 + 雪花号，号内带时间便于对账，全局不撞 */
    private String nextOrderNo(Date at) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(at);
        return "MO" + ts + SnowflakeIdWorker.getUUID();
    }

    private boolean isDeleted(TMhOrder order) {
        return order.getDelFlag() != null && order.getDelFlag().intValue() != 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
