package com.fc.v2.controller.admin;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.custom.OrderResult;
import com.fc.v2.service.ITMhOrderService;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 住院医嘱执行闭环 controller：录单 → 审方 →（驳回后坚持要发走强制放行）→ 发药 → 执行 → 闭环，
 * 转科随时可发生。闭环判定与执行条数全院只有服务层一处口径，两块屏看到的数必然对得上。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Controller
@RequestMapping("/MhOrderController")
@Api(value = "住院医嘱执行闭环")
public class MhOrderController extends BaseController {

    private final String prefix = "admin/mhOrder";

    @Autowired
    private ITMhOrderService orderService;

    /** 医嘱单列表页（科室屏/复核屏共用） */
    @GetMapping("/view")
    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    /** 医嘱录入页 */
    @GetMapping("/add")
    @ApiOperation(value = "医嘱录入跳转", notes = "医嘱录入跳转")
    public String add(ModelMap model) {
        return prefix + "/add";
    }

    /** 录医嘱：闭环算法一张单定死一种；补录单审方发药从略，执行人靠补登 */
    @Log(title = "医嘱录入", action = "create")
    @ApiOperation(value = "医嘱录入", notes = "医嘱录入")
    @PostMapping("/create")
    @ResponseBody
    public AjaxResult create(@RequestParam(value = "orderNo", required = false) String orderNo,
                             @RequestParam(value = "patientNo", required = false) String patientNo,
                             @RequestParam(value = "ownerDept", required = false) String ownerDept,
                             @RequestParam(value = "orderType", required = false) Integer orderType,
                             @RequestParam(value = "needCount", required = false) Integer needCount,
                             @RequestParam(value = "periodStart", required = false) Date periodStart,
                             @RequestParam(value = "periodEnd", required = false) Date periodEnd,
                             @RequestParam(value = "backfill", required = false, defaultValue = "false") boolean backfill,
                             @RequestParam(value = "operator", required = false) String operator) {
        OrderResult result = orderService.createOrder(orderNo, patientNo, ownerDept, orderType,
                needCount, periodStart, periodEnd, backfill, operator);
        return toOrderJson(result);
    }

    /** 审方：pass=true 通过 / false 驳回，审方人与意见落单 */
    @Log(title = "医嘱审方", action = "review")
    @ApiOperation(value = "医嘱审方", notes = "通过或驳回，留审方人")
    @PostMapping("/review")
    @ResponseBody
    public AjaxResult review(@RequestParam("orderNo") String orderNo,
                             @RequestParam("pass") boolean pass,
                             @RequestParam(value = "auditor", required = false) String auditor,
                             @RequestParam(value = "comment", required = false) String comment) {
        return toOrderJson(orderService.review(orderNo, pass, auditor, comment));
    }

    /** 驳回后医生坚持要发：强制放行，审批人/理由落单，事后查得到谁批的 */
    @Log(title = "医嘱强制放行", action = "override")
    @ApiOperation(value = "驳回后强制放行", notes = "审批人与理由必落单")
    @PostMapping("/override")
    @ResponseBody
    public AjaxResult overrideRelease(@RequestParam("orderNo") String orderNo,
                                      @RequestParam(value = "operator", required = false) String operator,
                                      @RequestParam(value = "reason", required = false) String reason) {
        return toOrderJson(orderService.overrideRelease(orderNo, operator, reason));
    }

    /** 发药：审方通过或强制放行后才准发 */
    @Log(title = "医嘱发药", action = "dispense")
    @ApiOperation(value = "医嘱发药", notes = "审方过了才准发")
    @PostMapping("/dispense")
    @ResponseBody
    public AjaxResult dispense(@RequestParam("orderNo") String orderNo,
                               @RequestParam(value = "pharmacist", required = false) String pharmacist) {
        return toOrderJson(orderService.dispense(orderNo, pharmacist));
    }

    /**
     * 执行落笔：同一 (orderNo, doseSeq) 点几次、几班并发、重试几轮都只落一笔；
     * 返回 200 且 data.replayed=true 表示这次是按重放收场的，库里没多笔。
     */
    @Log(title = "医嘱执行", action = "execute")
    @ApiOperation(value = "医嘱执行落笔", notes = "幂等：同一计划次数只落一笔")
    @PostMapping("/execute")
    @ResponseBody
    public AjaxResult execute(@RequestParam("orderNo") String orderNo,
                              @RequestParam(value = "doseSeq", required = false) Integer doseSeq,
                              @RequestParam(value = "execBy", required = false) String execBy,
                              @RequestParam(value = "execAt", required = false) Date execAt) {
        return toOrderJson(orderService.execute(orderNo, doseSeq, execBy, execAt));
    }

