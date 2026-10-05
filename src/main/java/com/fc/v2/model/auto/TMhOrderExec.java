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
 * t_mh_order_exec 对象 医嘱执行记录。
 *
 * <p>一次计划执行 = (order_no, dose_seq)，全库唯一：点几次、几班并发、重试几轮
 * 都只落一笔。dept_no 是执行发生时的归属科室快照，不随转科改，交接班对账看这里。</p>
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_mh_order_exec")
@ApiModel(value = "TMhOrderExec", description = "医嘱执行记录")
public class TMhOrderExec implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属医嘱单号 */
    @TableField("order_no")
    @ApiModelProperty(value = "所属医嘱单号")
    private String orderNo;

    /** 计划内第几次（与 order_no 组成幂等键） */
    @TableField("dose_seq")
    @ApiModelProperty(value = "计划内第几次")
    private Integer doseSeq;

    /** 执行人（正常执行必填；往年补录可空，空则不计闭环） */
    @TableField("exec_by")
    @ApiModelProperty(value = "执行人")
    private String execBy;

    /** 执行时点 */
    @TableField("exec_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行时点")
    private Date execAt;

    /** 执行时归属科室快照（不随转科改） */
    @TableField("dept_no")
    @ApiModelProperty(value = "执行时归属科室快照")
    private String deptNo;

    /** 该笔为补录 0否 1是 */
    @TableField("backfill")
    @ApiModelProperty(value = "该笔为补录")
    private Integer backfill;

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

    public Integer getDoseSeq() {
        return doseSeq;
    }

    public void setDoseSeq(Integer doseSeq) {
        this.doseSeq = doseSeq;
    }

    public String getExecBy() {
        return execBy;
    }

    public void setExecBy(String execBy) {
        this.execBy = execBy;
    }

    public Date getExecAt() {
        return execAt;
    }

    public void setExecAt(Date execAt) {
        this.execAt = execAt;
    }

    public String getDeptNo() {
        return deptNo;
    }

    public void setDeptNo(String deptNo) {
        this.deptNo = deptNo;
    }

    public Integer getBackfill() {
        return backfill;
    }

    public void setBackfill(Integer backfill) {
        this.backfill = backfill;
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
