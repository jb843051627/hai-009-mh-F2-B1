package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.TMhOrder;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 医嘱动作结果（录入/审方/坚持放行/发药/转科共用）。
 * <p>两态分清：{@link #isSuccess()} 动作落账；{@link #isRejected()} 拒收（状态不对、
 * 缺人缺理由、并发下已被他人改动），单上原话退回，不落账。并发 CAS 输掉一律按拒收回话，
 * 调用方重读单态再决定下一步，不许当成成功。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "OrderActionResult", description = "医嘱动作结果")
public class OrderActionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "动作是否落账")
    private boolean success;

    @ApiModelProperty(value = "是否拒收（不落账）")
    private boolean rejected;

    @ApiModelProperty(value = "给操作人看的明话")
    private String message;

    @ApiModelProperty(value = "动作后的单子快照（拒收时为当前库里的单子）")
    private TMhOrder order;

    public static OrderActionResult ok(TMhOrder order, String message) {
        OrderActionResult r = new OrderActionResult();
        r.success = true;
        r.rejected = false;
        r.order = order;
        r.message = message;
        return r;
    }

    public static OrderActionResult rejected(TMhOrder order, String message) {
        OrderActionResult r = new OrderActionResult();
        r.success = false;
        r.rejected = true;
        r.order = order;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
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

    public TMhOrder getOrder() {
        return order;
    }

    public void setOrder(TMhOrder order) {
        this.order = order;
    }
}
