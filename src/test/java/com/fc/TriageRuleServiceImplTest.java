package com.fc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TMhTriaRule;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.model.custom.TriageResult;

/**
 * 急诊分诊判定线口径测试。
 */
public class TriageRuleServiceImplTest extends AbstractH2Test {

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ---------- 分档：四个段位，卡线算上一档 ----------

    @Test
    void level_bands_with_current_v2_line() {
        // 2026 现行线 15/30/50
        assertEquals(1, triaRuleService.grade("急诊", bd("1"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(1, triaRuleService.grade("急诊", bd("15"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(2, triaRuleService.grade("急诊", bd("16"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(2, triaRuleService.grade("急诊", bd("30"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(3, triaRuleService.grade("急诊", bd("31"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(3, triaRuleService.grade("急诊", bd("50"), t("2026-03-01 09:00:00")).getLevel());
        assertEquals(4, triaRuleService.grade("急诊", bd("51"), t("2026-03-01 09:00:00")).getLevel());
    }

    @Test
    void score_exactly_on_threshold_goes_to_higher_band() {
        // 正好压在分界数上算上一级（15 必须是危重而不是紧急）
        TriageResult r = triaRuleService.grade("急诊", bd("15"), t("2026-03-01 09:00:00"));
        assertTrue(r.isSuccess());
        assertEquals(1, r.getLevel());
        assertEquals("TRIAGE-V2", r.getRuleCode());
    }

    // ---------- 交棒边界：含起不含止，全院同一刻切换 ----------

    @Test
    void handoff_exclusive_boundary_old_rules_up_to_last_moment() {
        // 交棒前一刻（2025-12-31 23:59:59）仍是旧线：40 压界算紧急档（旧线口径）
        TriageResult before = triaRuleService.grade("急诊", bd("40"), t("2025-12-31 23:59:59"));
        assertTrue(before.isSuccess());
        assertEquals("TRIAGE-V1", before.getRuleCode());
        assertEquals(2, before.getLevel());
    }

    @Test
    void handoff_exclusive_boundary_new_line_at_exact_changeover() {
        // 交棒那一刻 00:00:00 起新线说话：40 在新线 15/30/50 下落普通档
        TriageResult at = triaRuleService.grade("急诊", bd("40"), t("2026-01-01 00:00:00"));
        assertTrue(at.isSuccess());
        assertEquals("TRIAGE-V2", at.getRuleCode());
        assertEquals(3, at.getLevel());
    }

    @Test
    void backfilled_old_ticket_uses_old_line_even_today() {
        // 今年补录去年的分诊单，按分诊那天的旧线算，不看今天
        TriageResult r = triaRuleService.grade("急诊", bd("20"), t("2025-06-01 10:00:00"));
        assertEquals("TRIAGE-V1", r.getRuleCode());
        assertEquals(1, r.getLevel()); // 20 压旧线一档上界
    }

    // ---------- 顺位与代号裁决 ----------

    @Test
    void priority_higher_wins_when_lines_overlap() {
        // 2026 急诊途径：V2(priority100) 压过 EARLY(priority10)
        TMhTriaRule rule = triaRuleService.findActiveRule("急诊", t("2026-03-01 08:00:00"));
        assertEquals("TRIAGE-V2", rule.getRuleCode());
    }

    @Test
    void priority_tie_resolved_by_code_desc_deterministically() {
        // 造两条同顺位、同时段的线，代号 A 与 B，年年都取排后的 B
        insertRule(900, "TRIAGE-TIEA", "并列A", "急诊", "10.00", "20.00", "30.00",
                "2026-02-01 00:00:00", null, 200, 0);
        insertRule(901, "TRIAGE-TIEB", "并列B", "急诊", "12.00", "22.00", "32.00",
                "2026-02-01 00:00:00", null, 200, 0);
        TMhTriaRule rule = triaRuleService.findActiveRule("急诊", t("2026-02-10 00:00:00"));
        assertEquals("TRIAGE-TIEB", rule.getRuleCode());
        // 再跑一次，结论不能今年挑前条明年挑后条
        TMhTriaRule again = triaRuleService.findActiveRule("急诊", t("2026-02-10 00:00:00"));
        assertEquals("TRIAGE-TIEB", again.getRuleCode());
    }

    // ---------- 让位线 ----------

    @Test
    void retired_line_without_end_date_never_grades_new_ticket() {
        // ZDEAD 已让位且无交棒日，界值最松也绝不能赢——即使它顺位 100、代号最靠后
        TMhTriaRule rule = triaRuleService.findActiveRule("急诊", t("2025-06-01 00:00:00"));
        assertEquals("TRIAGE-V1", rule.getRuleCode());
        TMhTriaRule now = triaRuleService.findActiveRule("急诊", t("2026-06-01 00:00:00"));
        assertEquals("TRIAGE-V2", now.getRuleCode());
    }

    @Test
    void retired_line_still_loadable_by_code_for_review() {
        // 复查翻旧账：已让位的 V1 凭代号照样调出来，按当时窗口重判
        TMhTriaRule v1 = triaRuleService.findRuleByCodeAt("TRIAGE-V1", t("2025-06-01 12:00:00"));
        assertNotNull(v1);
        assertEquals("TRIAGE-V1", v1.getRuleCode());
        // 但今天的时点它不在窗口里，不许拿它改今天的单
        assertNull(triaRuleService.findRuleByCodeAt("TRIAGE-V1", t("2026-06-01 12:00:00")));
    }

    // ---------- 空界值：空档下跳，不是故障 ----------

    @Test
    void null_threshold_skips_that_band() {
        // 让 EARLY 成为赢线（同途径下把 V2 让位式压过：直接给 EARLY 抬顺位）
        TMhTriaRule early = triaRuleMapper.selectById(3L);
        early.setPriority(999);
        triaRuleMapper.updateById(early);
        // EARLY 二档为空（15/null/50）：
        // 15 -> 危重；20 超过 15，紧急档不设，接着看普通档 -> 普通(3)
        assertEquals(1, triaRuleService.grade("急诊", bd("15"), t("2026-03-01 00:00:00")).getLevel());
        assertEquals(3, triaRuleService.grade("急诊", bd("20"), t("2026-03-01 00:00:00")).getLevel());
        assertEquals(4, triaRuleService.grade("急诊", bd("51"), t("2026-03-01 00:00:00")).getLevel());
    }

    // ---------- 途径 ----------

    @Test
    void road_match_and_general_line() {
        // 门诊途径没有专线，落不限途径的 GENERAL（10/25/45）
        TriageResult r = triaRuleService.grade("门诊", bd("25"), t("2026-03-01 00:00:00"));
        assertTrue(r.isSuccess());
        assertEquals("TRIAGE-GENERAL", r.getRuleCode());
        assertEquals(2, r.getLevel());
        // 急诊途径：V2(100) 赢过 GENERAL(50)
        assertEquals("TRIAGE-V2",
                triaRuleService.grade("急诊", bd("25"), t("2026-03-01 00:00:00")).getRuleCode());
    }

    // ---------- 非法输入：退回重录 ----------

    @Test
    void reject_negative_null_and_absurd_scores() {
        TriageResult neg = triaRuleService.grade("急诊", bd("-1"), t("2026-03-01 00:00:00"));
        assertTrue(neg.isRejected());
        assertFalse(neg.isSuccess());

        TriageResult empty = triaRuleService.grade("急诊", null, t("2026-03-01 00:00:00"));
        assertTrue(empty.isRejected());

        TriageResult huge = triaRuleService.grade("急诊", bd("100.01"), t("2026-03-01 00:00:00"));
        assertTrue(huge.isRejected());

        // 100 是量程上界，算合法
        assertTrue(triaRuleService.grade("急诊", bd("100"), t("2026-03-01 00:00:00")).isSuccess());
    }

    // ---------- 无可用标准：明写，不凑合 ----------

    @Test
    void no_rule_before_any_line_started() {
        // 2024 年没有任何生效线
        TriageResult r = triaRuleService.grade("急诊", bd("20"), t("2024-06-01 00:00:00"));
        assertTrue(r.isNoRule());
        assertFalse(r.isSuccess());
        assertEquals(0, r.getLevel());
    }

    @Test
    void no_rule_for_unmatched_road() {
        TriageResult r = triaRuleService.grade("留观", bd("20"), t("2026-03-01 00:00:00"));
        // 留观途径：V2/EARLY 都是急诊专线不匹配，GENERAL 不限途径 -> 其实有通用线
        assertTrue(r.isSuccess());
        assertEquals("TRIAGE-GENERAL", r.getRuleCode());

        // 删掉通用线后留观即无标准，不许拿急诊线凑合
        TMhTriaRule g = triaRuleMapper.selectById(5L);
        g.setDelFlag(1);
        triaRuleMapper.updateById(g);
        TriageResult none = triaRuleService.grade("留观", bd("20"), t("2026-03-01 00:00:00"));
        assertTrue(none.isNoRule());
    }

    @Test
    void list_and_count_only_count_current_lines_in_window() {
        // 看板口径只认现行线：2025 窗口现行线为 0（V1 已让位；让位线不进看板）
        assertEquals(0, triaRuleService.countAvailable(t("2025-06-01 00:00:00")));
        assertTrue(triaRuleService.listAvailable(t("2025-06-01 00:00:00")).isEmpty());

        // 2026 现行窗口：V2、EARLY、GENERAL 三条
        assertEquals(3, triaRuleService.countAvailable(t("2026-06-01 00:00:00")));
        assertEquals("TRIAGE-V2", triaRuleService.listAvailable(t("2026-06-01 00:00:00")).get(0).getRuleCode());
    }

    // ---------- 落单与复查 ----------

    @Test
    void record_triage_persists_score_level_rule_snapshot() {
        TriageResult r = visitRowService.recordTriage("急诊", bd("30"), t("2026-03-02 08:30:00"));
        assertTrue(r.isSuccess());
        assertEquals(2, r.getLevel());
        assertEquals("TRIAGE-V2", r.getRuleCode());
        assertNotNull(r.getVisitRowId());
        assertNotNull(r.getTicketNo());

        TMhVisitRow row = visitRowMapper.selectById(r.getVisitRowId());
        assertEquals(0, row.getRawScore().compareTo(bd("30")));
        assertEquals(Integer.valueOf(2), row.getLevelNum());
        assertEquals("TRIAGE-V2", row.getRuleCode());
        assertEquals(t("2026-01-01 00:00:00"), row.getRuleEffStart());
        assertEquals(t("2026-03-02 08:30:00"), row.getTriageAt());
        assertEquals(Integer.valueOf(0), row.getStatus());
    }

    @Test
    void record_no_rule_ticket_explicitly_marked() {
        TriageResult r = visitRowService.recordTriage("急诊", bd("20"), t("2024-01-01 00:00:00"));
        assertTrue(r.isNoRule());
        assertNotNull(r.getVisitRowId());
        TMhVisitRow row = visitRowMapper.selectById(r.getVisitRowId());
        assertEquals(Integer.valueOf(0), row.getLevelNum());
        assertNull(row.getRuleCode());
        assertNull(row.getRuleEffStart());
        assertTrue(row.getRemark().contains("无可用标准"));
    }

    @Test
    void reject_does_not_create_ticket() {
        long before = countRows();
        TriageResult r = visitRowService.recordTriage("急诊", bd("-5"), t("2026-03-02 08:30:00"));
        assertTrue(r.isRejected());
        assertNull(r.getVisitRowId());
        assertEquals(before, countRows());
    }

    @Test
    void review_uses_recorded_code_and_keeps_original_level() {
        // 去年落单：V1 旧线，40 压二档界 -> 紧急
        TriageResult rec = visitRowService.recordTriage("急诊", bd("40"), t("2025-12-15 09:00:00"));
        assertEquals("TRIAGE-V1", rec.getRuleCode());
        assertEquals(2, rec.getLevel());

        // 今天复查：只认单上 V1，按 2025 当时口径仍判 2；若错用新线会变成 3
        TriageResult rev = visitRowService.reviewTriage(rec.getVisitRowId());
        assertTrue(rev.isSuccess());
        assertEquals(2, rev.getLevel());
        assertEquals("TRIAGE-V1", rev.getRuleCode());

        TMhVisitRow row = visitRowMapper.selectById(rec.getVisitRowId());
        assertEquals(Integer.valueOf(2), row.getLevelNum());      // 原级别不动
        assertEquals(Integer.valueOf(2), row.getReviewLevel());   // 复查级别单列
        assertNotNull(row.getReviewedAt());
        assertEquals(Integer.valueOf(1), row.getStatus());        // 一致 -> 已复核
    }

    @Test
    void review_gradebycode_rejects_bad_input_and_missing_window() {
        TriageResult rec = visitRowService.recordTriage("急诊", bd("40"), t("2025-12-15 09:00:00"));

        // 把单子上的分诊时点改到 V1 窗口外，复查不许用别的线凑合
        TMhVisitRow row = visitRowMapper.selectById(rec.getVisitRowId());
        row.setTriageAt(t("2024-01-01 00:00:00"));
        visitRowMapper.updateById(row);
        TriageResult rev = visitRowService.reviewTriage(rec.getVisitRowId());
        assertTrue(rev.isNoRule());
        assertEquals(0, rev.getLevel());

        // 原单仍保持原级别，没被今天的标准覆盖
        TMhVisitRow reloaded = visitRowMapper.selectById(rec.getVisitRowId());
        assertEquals(Integer.valueOf(2), reloaded.getLevelNum());
        assertEquals(Integer.valueOf(0), reloaded.getReviewLevel());
    }

    // ---------- helpers ----------

    private long countRows() {
        return visitRowMapper.selectCount(null);
    }

    private void insertRule(int id, String code, String name, String road,
                            String th1, String th2, String th3,
                            String start, String end, int priority, int status) {
        TMhTriaRule r = new TMhTriaRule();
        r.setId((long) id);
        r.setRuleCode(code);
        r.setRuleName(name);
        r.setApplyRoad(road);
        r.setTh1Max(th1 == null ? null : bd(th1));
        r.setTh2Max(th2 == null ? null : bd(th2));
        r.setTh3Max(th3 == null ? null : bd(th3));
        r.setEffStart(t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        triaRuleMapper.insert(r);
    }
}
