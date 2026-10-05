package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * t_mh_order 对象 住院医嘱单。
 *
 * <p>一张单只适用一种闭环算法（order_type 定死）；归属只看 owner_dept 一处，
 * 转科随病人走；审方驳回后坚持要发必须落强制放行三样（人/时点/理由）。</p>
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_mh_order")
@ApiModel(value = "TMhOrder", description = "住院医嘱单")
public class TMhOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 闭环算法：按次数（有效执行笔数达 need_count 才闭环） */
    public static final int TYPE_COUNT = 1;
    /** 闭环算法：按时段（过 period_end 且期内有有效执行才闭环） */
    public static final int TYPE_PERIOD = 2;

    /** 审方情形：待审 */
    public static final int REVIEW_PENDING = 0;
    /** 审方情形：通过 */
    public static final int REVIEW_PASS = 1;
    /** 审方情形：驳回 */
    public static final int REVIEW_REJECT = 2;
    /** 审方情形：驳回后强制放行（override 三样已落单） */
    public static final int REVIEW_OVERRIDE = 3;

    /** 发药情形：未发药 */
    public static final int DISPENSE_NO = 0;
    /** 发药情形：已发药 */
    public static final int DISPENSE_YES = 1;

    /** 闭环情形：未闭环 */
    public static final int CLOSE_OPEN = 0;
    /** 闭环情形：已闭环 */
    public static final int CLOSE_DONE = 1;
    /** 闭环情形：补录待核（有缺执行人的补录笔，不自动闭环） */
    public static final int CLOSE_BACKFILL_PENDING = 2;

    /** 单情形：在办 */
    public static final int STATUS_ACTIVE = 0;
    /** 单情形：已停嘱 */
    public static final int STATUS_STOPPED = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 医嘱单号 */
    @TableField("order_no")
    @ApiModelProperty(value = "医嘱单号")
    private String orderNo;

    /** 患者底档代号 */
    @TableField("patient_no")
    @ApiModelProperty(value = "患者底档代号")
    private String patientNo;

    /** 当前归属科室（唯一认领方，转科只改这里） */
    @TableField("owner_dept")
    @ApiModelProperty(value = "当前归属科室")
    private String ownerDept;

    /** 闭环算法 1按次数 2按时段（一张单只适用一种） */
    @TableField("order_type")
    @ApiModelProperty(value = "闭环算法 1按次数 2按时段")
    private Integer orderType;

    /** 应执行次数（按次数型必填） */
    @TableField("need_count")
    @ApiModelProperty(value = "应执行次数")
    private Integer needCount;

    /** 执行时段起（按时段型必填，含） */
    @TableField("period_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行时段起")
    private Date periodStart;

    /** 执行时段止（按时段型必填，不含） */
    @TableField("period_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行时段止")
    private Date periodEnd;

    /** 审方情形 0待审 1通过 2驳回 3驳回后强制放行 */
    @TableField("review_status")
    @ApiModelProperty(value = "审方情形")
    private Integer reviewStatus;

    /** 审方人 */
    @TableField("review_by")
    @ApiModelProperty(value = "审方人")
    private String reviewBy;

    /** 审方时点 */
    @TableField("review_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "审方时点")
    private Date reviewAt;

    /** 审方意见 */
    @TableField("review_comment")
    @ApiModelProperty(value = "审方意见")
    private String reviewComment;

    /** 强制放行审批人（驳回后坚持要发必落） */
    @TableField("override_by")
    @ApiModelProperty(value = "强制放行审批人")
    private String overrideBy;

    /** 强制放行时点 */
    @TableField("override_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "强制放行时点")
    private Date overrideAt;

    /** 强制放行理由 */
    @TableField("override_reason")
    @ApiModelProperty(value = "强制放行理由")
    private String overrideReason;

    /** 发药情形 0未发药 1已发药 */
    @TableField("dispense_status")
    @ApiModelProperty(value = "发药情形")
    private Integer dispenseStatus;

    /** 发药人 */
    @TableField("dispense_by")
    @ApiModelProperty(value = "发药人")
    private String dispenseBy;

    /** 发药时点 */
    @TableField("dispensed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "发药时点")
    private Date dispensedAt;

    /** 往年补录单 0否 1是 */
    @TableField("backfill")
    @ApiModelProperty(value = "往年补录单")
    private Integer backfill;

    /** 闭环情形 0未闭环 1已闭环 2补录待核 */
    @TableField("close_status")
    @ApiModelProperty(value = "闭环情形")
    private Integer closeStatus;

    /** 闭环达成时点 */
    @TableField("closed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "闭环达成时点")
    private Date closedAt;

    /** 单情形 0在办 1已停嘱 */
    @TableField("status")
    @ApiModelProperty(value = "单情形")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    /** 执行条数（列表页展示用，与详情同一条 COUNT，不落库） */
    @TableField(exist = false)
    @ApiModelProperty(value = "执行条数（与详情同口径）")
    private Long execCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPatientNo() {
        return patientNo;
    }

    public void setPatientNo(String patientNo) {
        this.patientNo = patientNo;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public Integer getOrderType() {
        return orderType;
    }

    public void setOrderType(Integer orderType) {
        this.orderType = orderType;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Date getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Date periodStart) {
        this.periodStart = periodStart;
    }

    public Date getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Date periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Integer getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(Integer reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(String reviewBy) {
        this.reviewBy = reviewBy;
    }

    public Date getReviewAt() {
        return reviewAt;
    }

    public void setReviewAt(Date reviewAt) {
        this.reviewAt = reviewAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public String getOverrideBy() {
        return overrideBy;
    }

    public void setOverrideBy(String overrideBy) {
        this.overrideBy = overrideBy;
    }

    public Date getOverrideAt() {
        return overrideAt;
    }

    public void setOverrideAt(Date overrideAt) {
        this.overrideAt = overrideAt;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public Integer getDispenseStatus() {
        return dispenseStatus;
    }

    public void setDispenseStatus(Integer dispenseStatus) {
        this.dispenseStatus = dispenseStatus;
    }

    public String getDispenseBy() {
        return dispenseBy;
    }

    public void setDispenseBy(String dispenseBy) {
        this.dispenseBy = dispenseBy;
    }

    public Date getDispensedAt() {
        return dispensedAt;
    }

    public void setDispensedAt(Date dispensedAt) {
        this.dispensedAt = dispensedAt;
    }

    public Integer getBackfill() {
        return backfill;
    }

    public void setBackfill(Integer backfill) {
        this.backfill = backfill;
    }

    public Integer getCloseStatus() {
        return closeStatus;
    }

    public void setCloseStatus(Integer closeStatus) {
        this.closeStatus = closeStatus;
    }

    public Date getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Date closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getExecCount() {
        return execCount;
    }

    public void setExecCount(Long execCount) {
        this.execCount = execCount;
    }

}
