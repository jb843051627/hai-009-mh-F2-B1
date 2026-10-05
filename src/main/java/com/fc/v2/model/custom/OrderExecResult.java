package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.TMhOrderExec;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 医嘱执行落笔结果。
 * <p>三态必须分清，不许都当成功：</p>
 * <ul>
 *   <li>{@link #isSuccess()}：新落一笔执行记录；</li>
 *   <li>{@link #isDuplicated()}：幂等命中——同一剂或同一幂等键已落过笔（点两次、
 *       交接班两人同时提交、网络重试），返回的是库里已落的那笔，库里不会多笔；</li>
 *   <li>{@link #isRejected()}：拒收不落笔（未发药、已闭环后计划外执行、剂次超范围、
 *       执行人空着——含补录）。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "OrderExecResult", description = "医嘱执行落笔结果")
public class OrderExecResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "是否新落一笔")
    private boolean success;

    @ApiModelProperty(value = "是否幂等命中（已落过笔，返回已有那笔）")
    private boolean duplicated;

    @ApiModelProperty(value = "是否拒收（不落笔）")
    private boolean rejected;

    @ApiModelProperty(value = "给操作人看的明话")
    private String message;

    @ApiModelProperty(value = "本次落下的或幂等命中的执行记录")
    private TMhOrderExec exec;

    @ApiModelProperty(value = "这一笔落下后医嘱是否已闭环")
    private boolean orderClosed;

    public static OrderExecResult ok(TMhOrderExec exec, boolean orderClosed, String message) {
        OrderExecResult r = new OrderExecResult();
        r.success = true;
        r.duplicated = false;
        r.rejected = false;
        r.exec = exec;
        r.orderClosed = orderClosed;
        r.message = message;
        return r;
    }

    public static OrderExecResult duplicated(TMhOrderExec exec, String message) {
        OrderExecResult r = new OrderExecResult();
        r.success = false;
        r.duplicated = true;
        r.rejected = false;
        r.exec = exec;
        r.message = message;
        return r;
    }

    public static OrderExecResult rejected(String message) {
        OrderExecResult r = new OrderExecResult();
        r.success = false;
        r.duplicated = false;
        r.rejected = true;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDuplicated() {
        return duplicated;
    }

    public boolean isRejected() {
        return rejected;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TMhOrderExec getExec() {
        return exec;
    }

    public void setExec(TMhOrderExec exec) {
        this.exec = exec;
    }

    public boolean isOrderClosed() {
        return orderClosed;
    }

    public void setOrderClosed(boolean orderClosed) {
        this.orderClosed = orderClosed;
    }
}
