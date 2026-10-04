package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TMhTriaRule;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 分诊定级结果（后台算完写死，页面不预填）。
 * <p>三态必须分清，不许都拿 0 糊弄：</p>
 * <ul>
 *   <li>{@link #isRejected()}：得分为负 / 空着不评 / 离谱大数，退回护士重录，不落单；</li>
 *   <li>{@link #isNoRule()}：输入合法但分诊时点查不到生效的现行线，单上明写"无可用标准"；</li>
 *   <li>{@link #isSuccess()}：定级成功，{@link #getLevel()} 为 1..4，并带所用线的代号与起用日快照。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "TriageResult", description = "分诊定级结果")
public class TriageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 录入允许的最高得分，超过视为离谱大数退回重录（症状评分量程上限） */
    public static final BigDecimal SCORE_MAX = new BigDecimal("100");

    @ApiModelProperty(value = "是否定级成功（级别1..4）")
    private boolean success;

    @ApiModelProperty(value = "是否退回重录（得分负/空/超量程）")
    private boolean rejected;

    @ApiModelProperty(value = "是否无可用标准")
    private boolean noRule;

    @ApiModelProperty(value = "级别 1危重 2紧急 3普通 4非急症 0未定级")
    private int level;

    @ApiModelProperty(value = "原始得分")
    private BigDecimal rawScore;

    @ApiModelProperty(value = "所用判定线代号")
    private String ruleCode;

    @ApiModelProperty(value = "所用判定线名")
    private String ruleName;

    @ApiModelProperty(value = "所用判定线的起用日")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ruleEffStart;

    @ApiModelProperty(value = "分诊发生时点")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date triageAt;

    @ApiModelProperty(value = "给护士/复核看的明话")
    private String message;

    @ApiModelProperty(value = "落单后的分诊单主键")
    private Long visitRowId;

    @ApiModelProperty(value = "分诊单号")
    private String ticketNo;

    /** 级别明称，与单上口径一致，两块屏看同一句话 */
    public static String levelName(int level) {
        switch (level) {
            case 1:
                return "危重";
            case 2:
                return "紧急";
            case 3:
                return "普通";
            case 4:
                return "非急症";
            default:
                return "无可用标准";
        }
    }

    public static TriageResult rejected(BigDecimal rawScore, String message) {
        TriageResult r = new TriageResult();
        r.success = false;
        r.rejected = true;
        r.noRule = false;
        r.level = 0;
        r.rawScore = rawScore;
        r.message = message;
        return r;
    }

    public static TriageResult noRule(BigDecimal rawScore, Date triageAt, String message) {
        TriageResult r = new TriageResult();
        r.success = false;
        r.rejected = false;
        r.noRule = true;
        r.level = 0;
        r.rawScore = rawScore;
        r.triageAt = triageAt;
        r.message = message;
        return r;
    }

    public static TriageResult ok(TMhTriaRule rule, int level, BigDecimal rawScore, Date triageAt) {
        TriageResult r = new TriageResult();
        r.success = true;
        r.rejected = false;
        r.noRule = false;
        r.level = level;
        r.rawScore = rawScore;
        r.triageAt = triageAt;
        if (rule != null) {
            r.ruleCode = rule.getRuleCode();
            r.ruleName = rule.getRuleName();
            r.ruleEffStart = rule.getEffStart();
        }
        r.message = "定级完成";
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean isNoRule() {
        return noRule;
    }

    public int getLevel() {
        return level;
    }

    public BigDecimal getRawScore() {
        return rawScore;
    }

    public void setRawScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public Date getRuleEffStart() {
        return ruleEffStart;
    }

    public Date getTriageAt() {
        return triageAt;
    }

    public void setTriageAt(Date triageAt) {
        this.triageAt = triageAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getVisitRowId() {
        return visitRowId;
    }

    public void setVisitRowId(Long visitRowId) {
        this.visitRowId = visitRowId;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }
}
