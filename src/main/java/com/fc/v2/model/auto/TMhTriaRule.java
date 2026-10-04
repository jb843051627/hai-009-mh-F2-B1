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
import java.math.BigDecimal;
import java.util.Date;

/**
 * t_mh_tria_rule 对象 t_mh_tria_rule
 *
 * @author fuce
 * @date 2026-09-14
 */
@TableName("t_mh_tria_rule")
@ApiModel(value = "TMhTriaRule", description = "t_mh_tria_rule")
public class TMhTriaRule implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 分诊判定线代号 */
    @TableField("rule_code")
    @ApiModelProperty(value = "分诊判定线代号")
    private String ruleCode;

    /** 判定线名 */
    @TableField("rule_name")
    @ApiModelProperty(value = "判定线名")
    private String ruleName;

    /** 适用途径（急诊/门诊/留观等，空=不限途径） */
    @TableField("apply_road")
    @ApiModelProperty(value = "适用途径（急诊/门诊/留观等，空=不限途径）")
    private String applyRoad;

    /** 危重档界值（含），空=不设危重档 */
    @TableField("th1_max")
    @ApiModelProperty(value = "危重档界值（含），空=不设危重档")
    private BigDecimal th1Max;

    /** 紧急档界值（含），空=不设紧急档 */
    @TableField("th2_max")
    @ApiModelProperty(value = "紧急档界值（含），空=不设紧急档")
    private BigDecimal th2Max;

    /** 普通档界值（含），空=不设普通档 */
    @TableField("th3_max")
    @ApiModelProperty(value = "普通档界值（含），空=不设普通档")
    private BigDecimal th3Max;

    /** 启用之日（含） */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "启用之日")
    private Date effStart;

    /** 交棒之日(不含)，空=长期有效 */
    @TableField("eff_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "交棒之日(不含)，空=长期有效")
    private Date effEnd;

    /** 判定线顺位(数值大的先说话) */
    @TableField("priority")
    @ApiModelProperty(value = "判定线顺位(数值大的先说话)")
    private Integer priority;

    /** 判定线情形 0现行 1已让位 */
    @TableField("status")
    @ApiModelProperty(value = "判定线情形 0现行 1已让位")
    private Integer status;

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

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getApplyRoad() {
        return applyRoad;
    }

    public void setApplyRoad(String applyRoad) {
        this.applyRoad = applyRoad;
    }

    public BigDecimal getTh1Max() {
        return th1Max;
    }

    public void setTh1Max(BigDecimal th1Max) {
        this.th1Max = th1Max;
    }

    public BigDecimal getTh2Max() {
        return th2Max;
    }

    public void setTh2Max(BigDecimal th2Max) {
        this.th2Max = th2Max;
    }

    public BigDecimal getTh3Max() {
        return th3Max;
    }

    public void setTh3Max(BigDecimal th3Max) {
        this.th3Max = th3Max;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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

}
