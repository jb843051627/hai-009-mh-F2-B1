package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.List;

import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 医嘱单汇总（列表与详情共用这一个形状，两边的执行数必然同源）。
 * <p>有效执行数只从 ITMhOrderExecService.countEffectiveExec 拿，闭环标志只从
 * ITMhOrderService.isClosed 拿——列表、详情、闭环判定同一个口径，
 * 不许任何一处另写一套计数。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "OrderSummary", description = "医嘱单汇总")
public class OrderSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "医嘱单")
    private TMhOrder order;

    @ApiModelProperty(value = "有效执行笔数（缺执行人的笔不计入；时段型只数窗口内）")
    private long effectiveCount;

    @ApiModelProperty(value = "是否已闭环（按本单型别的算法判定）")
    private boolean closed;

    @ApiModelProperty(value = "执行明细（详情填充，列表不填）")
    private List<TMhOrderExec> execs;

    public OrderSummary() {
    }

    public OrderSummary(TMhOrder order, long effectiveCount, boolean closed) {
        this.order = order;
        this.effectiveCount = effectiveCount;
        this.closed = closed;
    }

    public TMhOrder getOrder() {
        return order;
    }

    public void setOrder(TMhOrder order) {
        this.order = order;
    }

    public long getEffectiveCount() {
        return effectiveCount;
    }

    public void setEffectiveCount(long effectiveCount) {
        this.effectiveCount = effectiveCount;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public List<TMhOrderExec> getExecs() {
        return execs;
    }

    public void setExecs(List<TMhOrderExec> execs) {
        this.execs = execs;
    }
}
