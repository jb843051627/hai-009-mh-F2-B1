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
 * t_mh_order 对象 t_mh_order（医嘱单）
 *
 * <p>闭环铁律（与 doc/schema/mh.sql 表头口径一致，两边只许同步改）：</p>
 * <ul>
 *   <li>一张单只认一种闭环算法，{@link #orderType} 落单定死、终身不改：
 *       次数型数够 plan_count 笔有效执行即闭环；时段型只数落在窗口内的有效执行。</li>
 *   <li>归属只有一个字段 {@link #deptId}，执行责任随病人走，转科即改这里并写轨迹，
 *       一单同时只属于一个科室，不存在"两边都挂着"。</li>
 *   <li>审方驳回后医生坚持要发的，只能走坚持放行：override 三件套必填落账，
 *       事后查"谁批的"就查 {@link #overrideBy}。</li>
 *   <li>状态推进全部条件更新（CAS），并发与重试叠在一起也只有一方改得动。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_mh_order")
@ApiModel(value = "TMhOrder", description = "t_mh_order 医嘱单")
public class TMhOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 闭环算法型别：次数型（有效执行笔数顶到 plan_count 即闭环，不看执行时间） */
    public static final int TYPE_COUNT = 1;
    /** 闭环算法型别：时段型（只数落在 [windowStart, windowEnd) 窗口内的有效执行） */
    public static final int TYPE_WINDOW = 2;

    /** 情形：待审方（录入落单后） */
    public static final int STATUS_DRAFT = 0;
    /** 情形：待发药（审方通过，含驳回后坚持放行到达） */
    public static final int STATUS_REVIEWED = 1;
    /** 情形：执行中（已发药，逐剂执行） */
    public static final int STATUS_EXECUTING = 2;
    /** 情形：已闭环（有效执行顶到达标线，终态） */
    public static final int STATUS_CLOSED = 3;
    /** 情形：已驳回（审方打回，医生可坚持放行） */
    public static final int STATUS_REJECTED = 4;

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

    /** 闭环算法型别 1次数型 2时段型（落单定死，终身不改） */
    @TableField("order_type")
    @ApiModelProperty(value = "闭环算法型别 1次数型 2时段型")
    private Integer orderType;

    /** 计划剂次数（闭环达标线，>0） */
    @TableField("plan_count")
    @ApiModelProperty(value = "计划剂次数（闭环达标线）")
    private Integer planCount;

    /** 时段型窗口起（含）；次数型留空 */
    @TableField("window_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "时段型窗口起（含）")
    private Date windowStart;

    /** 时段型窗口止（不含）；次数型留空 */
    @TableField("window_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "时段型窗口止（不含）")
    private Date windowEnd;

    /** 当前归属科室代号（唯一归属，转科即改） */
    @TableField("dept_id")
    @ApiModelProperty(value = "当前归属科室代号（唯一归属，转科即改）")
    private String deptId;

    /** 情形 0待审方 1待发药 2执行中 3已闭环 4已驳回 */
    @TableField("status")
    @ApiModelProperty(value = "情形 0待审方 1待发药 2执行中 3已闭环 4已驳回")
    private Integer status;

    /** 审方药剂师 */
    @TableField("review_by")
    @ApiModelProperty(value = "审方药剂师")
    private String reviewBy;

    /** 审方时点 */
    @TableField("review_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "审方时点")
    private Date reviewAt;

    /** 审方意见（驳回必写缘由） */
    @TableField("review_comment")
    @ApiModelProperty(value = "审方意见")
    private String reviewComment;

    /** 驳回后坚持放行的医生（事后查谁批的认这个字段） */
    @TableField("override_by")
    @ApiModelProperty(value = "驳回后坚持放行的医生")
    private String overrideBy;

    /** 坚持放行时点 */
    @TableField("override_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "坚持放行时点")
    private Date overrideAt;

    /** 坚持放行理由（必填，不许空着放） */
    @TableField("override_reason")
    @ApiModelProperty(value = "坚持放行理由")
    private String overrideReason;

    /** 发药人 */
    @TableField("dispense_by")
    @ApiModelProperty(value = "发药人")
    private String dispenseBy;

    /** 发药时点 */
    @TableField("dispense_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "发药时点")
    private Date dispenseAt;

    /** 闭环时点（最后一笔有效执行顶到达标线那一下） */
    @TableField("closed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "闭环时点")
    private Date closedAt;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
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

    public Integer getOrderType() {
        return orderType;
    }

    public void setOrderType(Integer orderType) {
        this.orderType = orderType;
    }

    public Integer getPlanCount() {
        return planCount;
    }

    public void setPlanCount(Integer planCount) {
        this.planCount = planCount;
    }

    public Date getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Date windowStart) {
        this.windowStart = windowStart;
    }

    public Date getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Date windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public String getDispenseBy() {
        return dispenseBy;
    }

    public void setDispenseBy(String dispenseBy) {
        this.dispenseBy = dispenseBy;
    }

    public Date getDispenseAt() {
        return dispenseAt;
    }

    public void setDispenseAt(Date dispenseAt) {
        this.dispenseAt = dispenseAt;
    }

    public Date getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Date closedAt) {
        this.closedAt = closedAt;
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

}
