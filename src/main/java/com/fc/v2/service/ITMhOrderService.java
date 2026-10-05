package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.custom.OrderActionResult;
import com.fc.v2.model.custom.OrderExecResult;
import com.fc.v2.model.custom.OrderSummary;

/**
 * TMhOrder 业务层处理接口（医嘱单闭环）
 *
 * <p>状态机（所有推进都是条件更新 CAS，并发与重试叠在一起也只有一方改得动）：</p>
 * <pre>
 *   0待审方 --审方通过--&gt; 1待发药 --发药--&gt; 2执行中 --有效执行顶到达标线--&gt; 3已闭环
 *   0待审方 --审方驳回--&gt; 4已驳回 --医生坚持放行(override三件套)--&gt; 1待发药
 * </pre>
 *
 * <p>归属口径（定死，不再两边挂）：执行责任随病人走——转科原子改 dept_id 并写轨迹，
 * 未执行剂次随单归接收科室，已执行的账留在执行时科室；一单同时只属于一个科室，
 * 待办列表只按当前 dept_id 查。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
public interface ITMhOrderService {

    /** selectTMhOrderById */
    TMhOrder selectTMhOrderById(Long id);

    /**
     * 医嘱录入。闭环算法型别落单定死、终身不改：
     * 次数型 planCount&gt;0、窗口一律置空；时段型窗口必填且起早于止（含起不含止）。
     * 归属科室与录入人必填——单从一落生就得有人认领。
     */
    OrderActionResult createOrder(String patientNo, Integer orderType, Integer planCount,
                                  Date windowStart, Date windowEnd, String deptId,
                                  String operator, String remark);

    /**
     * 审方：pass=true 通过（0→1），pass=false 驳回（0→4，驳回缘由必填）。
     * 审方药剂师必填落账。并发下已被他人审过的，回明话让重读单态。
     */
    OrderActionResult review(Long id, String approver, boolean pass, String comment);

    /**
     * 驳回后坚持放行（4→1）：操作人与理由必填，落 override_by/override_at/override_reason
     * 三件套——事后查"谁批的"就查 override_by，查不到就是没走过这一步。
     */
    OrderActionResult overrideRelease(Long id, String operator, String reason);

    /** 发药（1→2）：发药人必填落账。 */
    OrderActionResult dispense(Long id, String operator);

    /**
     * 作废一笔执行（留痕不毁账，经手人与缘由必填）。
     * 作废可能把已闭环的单拽下达标线：落笔后当场双向重算闭环，
     * 闭环状态永远跟着有效执行走，不许挂假账。
     */
    OrderExecResult voidExec(Long execId, String operator, String reason);

    /**
     * 执行一剂：幂等优先于状态校验——同一剂/同一幂等键已落过，直接返回已有那笔
     * （点两次、交接班两人同时提交、网络重试，库里都只有一笔）；
     * 执行人空着（含补录）拒收；剂次超计划拒收；只有执行中的单收执行。
     * 落笔成功当场重算闭环。
     */
    OrderExecResult execute(Long id, Integer doseSeq, String executor, Date executedAt,
                            String idemKey, boolean backfilled);

    /**
     * 转科：未闭环才可转；原子改归属+写轨迹，经手人必填。
     * 已执行的账留在执行时科室，未执行剂次随单归接收科室；原地转不算转。
     */
    OrderActionResult transfer(Long id, String toDept, String operator);

    /**
     * 闭环重算（幂等，双向）：执行中且有效执行顶到达标线 → CAS 置已闭环并写死闭环时点；
     * 已闭环但有效执行跌下达标线（执行被作废）→ CAS 重开回执行中并清空闭环时点。
     * 闭环只由有效执行推导，没有第二个"自动算完成"的入口；并发与重试随便叠。
     */
    TMhOrder recheckClosure(Long id);

    /** 闭环判定（唯一口径）：有效执行笔数顶到 plan_count；型别分派在计数里，一张单只走一支 */
    boolean isClosed(TMhOrder order);

    /** 汇总（列表与详情共用这一个入口，两边的执行数必然同源） */
    OrderSummary summarize(TMhOrder order, boolean withExecs);

    /** 科室单列表（待办与在办都按当前归属科室查，转科后只出现在接收科室） */
    List<OrderSummary> listDeptSummaries(String deptId);

    /** 单详情：单 + 有效执行数 + 执行明细，与列表同一个汇总口径 */
    OrderSummary detailOf(Long id);

    /** 分页/列表原始查询（控制层拼条件，汇总数字仍走 summarize 单一口径） */
    List<TMhOrder> selectTMhOrderList(com.baomidou.mybatisplus.core.conditions.Wrapper<TMhOrder> queryWrapper);
}
