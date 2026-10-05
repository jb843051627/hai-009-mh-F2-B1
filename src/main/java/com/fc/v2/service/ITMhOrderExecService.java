package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.custom.OrderExecResult;

/**
 * TMhOrderExec 业务层处理接口（医嘱执行记录）
 *
 * <p>本域就两件事，都是全院唯一口径：</p>
 * <ul>
 *   <li>落笔幂等：同一剂（order_id, dose_seq）、同一幂等键，全库至多一笔。
 *       点两次、交接班两人同时提交同一剂、网络重试，撞上的那方拿到已落的那笔。</li>
 *   <li>有效计数：闭环判定、列表、详情都只能从 {@link #countEffectiveExec(TMhOrder)} 拿数，
 *       谁也不许自己另写一套——列表和详情对不上账就是另写口径写出来的。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
public interface ITMhOrderExecService {

    /**
     * 执行落笔（幂等）。前置：调用方已确认医嘱单处于可执行情形。
     * <ul>
     *   <li>执行人空着（含补录）：拒收，不落笔；</li>
     *   <li>剂次超出 1..plan_count：拒收，不落笔；</li>
     *   <li>同一剂/同一幂等键已落过：返回幂等命中与库里那笔，不加新笔；</li>
     *   <li>时段型窗口外的执行：照收留痕，备注明写"窗口外执行不计入闭环"。</li>
     * </ul>
     * idemKey 传空时按 "OD-{orderId}-{doseSeq}" 兜底——同一剂天然同一个键。
     */
    OrderExecResult recordExec(TMhOrder order, Integer doseSeq, String executor, Date executedAt,
                               String idemKey, boolean backfilled);

    /** 按幂等键找已落的笔，找不到返回 null */
    TMhOrderExec findByIdemKey(String idemKey);

    /** 找某医嘱某一剂已落的笔（未删除），找不到返回 null */
    TMhOrderExec findDose(Long orderId, Integer doseSeq);

    /** 某医嘱全部未删除执行记录（详情明细用，按剂次升序） */
    List<TMhOrderExec> listExecOfOrder(Long orderId);

    /**
     * 有效执行笔数（闭环判定/列表/详情唯一口径）：
     * 未删除、执行人非空；时段型只数落在 [windowStart, windowEnd) 窗口内的笔。
     * 缺执行人的笔（往年补录的脏数据也算）永远不计入。
     */
    long countEffectiveExec(TMhOrder order);

    /**
     * 作废一笔执行（留痕不毁账）：del_flag 置 1，dose_seq 与 idem_key 置空让位给重录，
     * 原剂次、原幂等键、经手人、缘由记进备注。行不删，事后复查有据。
     * 经手人与缘由必填；已作废的笔再作废按拒收。闭环重算是调用方的事（见医嘱服务）。
     */
    OrderExecResult voidExecRow(Long execId, String operator, String reason);
}
