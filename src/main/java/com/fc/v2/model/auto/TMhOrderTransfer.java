package com.fc.v2.model.auto;

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
 * t_mh_order_transfer 对象 t_mh_order_transfer（医嘱转科轨迹）
 *
 * <p>转科 = 原子改 t_mh_order.dept_id + 落一笔本轨迹，两步同事务。
 * 未执行的剂次随单归接收科室，已执行的账留在执行时科室（执行记录上的科室快照不动）。
 * 事后对"转科那一刻单子在谁头上"，只认本表 moved_at 与 from/to。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_mh_order_transfer")
@ApiModel(value = "TMhOrderTransfer", description = "t_mh_order_transfer 医嘱转科轨迹")
public class TMhOrderTransfer implements Serializable {
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

    /** 转出科室代号 */
    @TableField("from_dept")
    @ApiModelProperty(value = "转出科室代号")
    private String fromDept;

    /** 接收科室代号 */
    @TableField("to_dept")
    @ApiModelProperty(value = "接收科室代号")
    private String toDept;

    /** 转科经手人 */
    @TableField("moved_by")
    @ApiModelProperty(value = "转科经手人")
    private String movedBy;

    /** 转科时点 */
    @TableField("moved_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "转科时点")
    private Date movedAt;

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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getFromDept() {
        return fromDept;
    }

    public void setFromDept(String fromDept) {
        this.fromDept = fromDept;
    }

    public String getToDept() {
        return toDept;
    }

    public void setToDept(String toDept) {
        this.toDept = toDept;
    }

    public String getMovedBy() {
        return movedBy;
    }

    public void setMovedBy(String movedBy) {
        this.movedBy = movedBy;
    }

    public Date getMovedAt() {
        return movedAt;
    }

    public void setMovedAt(Date movedAt) {
        this.movedAt = movedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
