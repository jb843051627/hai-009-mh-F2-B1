package com.fc.v2.controller.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.custom.OrderActionResult;
import com.fc.v2.model.custom.OrderExecResult;
import com.fc.v2.model.custom.OrderSummary;
import com.fc.v2.service.ITMhOrderService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 医嘱闭环 controller：录入 → 审方 →（驳回后坚持放行）→ 发药 → 执行 → 闭环，外加转科。
 *
 * <p>页面只采集业务输入；状态推进、闭环判定、归属变更一律随后台回值，
 * 前端不许预填结果。操作人一律取当前登录人落账——审方谁审的、放行谁批的、
 * 执行谁做的、转科谁经手的，事后都查得到人。</p>
 *
 * @author fuce
 * @date 2026-10-04
 */
@Controller
@RequestMapping("/MhOrderController")
@Api(value = "医嘱闭环")
public class MhOrderController extends BaseController {

    @Autowired
    private ITMhOrderService mhOrderService;

    /**
     * 医嘱录入：闭环算法型别（1次数型/2时段型）落单定死；
     * 时段型必须给全窗口（含起不含止），次数型窗口一律置空。
     */
    @Log(title = "医嘱录入", action = "create")
    @ApiOperation(value = "医嘱录入", notes = "型别落单定死，归属科室必填")
    @PostMapping("/create")
    @ResponseBody
    public AjaxResult create(@RequestParam("patientNo") String patientNo,
                             @RequestParam("orderType") Integer orderType,
                             @RequestParam("planCount") Integer planCount,
                             @RequestParam(value = "windowStart", required = false) Date windowStart,
                             @RequestParam(value = "windowEnd", required = false) Date windowEnd,
                             @RequestParam("deptId") String deptId,
                             @RequestParam(value = "remark", required = false) String remark) {
        OrderActionResult result = mhOrderService.createOrder(patientNo, orderType, planCount,
                windowStart, windowEnd, deptId, ShiroUtils.getLoginName(), remark);
        return actionJson(result, "医嘱已录入");
    }

    /** 审方：pass=true 通过，pass=false 驳回（驳回缘由必填）；审方人取当前登录人 */
    @Log(title = "医嘱审方", action = "review")
    @ApiOperation(value = "医嘱审方", notes = "通过/驳回，审方人落账")
    @PostMapping("/review/{id}")
    @ResponseBody
    public AjaxResult review(@PathVariable("id") Long id,
                             @RequestParam("pass") boolean pass,
                             @RequestParam(value = "comment", required = false) String comment) {
        OrderActionResult result = mhOrderService.review(id, ShiroUtils.getLoginName(), pass, comment);
        return actionJson(result, pass ? "审方通过" : "审方驳回");
    }

    /**
     * 驳回后坚持放行：操作人取当前登录医生，理由必填。
     * 事后查"谁批的"就查单上 override_by。
     */
    @Log(title = "医嘱坚持放行", action = "override")
    @ApiOperation(value = "驳回后坚持放行", notes = "override三件套落账")
    @PostMapping("/override/{id}")
    @ResponseBody
    public AjaxResult overrideRelease(@PathVariable("id") Long id,
                                      @RequestParam("reason") String reason) {
        OrderActionResult result = mhOrderService.overrideRelease(id, ShiroUtils.getLoginName(), reason);
        return actionJson(result, "坚持放行已落账");
    }

    /** 发药：发药人取当前登录人 */
    @Log(title = "医嘱发药", action = "dispense")
    @ApiOperation(value = "医嘱发药", notes = "审方通过才能发药")
    @PostMapping("/dispense/{id}")
    @ResponseBody
    public AjaxResult dispense(@PathVariable("id") Long id) {
        OrderActionResult result = mhOrderService.dispense(id, ShiroUtils.getLoginName());
        return actionJson(result, "已发药");
    }

