package com.fc.v2.model.custom;

import java.io.Serializable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 医嘱闭环动作结果（录单/审方/放行/发药/执行/转科共用）。
 * <p>三态必须分清：</p>
 * <ul>
 *   <li>{@link #isSuccess()}：动作办成，闭环情形以 {@link #getCloseStatus()} 为准；</li>
 *   <li>{@link #isRejected()}：被规矩退回（未审方/未发药/次数越界/已停嘱/单不存在等），
 *       库里什么都没动；</li>
 *   <li>{@link #isReplayed()}：幂等重放——同一计划次数早落过、同一科室重复移交等，
 *       返回已落的那笔，绝不重复落账。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "OrderResult", description = "医嘱闭环动作结果")
public class OrderResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "是否办成")
    private boolean success;

    @ApiModelProperty(value = "是否被规矩退回")
    private boolean rejected;

    @ApiModelProperty(value = "是否幂等重放（已落过的那笔原样返回）")
    private boolean replayed;

    @ApiModelProperty(value = "医嘱单号")
    private String orderNo;

    @ApiModelProperty(value = "执行笔主键（执行/补登时回）")
    private Long execId;

    @ApiModelProperty(value = "计划内第几次（执行时回）")
    private Integer doseSeq;

    @ApiModelProperty(value = "动作后闭环情形 0未闭环 1已闭环 2补录待核")
    private Integer closeStatus;

    @ApiModelProperty(value = "动作后有效执行笔数（执行人非空的笔数，与列表同口径）")
    private Long execCount;

    @ApiModelProperty(value = "动作后归属科室（转科时回）")
    private String ownerDept;

    @ApiModelProperty(value = "给护士/医生看的明话")
    private String message;

    public static OrderResult ok(String orderNo, String message) {
        OrderResult r = new OrderResult();
        r.success = true;
        r.orderNo = orderNo;
        r.message = message;
        return r;
    }

    public static OrderResult rejected(String orderNo, String message) {
        OrderResult r = new OrderResult();
        r.rejected = true;
        r.orderNo = orderNo;
        r.message = message;
        return r;
    }

    public static OrderResult replayed(String orderNo, String message) {
        OrderResult r = new OrderResult();
        r.success = true;
        r.replayed = true;
        r.orderNo = orderNo;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getExecId() {
        return execId;
    }

    public void setExecId(Long execId) {
        this.execId = execId;
    }

    public Integer getDoseSeq() {
        return doseSeq;
    }

    public void setDoseSeq(Integer doseSeq) {
        this.doseSeq = doseSeq;
    }

    public Integer getCloseStatus() {
        return closeStatus;
    }

    public void setCloseStatus(Integer closeStatus) {
        this.closeStatus = closeStatus;
    }

    public Long getExecCount() {
        return execCount;
    }

    public void setExecCount(Long execCount) {
        this.execCount = execCount;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