    /** 转科：医嘱随病人走，归属换边写移交流水，原科室清单即刻摘除 */
    @Log(title = "医嘱转科移交", action = "transfer")
    @ApiOperation(value = "医嘱转科移交", notes = "随病人走，只挂一头")
    @PostMapping("/transfer")
    @ResponseBody
    public AjaxResult transfer(@RequestParam("orderNo") String orderNo,
                               @RequestParam(value = "toDept", required = false) String toDept,
                               @RequestParam(value = "operator", required = false) String operator) {
        return toOrderJson(orderService.transfer(orderNo, toDept, operator));
    }

    /** 补录笔补登执行人：补登后按同一口径重算闭环 */
    @Log(title = "补录执行人补登", action = "amendExecutor")
    @ApiOperation(value = "补录执行人补登", notes = "只许补录笔，补登后重算闭环")
    @PostMapping("/amendExecutor")
    @ResponseBody
    public AjaxResult amendExecutor(@RequestParam("execId") Long execId,
                                    @RequestParam(value = "execBy", required = false) String execBy,
                                    @RequestParam(value = "operator", required = false) String operator) {
        return toOrderJson(orderService.amendExecutor(execId, execBy, operator));
    }

    /** 医嘱单分页：每行挂执行条数，与详情同一条 COUNT，两处必然对得上 */
    @Log(title = "医嘱单查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @PostMapping("/list")
    @ResponseBody
    public ResultTable list(TMhOrder query) {
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("del_flag", 0);
        q.like(StringUtils.isNotEmpty(query.getOrderNo()), "order_no", query.getOrderNo());
        q.eq(StringUtils.isNotEmpty(query.getPatientNo()), "patient_no", query.getPatientNo());
        q.eq(StringUtils.isNotEmpty(query.getOwnerDept()), "owner_dept", query.getOwnerDept());
        q.eq(StringUtils.isNotNull(query.getOrderType()), "order_type", query.getOrderType());
        q.eq(StringUtils.isNotNull(query.getReviewStatus()), "review_status", query.getReviewStatus());
        q.eq(StringUtils.isNotNull(query.getCloseStatus()), "close_status", query.getCloseStatus());
        q.eq(StringUtils.isNotNull(query.getStatus()), "status", query.getStatus());
        q.orderByDesc("id");
        startPage();
        PageInfo<TMhOrder> page = new PageInfo<TMhOrder>(orderService.selectOrderList(q));
        // 列表口径 = 详情口径：同一条 COUNT 的分组版，不经 JOIN 放大
        List<TMhOrder> rows = page.getList();
        java.util.List<String> nos = new java.util.ArrayList<String>();
        for (TMhOrder o : rows) {
            nos.add(o.getOrderNo());
        }
        Map<String, Long> counts = orderService.countExecMap(nos);
        for (TMhOrder o : rows) {
            Long c = counts.get(o.getOrderNo());
            o.setExecCount(c == null ? Long.valueOf(0L) : c);
        }
        return pageTable(rows, page.getTotal());
    }

    /** 某单执行明细：条数与列表页同一口径（COUNT 执行表，不多不少） */
    @Log(title = "医嘱执行明细", action = "execList")
    @ApiOperation(value = "执行明细", notes = "条数与列表页同口径")
    @PostMapping("/execList")
    @ResponseBody
    public ResultTable execList(@RequestParam("orderNo") String orderNo) {
        List<TMhOrderExec> rows = orderService.listExec(orderNo);
        return pageTable(rows, orderService.countExec(orderNo));
    }

    /** 统一出参：退回给 500 原话；办成/重放给 200，重放在 data.replayed 上标明 */
    private AjaxResult toOrderJson(OrderResult result) {
        if (result.isRejected()) {
            return AjaxResult.error(result.getMessage());
        }
        return AjaxResult.successData(200, result).put("msg", result.getMessage());
    }
}
