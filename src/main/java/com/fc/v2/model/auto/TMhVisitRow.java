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
 * t_mh_visit_row 对象 t_mh_visit_row（急诊分诊单）
 *
 * @author fuce
 * @date 2026-09-14
 */
@TableName("t_mh_visit_row")
@ApiModel(value = "TMhVisitRow", description = "t_mh_visit_row 急诊分诊单")
public class TMhVisitRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 级别常量：无可用标准（单上明写，不落级） */
    public static final int LEVEL_NO_RULE = 0;
    /** 级别：危重 */
    public static final int LEVEL_CRITICAL = 1;
    /** 级别：紧急 */
    public static final int LEVEL_URGENT = 2;
    /** 级别：普通 */
    public static final int LEVEL_NORMAL = 3;
    /** 级别：非急症 */
    public static final int LEVEL_NON_URGENT = 4;

    /** 情形：待复核 */
    public static final int STATUS_PENDING = 0;
    /** 情形：已复核（重判与原级别一致，封卷） */
    public static final int STATUS_REVIEWED = 1;
    /** 情形：退回重录 */
    public static final int STATUS_REJECTED = 2;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 分诊单号 */
    @TableField("ticket_no")
    @ApiModelProperty(value = "分诊单号")
    private String ticketNo;

    /** 分诊途径（急诊/门诊/留观） */
    @TableField("road")
    @ApiModelProperty(value = "分诊途径（急诊/门诊/留观）")
    private String road;

    /** 原始症状得分（护士录入，原样留档） */
    @TableField("raw_score")
    @ApiModelProperty(value = "原始症状得分（护士录入，原样留档）")
    private BigDecimal rawScore;

    /** 系统判定级别 1危重 2紧急 3普通 4非急症 0无可用标准 */
    @TableField("level_num")
    @ApiModelProperty(value = "系统判定级别 1危重 2紧急 3普通 4非急症 0无可用标准")
    private Integer levelNum;

    /** 定级所用判定线代号（随后台回值落单，无标准时留空） */
    @TableField("rule_code")
    @ApiModelProperty(value = "定级所用判定线代号（随后台回值落单，无标准时留空）")
    private String ruleCode;

    /** 所用判定线的起用日（随线快照落单） */
    @TableField("rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "所用判定线的起用日（随线快照落单）")
    private Date ruleEffStart;

    /** 分诊发生时点（按这一时刻选生效线，不按今天） */
    @TableField("triage_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "分诊发生时点（按这一时刻选生效线，不按今天）")
    private Date triageAt;

    /** 最近一次复查时点 */
    @TableField("reviewed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近一次复查时点")
    private Date reviewedAt;

    /** 复查按单上线代号重判的级别（不改原级别） */
    @TableField("review_level")
    @ApiModelProperty(value = "复查按单上线代号重判的级别（不改原级别）")
    private Integer reviewLevel;

    /** 分诊单情形 0待复核 1已复核 2退回重录 */
    @TableField("status")
    @ApiModelProperty(value = "分诊单情形 0待复核 1已复核 2退回重录")
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

    /** 备注（无可用标准等在此明写） */
    @TableField("remark")
    @ApiModelProperty(value = "备注（无可用标准等在此明写）")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getRoad() {
        return road;
    }

    public void setRoad(String road) {
        this.road = road;
    }

    public BigDecimal getRawScore() {
        return rawScore;
    }

    public void setRawScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
    }

    public Integer getLevelNum() {
        return levelNum;
    }

    public void setLevelNum(Integer levelNum) {
        this.levelNum = levelNum;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRuleEffStart() {
        return ruleEffStart;
    }

    public void setRuleEffStart(Date ruleEffStart) {
        this.ruleEffStart = ruleEffStart;
    }

    public Date getTriageAt() {
        return triageAt;
    }

    public void setTriageAt(Date triageAt) {
        this.triageAt = triageAt;
    }

    public Date getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Integer getReviewLevel() {
        return reviewLevel;
    }

    public void setReviewLevel(Integer reviewLevel) {
        this.reviewLevel = reviewLevel;
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
