package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.model.custom.TriageResult;
import com.fc.v2.service.ITMhTriaRuleService;
import com.fc.v2.service.ITMhVisitRowService;
import com.fc.v2.util.SnowflakeIdWorker;

/**
 * 急诊分诊单 Service 业务层处理。
 *
 * <p>落单铁律：级别、判定线代号、线起用日一律由后台定级服务算完写死，护士只录途径/得分/分诊时点，
 * 页面不预填结果。单子留下原始得分、级别、用哪条线、那条线的起用日；复查只认单上记的线代号，
 * 按分诊当时口径重判，重判结果单列 review_level，绝不拿今天的标准覆盖昨天的原级别。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhVisitRowServiceImpl implements ITMhVisitRowService {

    /** 无可用标准时单上写明的原话，护理部复核屏与急诊室屏都认这一句 */
    public static final String NO_RULE_REMARK = "无可用标准";

    @javax.annotation.Resource
    private TMhVisitRowMapper mhVisitRowMapper;

    @javax.annotation.Resource
    private ITMhTriaRuleService triaRuleService;

    @Override
    public TMhVisitRow selectTMhVisitRowById(Long id) {
        return this.mhVisitRowMapper.selectById(id);
    }

    @Override
    public List<TMhVisitRow> selectTMhVisitRowList(Wrapper<TMhVisitRow> queryWrapper) {
        return this.mhVisitRowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageResult recordTriage(String road, BigDecimal rawScore, Date triageAt) {
        // 定级只认分诊发生时点；补录旧单必须传当时时点，实时分诊不传则按此刻
        Date at = triageAt == null ? new Date() : triageAt;
        TriageResult result = this.triaRuleService.grade(road, rawScore, at);

        // 得分为负/空/离谱大数：退回护士重录，不落单
        if (result.isRejected()) {
            return result;
        }

        TMhVisitRow row = new TMhVisitRow();
        row.setTicketNo(nextTicketNo(at));
        row.setRoad(road);
        row.setRawScore(rawScore);
        row.setTriageAt(at);
        row.setDelFlag(0);
        row.setStatus(TMhVisitRow.STATUS_PENDING);

        if (result.isNoRule()) {
            // 查不到可用线也落单：级别 0、线代号留空，单上明写"无可用标准"，不许拿前后几天的线凑合
            row.setLevelNum(TMhVisitRow.LEVEL_NO_RULE);
            row.setRuleCode(null);
            row.setRuleEffStart(null);
            row.setRemark(remarkWithPrefix(result.getMessage()));
        } else {
            // 级别与用哪条线、线起用日都随后台回值写死，不接受页面传入
            row.setLevelNum(result.getLevel());
            row.setRuleCode(result.getRuleCode());
            row.setRuleEffStart(result.getRuleEffStart());
            String effStartText = result.getRuleEffStart() == null
                    ? "未登记"
                    : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(result.getRuleEffStart());
            row.setRemark(TriageResult.levelName(result.getLevel()) + "（依据判定线 "
                    + result.getRuleCode() + "，该线起用日 " + effStartText + "）");
        }

        this.mhVisitRowMapper.insert(row);
        result.setVisitRowId(row.getId());
        result.setTicketNo(row.getTicketNo());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageResult reviewTriage(Long id) {
        TMhVisitRow row = this.mhVisitRowMapper.selectById(id);
        if (row == null) {
            return TriageResult.rejected(null, "分诊单不存在：" + id);
        }

        // 只认单上记的线代号；单上没记（无可用标准单）直接按无标准复核，不另找今天的线
        TriageResult result = this.triaRuleService.gradeByCode(
                row.getRuleCode(), row.getRawScore(), row.getTriageAt());

        Date now = new Date();
        row.setReviewedAt(now);
        row.setReviewLevel(result.getLevel());

        if (result.isSuccess() && row.getLevelNum() != null
                && result.getLevel() == row.getLevelNum().intValue()) {
            // 重判与原级别一致：复核封卷
            row.setStatus(TMhVisitRow.STATUS_REVIEWED);
        } else if (result.isSuccess()) {
            // 重判与原级别不一致：按规矩不该发生（旧线只供翻旧账，不应被改），
            // 不覆盖原级别，留在待复核交护理部追查
            row.setStatus(TMhVisitRow.STATUS_PENDING);
        } else {
            // 无可用标准的复查结论，同样不覆盖原单，留在待复核人工兜底
            row.setStatus(TMhVisitRow.STATUS_PENDING);
        }
        this.mhVisitRowMapper.updateById(row);

        result.setVisitRowId(row.getId());
        result.setTicketNo(row.getTicketNo());
        return result;
    }

    @Override
    public int submitBatch(String batchNo, List<TMhVisitRow> rows) {
        // 基线签名保留：整批导入核收与本环节（急诊分诊落单）无关，未启用
        return 0;
    }

    @Override
    public List<TMhVisitRow> listErrors(String batchNo) {
        // 基线签名保留：错误报告导出未启用
        return new ArrayList<TMhVisitRow>();
    }

    /** 无标准备注统一以"无可用标准"起头，避免和服务层原话叠成两句 */
    private String remarkWithPrefix(String message) {
        if (message == null || message.trim().isEmpty()) {
            return NO_RULE_REMARK;
        }
        return message.startsWith(NO_RULE_REMARK) ? message : NO_RULE_REMARK + "：" + message;
    }

    /** 分诊单号：TR + 分诊时点紧凑串 + 雪花号，号内带时间便于对账，全局不撞 */
    private String nextTicketNo(Date at) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(at);
        return "TR" + ts + SnowflakeIdWorker.getUUID();
    }
}