    /**
     * 执行一剂：幂等键可传可不传——同一剂天然同一个键，点两次、交接班两人同时提交、
     * 网络重试，库里都只落一笔，撞上的那方拿到已落的那笔。
     * 执行人默认当前登录人（护士长代录时可指定），空着一律拒收，补录也一样。
     */
    @Log(title = "医嘱执行", action = "execute")
    @ApiOperation(value = "医嘱执行落笔", notes = "幂等，执行人必填")
    @PostMapping("/execute/{id}")
    @ResponseBody
    public AjaxResult execute(@PathVariable("id") Long id,
                              @RequestParam("doseSeq") Integer doseSeq,
                              @RequestParam(value = "executor", required = false) String executor,
                              @RequestParam(value = "executedAt", required = false) Date executedAt,
                              @RequestParam(value = "idemKey", required = false) String idemKey,
                              @RequestParam(value = "backfilled", required = false, defaultValue = "false") boolean backfilled) {
        String who = StringUtils.isNotEmpty(executor) ? executor.trim() : ShiroUtils.getLoginName();
        OrderExecResult result = mhOrderService.execute(id, doseSeq, who, executedAt, idemKey, backfilled);
        if (result.isRejected()) {
            return AjaxResult.error(result.getMessage());
        }
        AjaxResult json = AjaxResult.successData(200, result);
        json.put("msg", result.getMessage());
        return json;
    }

    /**
     * 转科：归属随病人走——未执行剂次归接收科室，已执行的账留在执行时科室。
     * 一单同时只属于一个科室，转科后待办只挂接收科室。
     */
    @Log(title = "医嘱转科", action = "transfer")
    @ApiOperation(value = "医嘱转科", notes = "原子改归属+写轨迹")
    @PostMapping("/transfer/{id}")
    @ResponseBody
    public AjaxResult transfer(@PathVariable("id") Long id,
                               @RequestParam("toDept") String toDept) {
        OrderActionResult result = mhOrderService.transfer(id, toDept, ShiroUtils.getLoginName());
        return actionJson(result, "已转科");
    }

    /**
     * 作废一笔执行：留痕不毁账，经手人取当前登录人，缘由必填。
     * 作废可能把已闭环的单拽回执行中——闭环状态永远跟着有效执行走。
     */
    @Log(title = "医嘱执行作废", action = "voidExec")
    @ApiOperation(value = "医嘱执行作废", notes = "留痕不毁账，剂次让位可重录")
    @PostMapping("/voidExec/{execId}")
    @ResponseBody
    public AjaxResult voidExec(@PathVariable("execId") Long execId,
                               @RequestParam("reason") String reason) {
        OrderExecResult result = mhOrderService.voidExec(execId, ShiroUtils.getLoginName(), reason);
        if (result.isRejected()) {
            return AjaxResult.error(result.getMessage());
        }
        return AjaxResult.successData(200, result).put("msg", result.getMessage());
    }

    /** 科室单列表：只按当前归属科室查；每行的有效执行数与详情同一个汇总口径 */
    @Log(title = "医嘱单查询", action = "list")
    @ApiOperation(value = "科室医嘱单列表", notes = "按当前归属科室查")
    @PostMapping("/list")
    @ResponseBody
    public ResultTable list(@RequestParam("deptId") String deptId) {
        QueryWrapper<TMhOrder> q = new QueryWrapper<TMhOrder>();
        q.eq("del_flag", 0);
        q.eq("dept_id", deptId);
        q.orderByDesc("id");
        startPage();
        PageInfo<TMhOrder> page = new PageInfo<TMhOrder>(mhOrderService.selectTMhOrderList(q));
        List<OrderSummary> rows = new ArrayList<OrderSummary>();
        for (TMhOrder o : page.getList()) {
            rows.add(mhOrderService.summarize(o, false));
        }
        return pageTable(rows, page.getTotal());
    }

    /** 单详情：单 + 有效执行数 + 执行明细，与列表同一个汇总口径，两处必然对得上 */
    @Log(title = "医嘱单详情", action = "detail")
    @ApiOperation(value = "医嘱单详情", notes = "与列表同口径")
    @GetMapping("/detail/{id}")
    @ResponseBody
    public AjaxResult detail(@PathVariable("id") Long id) {
        OrderSummary summary = mhOrderService.detailOf(id);
        if (summary == null) {
            return AjaxResult.error("医嘱单不存在：" + id);
        }
        return AjaxResult.successData(200, summary).put("msg", "查询成功");
    }

    /** 动作结果统一回话：拒收按 500 原话退回，成功带单态快照 */
    private AjaxResult actionJson(OrderActionResult result, String okMsg) {
        if (result.isRejected()) {
            return AjaxResult.error(result.getMessage());
        }
        return AjaxResult.successData(200, result).put("msg", okMsg);
    }
}
