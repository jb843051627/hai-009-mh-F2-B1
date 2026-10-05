package com.fc.v2.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.auto.TMhOrderTransfer;
import com.fc.v2.model.custom.OrderResult;

/**
 * 住院医嘱执行闭环 业务层处理接口。
 *
 * <p>链路：录单 → 审方 →（驳回后坚持要发走强制放行）→ 发药 → 执行 → 闭环；转科随时可发生。
 * 闭环判定、执行条数统计全院只有这一处口径，页面与定时任务都调这里。</p>
 *
 * @author fuce
 * @date 2026-10-05
 */
public interface ITMhOrderService {

    /** 按单号取单（未删除） */
    TMhOrder selectByOrderNo(String orderNo);

    /** 条件分页查询（科室屏与复核屏同口径） */
    List<TMhOrder> selectOrderList(QueryWrapper<TMhOrder> queryWrapper);

    /** 录医嘱；backfill=true 为往年补录单（审方发药从略置妥，执行人靠补登） */
    OrderResult createOrder(String orderNo, String patientNo, String ownerDept, Integer orderType,
                            Integer needCount, Date periodStart, Date periodEnd, boolean backfill, String operator);

    /** 审方：pass=true 通过 / false 驳回；审方人与意见落单 */
    OrderResult review(String orderNo, boolean pass, String auditor, String comment);

    /** 驳回后医生坚持要发：强制放行，审批人/时点/理由三样落单，缺一样都不放 */
    OrderResult overrideRelease(String orderNo, String operator, String reason);

    /** 发药：审方通过或强制放行后才准发 */
    OrderResult dispense(String orderNo, String pharmacist);

    /**
     * 执行落笔：同一 (orderNo, doseSeq) 点几次、几班并发、重试几轮都只落一笔，
     * 重放返回已落的那笔；落笔与闭环推进同一事务。
     * 正常执行 execBy 必填；往年补录单允许 execBy 为空（空则该笔不计闭环，单进补录待核）。
     */
    OrderResult execute(String orderNo, Integer doseSeq, String execBy, Date execAt);

    /** 转科：医嘱随病人走，owner_dept 换边并写移交流水；重复移交同一科室按重放处理 */
    OrderResult transfer(String orderNo, String toDept, String operator);

    /** 补录笔补登执行人：只许补录笔且原执行人为空；补登后按同一口径重算闭环 */
    OrderResult amendExecutor(Long execId, String execBy, String operator);

    /** 按时段型到期单的自动闭环：与执行内联推进同一派生口径；按次数型与补录待核单不碰 */
    int runCloseJob(Date at);

    /** 执行条数（详情口径）：直接 COUNT 执行表，不经任何 JOIN 放大 */
    long countExec(String orderNo);

    /** 执行条数（列表口径）：与详情同一条 COUNT 的分组版，两处的数必然对得上 */
    Map<String, Long> countExecMap(Collection<String> orderNos);

    /** 某单执行明细（未删除，按次数升序） */
    List<TMhOrderExec> listExec(String orderNo);

    /** 科室在办清单：只按 owner_dept 过滤，转科后一张单只挂一头 */
    List<TMhOrder> listByDept(String ownerDept);

    /** 某单移交流水（未删除，按移交时点升序） */
    List<TMhOrderTransfer> listTransfers(String orderNo);
}
