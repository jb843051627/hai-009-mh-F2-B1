package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhTriaRule;
import com.fc.v2.model.custom.TriageResult;

/**
 * TMhTriaRule 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhTriaRuleService {

    /** selectTMhTriaRuleById */
    TMhTriaRule selectTMhTriaRuleById(Long id);

    /** listAvailable：分诊时点生效的现行线（含起不含止），已按顺位/代号排好序 */
    List<TMhTriaRule> listAvailable(Date at);

    /** evaluate：按线代号取分诊时点生效的现行线定级（老签名，返回 0 含退回/无标准两种情形） */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** evaluateTop：按分诊时点顺位最高的生效线定级（老签名；补录旧单同样按当时的线，不看今天） */
    int evaluateTop(BigDecimal input, Date at);

    /** usable */
    boolean usable(Long id, Date at);

    /** countAvailable */
    int countAvailable(Date at);

    /**
     * 新单定级（新层）：按分诊发生时点选生效的现行线（status=0），
     * 同一时段并多条时顺位大的说话，并列按代号排后一条。
     * 输入为负/空/超量程 → 退回重录；查不到可用线 → 明写"无可用标准"。
     *
     * @param road     分诊途径（急诊/门诊/留观），空串表示只取不限途径的线
     * @param rawScore 护士录入的原始症状得分
     * @param triageAt 分诊发生时点（按它选线，不按今天）
     */
    TriageResult grade(String road, BigDecimal rawScore, Date triageAt);

    /**
     * 复查定级（新层）：只认单上记的线代号，把那条线（含已让位）调到分诊当时的生效窗口重判，
     * 不看今天的标准；非法输入同样退回。单上线在分诊时点查不到 → 无可用标准。
     */
    TriageResult gradeByCode(String ruleCode, BigDecimal rawScore, Date triageAt);

    /**
     * 定位分诊时点现行生效的赢线：顺位大优先、并列代号排后一条（确定性裁决）。
     * 只选 status=0；让位线不参与新定级。查不到返回 null。
     */
    TMhTriaRule findActiveRule(String road, Date at);

    /**
     * 按代号定位一条在指定时点生效的线（不限 status，已让位也可调出），供复查翻旧账。
     */
    TMhTriaRule findRuleByCodeAt(String ruleCode, Date at);
}
