package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhTriaRuleMapper;
import com.fc.v2.model.auto.TMhTriaRule;
import com.fc.v2.model.custom.TriageResult;
import com.fc.v2.service.ITMhTriaRuleService;

/**
 * 急诊分诊判定线 Service业务层处理（rule-eval 形状）。
 *
 * <p>全院口径就这一处，必须讲死：</p>
 * <ul>
 *   <li>定级只看"分诊发生那一刻"（triageAt）生效的线，不看今天；生效窗口含起用日、不含交棒日，
 *       交棒日为空表示长期有效——旧线管到交棒前一刻，新线从交棒那一刻起，全院两块屏看到同一条。</li>
 *   <li>新定级只选 status=0 现行线；status=1 已让位的线不删，只供按代号翻旧账复查。</li>
 *   <li>同一时段并着几条线：priority 数值大的说话；并列按 rule_code 排后一条（代码序，年年一致）。</li>
 *   <li>三个分界数把得分切成四段，正好压在界值上算上一档；某档界值为空就不设那档，往下一档接着判。</li>
 *   <li>输入为负 / 空着不评 / 超过量程退回护士重录；查不到可用线明写"无可用标准"，不拿前后几天凑合。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhTriaRuleServiceImpl implements ITMhTriaRuleService {

    /** 现行 */
    private static final int STATUS_ACTIVE = 0;

    /** 顺位为空时按最低顺位处理 */
    private static final int PRIORITY_NULL = Integer.MIN_VALUE;

    /**
     * 赢线裁决：顺位降序；顺位并列按代号字典序降序（"代号排后一条"赢），年年同讲法、不随时间变。
     * 代号为空当作最小，永远赢不了并列局。
     */
    private static final Comparator<TMhTriaRule> BY_PRIORITY_THEN_CODE_DESC = new Comparator<TMhTriaRule>() {
        @Override
        public int compare(TMhTriaRule a, TMhTriaRule b) {
            int pa = a.getPriority() == null ? PRIORITY_NULL : a.getPriority();
            int pb = b.getPriority() == null ? PRIORITY_NULL : b.getPriority();
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMhTriaRuleMapper mhTriaRuleMapper;

    /**
     * 时段比较统一走墙钟字符串（yyyy-MM-dd HH:mm:ss）：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使交棒当日这类用例在边界上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TMhTriaRule selectTMhTriaRuleById(Long id) {
        return this.mhTriaRuleMapper.selectById(id);
    }

    @Override
    public List<TMhTriaRule> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TMhTriaRule>();
        }
        List<TMhTriaRule> rows = loadRules(STATUS_ACTIVE);
        List<TMhTriaRule> out = new ArrayList<TMhTriaRule>();
        for (TMhTriaRule r : rows) {
            if (inWindow(r, at)) {
                out.add(r);
            }
        }
        // 顺序就是裁决顺序：看板、角标、取优必须看到同一个排列
        Collections.sort(out, BY_PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        // 老签名表达不了"退回重录/无可用标准"的区别，两种情形都维持返回 0；
        // 新代码需要三态区分时请走 grade / gradeByCode。
        if (rejectReason(input, at) != null) {
            return 0;
        }
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return 0;
        }
        TMhTriaRule rule = findRuleByCode(ruleCode, at, STATUS_ACTIVE);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (rejectReason(input, at) != null) {
            return 0;
        }
        TMhTriaRule rule = findActiveRule(null, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMhTriaRule r = this.mhTriaRuleMapper.selectById(id);
        if (r == null || !isZero(r.getDelFlag()) || r.getStatus() == null
                || r.getStatus().intValue() != STATUS_ACTIVE) {
            return false;
        }
        // 空界值不是故障（空着就不设那一档），可用性只看身份与生效窗口
        return inWindow(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        int n = 0;
        for (TMhTriaRule r : loadRules(STATUS_ACTIVE)) {
            if (inWindow(r, at)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public TriageResult grade(String road, BigDecimal rawScore, Date triageAt) {
        String reject = rejectReason(rawScore, triageAt);
        if (reject != null) {
            return TriageResult.rejected(rawScore, reject);
        }
        TMhTriaRule rule = findActiveRule(road, triageAt);
        if (rule == null) {
            return TriageResult.noRule(rawScore, triageAt,
                    "无可用标准：分诊时点没有生效的判定线，不许拿前后几天的线凑合");
        }
        int level = levelOf(rule, rawScore);
        return TriageResult.ok(rule, level, rawScore, triageAt);
    }

    @Override
    public TriageResult gradeByCode(String ruleCode, BigDecimal rawScore, Date triageAt) {
        String reject = rejectReason(rawScore, triageAt);
        if (reject != null) {
            return TriageResult.rejected(rawScore, reject);
        }
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return TriageResult.noRule(rawScore, triageAt, "无可用标准：单上未记判定线代号");
        }
        // 复查翻旧账：status 不限，已让位的线照样调出来，但必须压在分诊当时的生效窗口上
        TMhTriaRule rule = findRuleByCode(ruleCode, triageAt, null);
        if (rule == null) {
            return TriageResult.noRule(rawScore, triageAt,
                    "无可用标准：单上所记判定线在分诊时点不生效，不允许用今天的标准改昨天的单");
        }
        int level = levelOf(rule, rawScore);
        return TriageResult.ok(rule, level, rawScore, triageAt);
    }

    @Override
    public TMhTriaRule findActiveRule(String road, Date at) {
        if (at == null) {
            return null;
        }
        TMhTriaRule best = null;
        // 定级只看分诊发生那一刻在窗口里的线（补录旧单按旧线算，不看今天它现不现役）：
        // 现行线直接参选；已让位线只有确实交过棒（eff_end 非空）才允许在其历史窗口内被时间点选中，
        // 已让位却没有交棒日的脏线不参与新定级，只能按代号复查调出。
        for (TMhTriaRule r : loadRules(null)) {
            if (!inWindow(r, at) || !roadMatch(r, road) || !gradable(r)) {
                continue;
            }
            if (best == null || BY_PRIORITY_THEN_CODE_DESC.compare(r, best) < 0) {
                best = r;
            }
        }
        return best;
    }

    @Override
    public TMhTriaRule findRuleByCodeAt(String ruleCode, Date at) {
        if (ruleCode == null || at == null) {
            return null;
        }
        return findRuleByCode(ruleCode, at, null);
    }

    // ---------------------------------------------------------------------
    // 内部口径
    // ---------------------------------------------------------------------

    /** 载入未删除的判定线；status 不为 null 时限定情形（0 现行 / 1 已让位） */
    private List<TMhTriaRule> loadRules(Integer status) {
        QueryWrapper<TMhTriaRule> q = new QueryWrapper<TMhTriaRule>();
        q.eq("del_flag", 0);
        if (status != null) {
            q.eq("status", status.intValue());
        }
        List<TMhTriaRule> rows = this.mhTriaRuleMapper.selectList(q);
        return rows == null ? new ArrayList<TMhTriaRule>() : rows;
    }

    /**
     * 生效窗口：起用日与交棒日都算在窗口内。
     * 起用日为空视为早已生效；交棒日为空（含早期导入的 0000-00-00 脏值）视为长期有效。
     */
    private boolean inWindow(TMhTriaRule r, Date at) {
        String now = ts(at);
        String s = r.getEffStart() == null ? null : ts(r.getEffStart());
        String e = r.getEffEnd() == null ? null : ts(r.getEffEnd());
        if (e != null && e.startsWith("0000-00-00")) {
            e = null;
        }
        boolean started = (s == null) || s.compareTo(now) <= 0;
        boolean notEnded = (e == null) || now.compareTo(e) <= 0;
        return started && notEnded;
    }

    /** 途径匹配：线不限途径（空）时通吃；否则必须与分诊途径一致。分诊途径为空只命中不限途径的线 */
    private boolean roadMatch(TMhTriaRule r, String road) {
        String ruleRoad = r.getApplyRoad() == null ? "" : r.getApplyRoad().trim();
        if (ruleRoad.isEmpty()) {
            return true;
        }
        return road != null && ruleRoad.equals(road.trim());
    }

    /**
     * 该线能否参与"按时间点取线"的新定级（含补录）：
     * 现行线可以；已让位线必须确实交过棒（eff_end 非空），它在自己的历史窗口内仍管当时的单；
     * 已让位却没有交棒日的线是被人为停用的脏配置，不参与定级，只能凭单上代号复查调出。
     */
    private boolean gradable(TMhTriaRule r) {
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_ACTIVE) {
            return true;
        }
        if (r.getEffEnd() == null) {
            return false;
        }
        String e = ts(r.getEffEnd());
        return !e.startsWith("0000-00-00");
    }

    /** 按代号取该时点生效的线；同代号多版（脏数据/换版重叠）时按裁决序取赢的一条；该时点不在窗口一律取不到 */
    private TMhTriaRule findRuleByCode(String ruleCode, Date at, Integer status) {
        if (ruleCode == null || at == null) {
            return null;
        }
        TMhTriaRule best = null;
        for (TMhTriaRule r : loadRules(status)) {
            if (!ruleCode.equals(r.getRuleCode())) {
                continue;
            }
            if (!inWindow(r, at)) {
                continue;
            }
            if (best == null || BY_PRIORITY_THEN_CODE_DESC.compare(r, best) < 0) {
                best = r;
            }
        }
        // 该时点一条生效版本都没有就是取不到：不许拿窗口外的版本凑合，复查按无可用标准明写
        return best;
    }

    /**
     * 录入退回理由：空着不评 / 为负 / 超过症状评分量程（离谱大数）。
     * 返回 null 表示输入收下。
     */
    private String rejectReason(BigDecimal input, Date at) {
        if (at == null) {
            return "退回重录：分诊发生时点缺失，无法按当时标准取线";
        }
        if (input == null) {
            return "退回重录：症状得分为空，空着不评";
        }
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            return "退回重录：症状得分为负数";
        }
        if (input.compareTo(TriageResult.SCORE_MAX) > 0) {
            return "退回重录：症状得分超出量程（>" + TriageResult.SCORE_MAX + "），属离谱大数";
        }
        return null;
    }

    /**
     * 分档：三个分界数把得分切成四段，compareTo&lt;=0 保证正好压在界值上算上一档；
     * 某档界值为空就不设那一档，往下一档接着判；全空时一律落到第 4 档（非急症），不是故障。
     */
    private int levelOf(TMhTriaRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }

    private boolean isZero(Integer v) {
        return v != null && v.intValue() == 0;
    }
}
