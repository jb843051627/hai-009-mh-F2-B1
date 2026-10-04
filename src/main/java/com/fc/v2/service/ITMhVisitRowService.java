package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.model.custom.TriageResult;

/**
 * TMhVisitRow 业务层处理接口（急诊分诊单）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhVisitRowService {

    /** selectTMhVisitRowById */
    TMhVisitRow selectTMhVisitRowById(Long id);

    /** submitBatch（基线签名保留：整批导入核收未启用） */
    int submitBatch(String batchNo, List<TMhVisitRow> rows);

    /** listErrors（基线签名保留：错误报告导出未启用） */
    List<TMhVisitRow> listErrors(String batchNo);

    /**
     * 分诊落单（新层）：护士只录途径、原始得分、分诊发生时点；
     * 级别、判定线代号、线起用日全由后台定级服务算完写死，页面不许预填。
     * <ul>
     *   <li>输入非法：退回护士重录，不落单；</li>
     *   <li>无可用标准：照样落单，级别记 0、线代号留空，备注明写"无可用标准"；</li>
     *   <li>定级成功：落 1..4 级并快照线代号与起用日。</li>
     * </ul>
     * triageAt 为空按当前时刻（但补录旧单必须传分诊当时时点，定级只认它不认今天）。
     */
    TriageResult recordTriage(String road, BigDecimal rawScore, Date triageAt);

    /**
     * 复查（新层）：只认单上记的线代号，调那条线（含已让位）按单上分诊时点重判，
     * 重判结果只写 review_level/reviewed_at，绝不改原 level/rule_code。
     * 不允许拿今天的标准改昨天的单。
     */
    TriageResult reviewTriage(Long id);

    /** 分页/列表查询（后台管理与护理部复核共用一个口径） */
    List<TMhVisitRow> selectTMhVisitRowList(Wrapper<TMhVisitRow> queryWrapper);
}
