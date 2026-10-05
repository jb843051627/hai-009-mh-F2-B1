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
 * t_mh_order_exec 对象 t_mh_order_exec（医嘱执行记录）
 *
 * <p>落笔铁律：</p>
 * <ul>
 *   <li>同一剂（order_id, dose_seq）全库至多一笔，同一幂等键全库至多一笔——
 *       点两次、交接班两人同时提交同一剂、网络重试，都撞在这道唯一约束上，
 *       撞上的那方拿到已落的那笔，库里不会多笔。</li>
 *   <li>{@link #executor} 是闭环硬条件：空着的笔（含往年补录混进来的脏数据）永远不计入闭环，
 *       执行入口对空执行人直接拒收、不落笔。</li>
 *   <li>{@link #deptId} 是落笔当时的归属科室快照：账留当时科室，转科不改历史。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_mh_order_exec")
@ApiModel(value = "TMhOrderExec", description = "t_mh_order_exec 医嘱执行记录")
public class TMhOrderExec implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 医嘱单主键 */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "医嘱单主键")
    private Long orderId;

    /** 剂次（1..plan_count，同一医嘱下唯一） */
    @TableField("dose_seq")
    @ApiModelProperty(value = "剂次（1..plan_count，同一医嘱下唯一）")
    private Integer doseSeq;

    /** 幂等键（同一次执行意图同一个键，重试/双击不重复落笔） */
    @TableField("idem_key")
    @ApiModelProperty(value = "幂等键")
    private String idemKey;

    /** 执行人（闭环硬条件：空着不计入，入口拒收空执行人） */
    @TableField("executor")
    @ApiModelProperty(value = "执行人（闭环硬条件）")
    private String executor;

    /** 执行时点（时段型按它判在不在窗口内） */
    @TableField("executed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行时点")
    private Date executedAt;

    /** 落笔当时归属科室（账留当时科室，转科不改历史） */
    @TableField("dept_id")
    @ApiModelProperty(value = "落笔当时归属科室")
    private String deptId;

    /** 0正常执行 1往年补录（补录同样要执行人） */
    @TableField("backfilled")
    @ApiModelProperty(value = "0正常执行 1往年补录")
    private Integer backfilled;

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

    /** 备注（窗口外执行不计入闭环等在此明写） */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getDoseSeq() {
        return doseSeq;
    }

    public void setDoseSeq(Integer doseSeq) {
        this.doseSeq = doseSeq;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public Date getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Date executedAt) {
        this.executedAt = executedAt;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public Integer getBackfilled() {
        return backfilled;
    }

    public void setBackfilled(Integer backfilled) {
        this.backfilled = backfilled;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
