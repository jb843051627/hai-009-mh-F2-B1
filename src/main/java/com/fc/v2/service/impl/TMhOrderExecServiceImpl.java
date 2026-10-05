package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhOrderExecMapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.custom.OrderExecResult;
import com.fc.v2.service.ITMhOrderExecService;

/**
 * 医嘱执行记录 Service 业务层处理。
 *
 * <p>落笔幂等靠两道防线，缺一不可：</p>
 * <ul>
 *   <li>应用层先查：同一剂（order_id, dose_seq）或同一幂等键已落过，直接返回已有那笔，
 *       同一份医嘱点两次、网络重试都拦在这里；</li>
 *   <li>数据库唯一约束兜底：交接班两人真正同时提交，应用层双双查空、双双落笔时，
 *       唯一索引只放一笔过去，撞约束的那方改查已有笔返回——库里始终只有一笔。</li>
 * </ul>
 *
 * <p>有效计数是全院唯一口径：闭环判定、列表、详情都只认
 * {@link #countEffectiveExec(TMhOrder)}，缺执行人的笔永远不计入。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@Service
public class TMhOrderExecServiceImpl implements ITMhOrderExecService {

    /** 窗口外执行的备注原话，单上、列表、详情都认这一句 */
    public static final String OUT_OF_WINDOW_REMARK = "窗口外执行：不计入闭环，仅留痕";

    @javax.annotation.Resource
    private TMhOrderExecMapper mhOrderExecMapper;

    /** 幂等键兜底规则：调用方不传键时，同一剂天然同一个键 */
    public static String fallbackIdemKey(Long orderId, Integer doseSeq) {
        return "OD-" + orderId + "-" + doseSeq;
    }

    @Override
    public OrderExecResult recordExec(TMhOrder order, Integer doseSeq, String executor, Date executedAt,
                                      String idemKey, boolean backfilled) {
        if (order == null || order.getId() == null) {
            return OrderExecResult.rejected("医嘱单不存在，不能落执行");
        }
        // 执行人是闭环硬条件：空着直接拒收，补录老单也不例外
        if (isBlank(executor)) {
            return OrderExecResult.rejected("拒收：执行人必填，空着不许落笔（补录老单一样要执行人）");
        }
        Integer planCount = order.getPlanCount();
        if (doseSeq == null || planCount == null || doseSeq.intValue() < 1 || doseSeq.intValue() > planCount.intValue()) {
            return OrderExecResult.rejected("拒收：剂次超出计划范围（1.." + planCount + "），计划外执行不收");
        }

        Date at = executedAt == null ? new Date() : executedAt;
        String key = isBlank(idemKey) ? fallbackIdemKey(order.getId(), doseSeq) : idemKey.trim();

        // 应用层幂等：同一键/同一剂已落过，返回库里那笔，不加新笔
        TMhOrderExec byKey = findByIdemKey(key);
        if (byKey != null) {
            if (!byKey.getOrderId().equals(order.getId())) {
                return OrderExecResult.rejected("拒收：该幂等键已被别的医嘱占用，换键再来");
            }
            return OrderExecResult.duplicated(byKey, "该次执行已落过笔（同一幂等键），库里只有一笔，返回已有记录");
        }
        TMhOrderExec byDose = findDose(order.getId(), doseSeq);
        if (byDose != null) {
            return OrderExecResult.duplicated(byDose, "第 " + doseSeq + " 剂已执行过，库里只有一笔，返回已有记录");
        }

        TMhOrderExec row = new TMhOrderExec();
        row.setOrderId(order.getId());
        row.setDoseSeq(doseSeq);
        row.setIdemKey(key);
        row.setExecutor(executor.trim());
        row.setExecutedAt(at);
        // 账留落笔当时的归属科室，转科不改历史
        row.setDeptId(order.getDeptId());
        row.setBackfilled(backfilled ? 1 : 0);
        row.setDelFlag(0);
        if (Integer.valueOf(TMhOrder.TYPE_WINDOW).equals(order.getOrderType())
                && !inWindow(order, at)) {
            // 时段型窗口外的执行：照收留痕，但永远不计入闭环
            row.setRemark(OUT_OF_WINDOW_REMARK);
        }

        try {
            this.mhOrderExecMapper.insert(row);
        } catch (PersistenceException e) {
            // 并发撞唯一约束（同一剂/同一键）：谁撞上谁改查已有笔，库里仍然只有一笔
            TMhOrderExec existing = findDose(order.getId(), doseSeq);
            if (existing == null) {
                existing = findByIdemKey(key);
            }
            if (existing != null && existing.getOrderId().equals(order.getId())) {
                return OrderExecResult.duplicated(existing,
                        "并发提交撞上唯一约束：该剂已落笔，返回已有记录，库里只有一笔");
            }
            if (existing != null) {
                return OrderExecResult.rejected("拒收：该幂等键已被别的医嘱占用，换键再来");
            }
            throw e;
        }
        return OrderExecResult.ok(row, false, "第 " + doseSeq + " 剂执行落笔");
    }

    @Override
    public TMhOrderExec findByIdemKey(String idemKey) {
        if (isBlank(idemKey)) {
            return null;
        }
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("idem_key", idemKey.trim());
        q.eq("del_flag", 0);
        return this.mhOrderExecMapper.selectOne(q);
    }

    @Override
    public TMhOrderExec findDose(Long orderId, Integer doseSeq) {
        if (orderId == null || doseSeq == null) {
            return null;
        }
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_id", orderId);
        q.eq("dose_seq", doseSeq.intValue());
        q.eq("del_flag", 0);
        return this.mhOrderExecMapper.selectOne(q);
    }

    @Override
    public List<TMhOrderExec> listExecOfOrder(Long orderId) {
        if (orderId == null) {
            return new ArrayList<TMhOrderExec>();
        }
        QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
        q.eq("order_id", orderId);
        q.eq("del_flag", 0);
        q.orderByAsc("dose_seq");
        List<TMhOrderExec> rows = this.mhOrderExecMapper.selectList(q);
        return rows == null ? new ArrayList<TMhOrderExec>() : rows;
    }

    @Override
    public long countEffectiveExec(TMhOrder order) {
        if (order == null || order.getId() == null) {
            return 0;
        }
        long n = 0;
        for (TMhOrderExec row : listExecOfOrder(order.getId())) {
            // 缺执行人的笔（往年补录的脏数据也算）永远不计入闭环
            if (isBlank(row.getExecutor())) {
                continue;
            }
            // 时段型只数窗口内的笔；次数型不看时间
            if (Integer.valueOf(TMhOrder.TYPE_WINDOW).equals(order.getOrderType())
                    && !inWindow(order, row.getExecutedAt())) {
                continue;
            }
            n++;
        }
        return n;
    }

    @Override
    public OrderExecResult voidExecRow(Long execId, String operator, String reason) {
        if (isBlank(operator)) {
            return OrderExecResult.rejected("拒收：作废经手人必填，事后对账认这个人");
        }
        if (isBlank(reason)) {
            return OrderExecResult.rejected("拒收：作废必须写明缘由");
        }
        if (execId == null) {
            return OrderExecResult.rejected("拒收：执行记录主键必填");
        }
        TMhOrderExec row = this.mhOrderExecMapper.selectById(execId);
        if (row == null || (row.getDelFlag() != null && row.getDelFlag().intValue() != 0)) {
            return OrderExecResult.rejected("拒收：执行记录不存在或已作废：" + execId);
        }
        // 留痕不毁账：剂次与幂等键置空让位给重录，原值连同经手人、缘由记进备注，行留着供复查。
        // 置空必须走 UpdateWrapper.set（updateById 默认跳过 null 字段，空值写不进去）；
        // 带上 del_flag=0 条件，两人同时作废同一笔也只有一方改得动
        String original = "作废留痕：原第 " + row.getDoseSeq() + " 剂，原幂等键 " + row.getIdemKey()
                + "，经手 " + operator.trim() + "，缘由：" + reason.trim();
        UpdateWrapper<TMhOrderExec> uw = new UpdateWrapper<TMhOrderExec>();
        uw.set("del_flag", 1);
        uw.set("dose_seq", null);
        uw.set("idem_key", null);
        uw.set("remark", original);
        uw.eq("id", execId).eq("del_flag", 0);
        if (this.mhOrderExecMapper.update(null, uw) == 0) {
            return OrderExecResult.rejected("拒收：执行记录不存在或已作废：" + execId);
        }
        row.setDelFlag(1);
        row.setDoseSeq(null);
        row.setIdemKey(null);
        row.setRemark(original);
        return OrderExecResult.ok(row, false, "执行记录已作废留痕，剂次与幂等键已让位，可按规矩重录");
    }

    /** 时段窗口：含起不含止；窗口缺任一端视为不在窗口（脏单闭不了环，等人修，不许自动算完成） */
    private boolean inWindow(TMhOrder order, Date at) {
        if (at == null || order.getWindowStart() == null || order.getWindowEnd() == null) {
            return false;
        }
        return !at.before(order.getWindowStart()) && at.before(order.getWindowEnd());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
