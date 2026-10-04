package com.fc.v2.controller.admin;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.model.custom.TriageResult;
import com.fc.v2.service.ITMhVisitRowService;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 急诊分诊 controller：护士录症状得分，后台照分诊当时生效的判定线定级写死。
 *
 * <p>页面只采集分诊途径、原始得分、分诊发生时点；级别、判定线代号、线起用日一律随后台回值落单，
 * 前端不许预填。急诊室落单屏与护理部复核屏共用本控制器的同一套取线口径。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@Controller
@RequestMapping("/MhTriageController")
@Api(value = "急诊分诊")
public class MhTriageController extends BaseController {

    private final String prefix = "admin/mhTriage";

    @Autowired
    private ITMhVisitRowService visitRowService;

    /** 分诊单列表页（护理部复核屏） */
    @GetMapping("/view")
    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    /** 护士分诊录入页 */
    @GetMapping("/add")
    @ApiOperation(value = "分诊录入跳转", notes = "分诊录入跳转")
    public String add(ModelMap model) {
        return prefix + "/add";
    }

    /**
     * 分诊落单：后台定级、写死结果。
     * 返回 500 = 输入非法退回重录（未落单）；返回 200 且 data.noRule=true = 已落单但无可用标准；
     * 返回 200 且 data.success=true = 定级成功，级别/线代号/线起用日以 data 为准。
     */
    @Log(title = "急诊分诊落单", action = "grade")
    @ApiOperation(value = "分诊定级落单", notes = "分诊定级落单")
    @PostMapping("/grade")
    @ResponseBody
    public AjaxResult grade(@RequestParam(value = "road", required = false) String road,
                            @RequestParam(value = "rawScore", required = false) String rawScore,
                            @RequestParam(value = "triageAt", required = false) Date triageAt) {
        java.math.BigDecimal score = parseScore(rawScore);
        if (rawScore != null && rawScore.trim().length() > 0 && score == null) {
            return AjaxResult.error("退回重录：症状得分必须是数字（" + rawScore + "）");
        }
        TriageResult result = visitRowService.recordTriage(
                StringUtils.isEmpty(road) ? null : road.trim(), score, triageAt);
        AjaxResult json;
        if (result.isRejected()) {
            // 非法输入：不落单，护士照原话重录
            json = AjaxResult.error(result.getMessage());
        } else {
            json = AjaxResult.successData(200, result).put("msg",
                    result.isNoRule() ? "已落单：无可用标准" : "定级完成：" + TriageResult.levelName(result.getLevel()));
        }
        return json;
    }

    /**
     * 复查：只认单上记的线代号，按单上分诊时点的口径重判，重判结果写 review_level，不改原级别。
     */
    @Log(title = "急诊分诊复查", action = "review")
    @ApiOperation(value = "分诊单复查", notes = "按单上线代号当时口径重判")
    @PostMapping("/review/{id}")
    @ResponseBody
    public AjaxResult review(@PathVariable("id") Long id) {
        TriageResult result = visitRowService.reviewTriage(id);
        if (result.isRejected()) {
            return AjaxResult.error(result.getMessage());
        }
        return AjaxResult.successData(200, result).put("msg",
                result.isNoRule() ? "复查：无可用标准" : "复查重判：" + TriageResult.levelName(result.getLevel()));
    }

    /** 分诊单明细分页（两块屏同口径） */
    @Log(title = "急诊分诊单查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @PostMapping("/list")
    @ResponseBody
    public ResultTable list(TMhVisitRow query) {
        QueryWrapper<TMhVisitRow> q = new QueryWrapper<TMhVisitRow>();
        q.eq("del_flag", 0);
        q.like(StringUtils.isNotEmpty(query.getTicketNo()), "ticket_no", query.getTicketNo());
        q.eq(StringUtils.isNotEmpty(query.getRoad()), "road", query.getRoad());
        q.eq(StringUtils.isNotNull(query.getLevelNum()), "level_num", query.getLevelNum());
        q.eq(StringUtils.isNotNull(query.getStatus()), "status", query.getStatus());
        q.orderByDesc("triage_at");
        startPage();
        PageInfo<TMhVisitRow> page = new PageInfo<TMhVisitRow>(visitRowService.selectTMhVisitRowList(q));
        return pageTable(page.getList(), page.getTotal());
    }

    /** 分诊得分空着不评或格式不对时交回服务层统一退回；非数字返回 null 供控制层给原话 */
    private java.math.BigDecimal parseScore(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return null;
        }
        try {
            return new java.math.BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
