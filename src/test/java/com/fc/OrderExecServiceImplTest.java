package com.fc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.auto.TMhOrderTransfer;
import com.fc.v2.model.custom.OrderResult;
import com.fc.v2.service.impl.TMhOrderServiceImpl;

/**
 * 住院医嘱执行闭环口径测试：护士长报上来的七类问题，逐条锁死。
 */
public class OrderExecServiceImplTest extends AbstractH2Test {

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- 全流程走查：录单→审方→发药→执行→闭环，多走一步都露出来 ----------

    @Test
    void walkthrough_record_review_dispense_execute_close() {
        OrderResult created = orderService.createOrder(null, "PT00", "内科",
                TMhOrder.TYPE_COUNT, 2, null, null, false, "doc-1");
        assertTrue(created.isSuccess());
        String no = created.getOrderNo();
        assertNotNull(no);

        // 未审方就执行 / 未审方就发药：都退回
        assertTrue(orderService.execute(no, 1, "nurse-1", null).isRejected());
        assertTrue(orderService.dispense(no, "phar-1").isRejected());

        // 审方通过，审方人落单
        assertTrue(orderService.review(no, true, "phar-1", "配伍无碍").isSuccess());
        TMhOrder reviewed = orderService.selectByOrderNo(no);
        assertEquals("phar-1", reviewed.getReviewBy());
        assertNotNull(reviewed.getReviewAt());

        // 审过了但没发药就执行：退回
        assertTrue(orderService.execute(no, 1, "nurse-1", null).isRejected());

        assertTrue(orderService.dispense(no, "phar-1").isSuccess());

        OrderResult e1 = orderService.execute(no, 1, "nurse-1", null);
        assertTrue(e1.isSuccess());
        assertEquals(TMhOrder.CLOSE_OPEN, e1.getCloseStatus().intValue());

        OrderResult e2 = orderService.execute(no, 2, "nurse-1", null);
        assertTrue(e2.isSuccess());
        assertEquals(TMhOrder.CLOSE_DONE, e2.getCloseStatus().intValue());
        assertEquals(2L, e2.getExecCount().longValue());

        TMhOrder order = orderService.selectByOrderNo(no);
        assertEquals(TMhOrder.CLOSE_DONE, order.getCloseStatus().intValue());
        assertNotNull(order.getClosedAt());
        assertEquals(2L, orderService.countExec(no));
    }

    // ---------- 缺陷一：同一份医嘱点两次 / 交接班两人同时提交，库里只许一笔 ----------

    @Test
    void double_click_same_dose_lands_single_row() {
        OrderResult first = orderService.execute("ORD-C3", 1, "nurse-1", null);
        assertTrue(first.isSuccess());
        assertFalse(first.isReplayed());

        // 双击/重试：同一计划次数再提交，按重放收场，不落第二笔
        OrderResult again = orderService.execute("ORD-C3", 1, "nurse-1", null);
        assertTrue(again.isSuccess());
        assertTrue(again.isReplayed());
        assertEquals(first.getExecId(), again.getExecId());

        assertEquals(1L, orderService.countExec("ORD-C3"));
        assertEquals(1, orderService.listExec("ORD-C3").size());
    }

    @Test
    void shift_handover_concurrent_submit_lands_single_row() throws Exception {
        SqlSession[] holder = new SqlSession[1];
        TMhOrderServiceImpl nightSvc = newOrderServiceOnNewSession(holder);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<OrderResult> results = Collections.synchronizedList(new ArrayList<OrderResult>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());

            // 夜班护士那条会话
            Thread night = new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        go.await();
                        results.add(nightSvc.execute("ORD-C3", 2, "nurse-night", null));
                    } catch (Exception e) {
                        errors.add(e);
                    }
                }
            });
            night.start();
            ready.countDown();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            // 白班护士这条会话，同一针同一时刻提交
            results.add(orderService.execute("ORD-C3", 2, "nurse-day", null));
            night.join(5000);

            assertTrue(errors.isEmpty(), "并发提交不许抛错给护士：" + errors);
            assertEquals(2, results.size());
            // 两笔提交只落一笔：一个算落笔、一个算重放，谁也不报错
            assertEquals(1L, orderService.countExec("ORD-C3"));
            assertTrue(results.stream().allMatch(OrderResult::isSuccess));
            assertEquals(1L, results.stream().filter(OrderResult::isReplayed).count());
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }

    // ---------- 缺陷二：一张单只认一种闭环算法，不存在两种都算数 ----------

    @Test
    void count_order_closes_only_by_count_never_by_period() {
        // 按次数型：次数不够，日历翻到明年、定时任务跑烂也不闭环
        orderService.execute("ORD-C2", 1, "nurse-1", null);
        orderService.runCloseJob(t("2027-01-01 00:00:00"));
        assertEquals(TMhOrder.CLOSE_OPEN, orderService.selectByOrderNo("ORD-C2").getCloseStatus().intValue());
        // 次数凑齐那一刻才闭环
        OrderResult e2 = orderService.execute("ORD-C2", 2, "nurse-1", null);
        assertEquals(TMhOrder.CLOSE_DONE, e2.getCloseStatus().intValue());
    }

    @Test
    void period_order_closes_only_by_period_never_by_count() {
        // 按时段型：期内执行再多次，没到期也不闭环（次数算法说了不算）
        orderService.execute("ORD-P-FUT", 1, "nurse-1", null);
        orderService.execute("ORD-P-FUT", 2, "nurse-1", null);
        orderService.execute("ORD-P-FUT", 3, "nurse-1", null);
        assertEquals(TMhOrder.CLOSE_OPEN, orderService.selectByOrderNo("ORD-P-FUT").getCloseStatus().intValue());
        // 到期后定时任务按同一口径收闭环（ORD-P-JOB 同期到期一并收）
        assertEquals(2, orderService.runCloseJob(t("2100-01-01 00:00:00")));
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-P-FUT").getCloseStatus().intValue());
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-P-JOB").getCloseStatus().intValue());
    }

    @Test
    void close_job_only_collects_due_period_orders_with_valid_exec() {
        // 到期且期内有有效执行 → 收；到期但期内零有效执行 → 不收；未到期 → 不碰
        assertEquals(1, orderService.runCloseJob(t("2026-10-01 00:00:00")));
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-P-JOB").getCloseStatus().intValue());
        assertEquals(TMhOrder.CLOSE_OPEN, orderService.selectByOrderNo("ORD-P-EMPTY").getCloseStatus().intValue());
        assertEquals(TMhOrder.CLOSE_OPEN, orderService.selectByOrderNo("ORD-P-FUT").getCloseStatus().intValue());
        // 任务重跑幂等：已闭环的不再动
        assertEquals(0, orderService.runCloseJob(t("2026-10-01 00:00:00")));
    }

    // ---------- 缺陷三：转科后只挂一头，移交流水留痕 ----------

    @Test
    void transfer_moves_ownership_to_single_side_with_trail() {
        assertTrue(orderService.listByDept("内科").stream().anyMatch(o -> "ORD-XFER".equals(o.getOrderNo())));
        assertFalse(orderService.listByDept("外科").stream().anyMatch(o -> "ORD-XFER".equals(o.getOrderNo())));

        OrderResult moved = orderService.transfer("ORD-XFER", "外科", "headnurse-1");
        assertTrue(moved.isSuccess());
        assertEquals("外科", moved.getOwnerDept());

        // 转后只挂一头：内科清单即刻摘除，外科认领，不存在两边都挂
        assertFalse(orderService.listByDept("内科").stream().anyMatch(o -> "ORD-XFER".equals(o.getOrderNo())));
        assertTrue(orderService.listByDept("外科").stream().anyMatch(o -> "ORD-XFER".equals(o.getOrderNo())));

        // 移交流水留痕：谁移交、从哪头到哪头、什么时候，不用打电话追
        List<TMhOrderTransfer> flows = orderService.listTransfers("ORD-XFER");
        assertEquals(1, flows.size());
        assertEquals("内科", flows.get(0).getFromDept());
        assertEquals("外科", flows.get(0).getToDept());
        assertEquals("headnurse-1", flows.get(0).getMovedBy());
        assertNotNull(flows.get(0).getMovedAt());

        // 重复移交同一科室：重放，不再写流水
        OrderResult again = orderService.transfer("ORD-XFER", "外科", "headnurse-2");
        assertTrue(again.isReplayed());
        assertEquals(1, orderService.listTransfers("ORD-XFER").size());
    }

    @Test
    void transfer_keeps_exec_dept_snapshot_for_handover_audit() {
        orderService.execute("ORD-XFER", 1, "nurse-neike", null);
        orderService.transfer("ORD-XFER", "外科", "headnurse-1");
        orderService.execute("ORD-XFER", 2, "nurse-waike", null);

        // 哪头打的就记哪头：第 1 针记内科、第 2 针记外科，交接班对账看快照
        List<TMhOrderExec> rows = orderService.listExec("ORD-XFER");
        assertEquals(2, rows.size());
        assertEquals("内科", rows.get(0).getDeptNo());
        assertEquals("外科", rows.get(1).getDeptNo());
        // 转科不耽误闭环：次数凑齐照样闭环
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-XFER").getCloseStatus().intValue());
    }

    // ---------- 缺陷四：驳回后坚持要发，谁批的必须查得到 ----------

    @Test
    void rejected_order_needs_audited_override_before_execute() {
        // 驳回单直接执行：退回
        assertTrue(orderService.execute("ORD-REJ", 1, "nurse-1", null).isRejected());
        // 强制放行不落审批人 / 不落理由：都退回
        assertTrue(orderService.overrideRelease("ORD-REJ", null, "病情需要").isRejected());
        assertTrue(orderService.overrideRelease("ORD-REJ", "dr-director", null).isRejected());
        assertTrue(orderService.overrideRelease("ORD-REJ", "dr-director", "  ").isRejected());
        // 三样落齐才放行
        assertTrue(orderService.overrideRelease("ORD-REJ", "dr-director", "家属知情同意，病情需要").isSuccess());
        // 放行后照常发药、执行
        assertTrue(orderService.dispense("ORD-REJ", "phar-1").isSuccess());
        assertTrue(orderService.execute("ORD-REJ", 1, "nurse-1", null).isSuccess());
        // 事后按单查得到谁批的、何时、为何
        TMhOrder order = orderService.selectByOrderNo("ORD-REJ");
        assertEquals(TMhOrder.REVIEW_OVERRIDE, order.getReviewStatus().intValue());
        assertEquals("dr-director", order.getOverrideBy());
        assertNotNull(order.getOverrideAt());
        assertEquals("家属知情同意，病情需要", order.getOverrideReason());
    }

    @Test
    void pending_or_rejected_order_blocks_dispense_and_execute() {
        // 待审：不许发药、不许执行
        assertTrue(orderService.dispense("ORD-NEW", "phar-1").isRejected());
        assertTrue(orderService.execute("ORD-NEW", 1, "nurse-1", null).isRejected());
        // 驳回后未放行：同样不许
        assertTrue(orderService.review("ORD-NEW", false, "phar-1", "剂量存疑").isSuccess());
        assertTrue(orderService.execute("ORD-NEW", 1, "nurse-1", null).isRejected());
    }

    @Test
    void undispensed_order_blocks_execute() {
        assertTrue(orderService.execute("ORD-NODISP", 1, "nurse-1", null).isRejected());
        assertTrue(orderService.dispense("ORD-NODISP", "phar-1").isSuccess());
        assertTrue(orderService.execute("ORD-NODISP", 1, "nurse-1", null).isSuccess());
    }

    // ---------- 缺陷五：往年补录缺执行人，不许自动算完成 ----------

    @Test
    void backfill_missing_executor_not_auto_closed() {
        // 往年补录老单：执行人空着也落笔（历史就是如此），但不许自动算完成
        OrderResult e1 = orderService.execute("ORD-BF", 1, null, t("2025-03-01 09:00:00"));
        assertTrue(e1.isSuccess());
        assertEquals(TMhOrder.CLOSE_BACKFILL_PENDING, e1.getCloseStatus().intValue());

        OrderResult e2 = orderService.execute("ORD-BF", 2, null, t("2025-03-02 09:00:00"));
        assertEquals(TMhOrder.CLOSE_BACKFILL_PENDING, e2.getCloseStatus().intValue());
        // 次数表面够了也不闭环：缺执行人的补录笔不算进度
        assertEquals(TMhOrder.CLOSE_BACKFILL_PENDING,
                orderService.selectByOrderNo("ORD-BF").getCloseStatus().intValue());
        // 自动闭环任务也不碰补录待核的单
        orderService.runCloseJob(t("2027-01-01 00:00:00"));
        assertEquals(TMhOrder.CLOSE_BACKFILL_PENDING,
                orderService.selectByOrderNo("ORD-BF").getCloseStatus().intValue());

        // 补登一个还差一个：继续待核
        List<TMhOrderExec> rows = orderService.listExec("ORD-BF");
        assertEquals(2, rows.size());
        OrderResult a1 = orderService.amendExecutor(rows.get(0).getId(), "nurse-old-1", "headnurse-1");
        assertTrue(a1.isSuccess());
        assertEquals(TMhOrder.CLOSE_BACKFILL_PENDING, a1.getCloseStatus().intValue());
        // 补登齐：按同一口径重算，该闭环闭环
        OrderResult a2 = orderService.amendExecutor(rows.get(1).getId(), "nurse-old-2", "headnurse-1");
        assertEquals(TMhOrder.CLOSE_DONE, a2.getCloseStatus().intValue());
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-BF").getCloseStatus().intValue());
    }

    @Test
    void amend_executor_only_for_backfill_rows() {
        orderService.execute("ORD-C3", 1, "nurse-1", null);
        TMhOrderExec row = orderService.listExec("ORD-C3").get(0);
        // 正常笔不许改执行人：谁执行就是谁
        assertTrue(orderService.amendExecutor(row.getId(), "someone-else", "headnurse-1").isRejected());
        assertEquals("nurse-1", orderService.listExec("ORD-C3").get(0).getExecBy());
    }

    // ---------- 缺陷六：列表和详情同一口径，加起来的数就是库里的数 ----------

    @Test
    void list_and_detail_counts_always_match() {
        orderService.execute("ORD-C3", 1, "nurse-1", null);
        orderService.execute("ORD-C3", 2, "nurse-2", null);
        orderService.execute("ORD-C3", 3, "nurse-1", null);

        // 详情口径
        assertEquals(3L, orderService.countExec("ORD-C3"));
        assertEquals(3, orderService.listExec("ORD-C3").size());

        // 列表口径：同一条 COUNT 的分组版
        Map<String, Long> counts = orderService.countExecMap(Arrays.asList("ORD-C3", "ORD-C2", "ORD-P-JOB"));
        assertEquals(3L, counts.get("ORD-C3").longValue());
        assertEquals(0L, counts.getOrDefault("ORD-C2", Long.valueOf(0L)).longValue());
        assertEquals(1L, counts.get("ORD-P-JOB").longValue());

        // 全部单加起来的数就是库里的数，不多不少
        long totalInDb = orderExecMapper.selectCount(
                new QueryWrapper<TMhOrderExec>().eq("del_flag", 0)).longValue();
        long sum = 0;
        for (String no : Arrays.asList("ORD-C3", "ORD-C2", "ORD-P-FUT", "ORD-P-JOB", "ORD-P-EMPTY",
                "ORD-NEW", "ORD-REJ", "ORD-BF", "ORD-NODISP", "ORD-XFER")) {
            sum += orderService.countExec(no);
        }
        assertEquals(totalInDb, sum);
    }

    // ---------- 缺陷七：并发和重试叠在一起，闭环状态始终由执行笔派生 ----------

    @Test
    void retries_and_replays_never_drift_close_status() {
        orderService.execute("ORD-C2", 1, "nurse-1", null);
        orderService.execute("ORD-C2", 1, "nurse-1", null);   // 重试：重放
        OrderResult done = orderService.execute("ORD-C2", 2, "nurse-1", null);
        assertEquals(TMhOrder.CLOSE_DONE, done.getCloseStatus().intValue());
        orderService.execute("ORD-C2", 2, "nurse-1", null);   // 又重试：重放
        orderService.runCloseJob(t("2027-01-01 00:00:00"));    // 任务重跑：按次数型不归它管
        orderService.runCloseJob(t("2027-01-01 00:00:00"));

        // 折腾完：闭环状态与实际执行笔数严丝合缝
        assertEquals(TMhOrder.CLOSE_DONE, orderService.selectByOrderNo("ORD-C2").getCloseStatus().intValue());
        assertEquals(2L, orderService.countExec("ORD-C2"));
        assertEquals(2, orderService.listExec("ORD-C2").size());
    }

    // ---------- 录单校验：一张单定死一种算法，缺件不收 ----------

    @Test
    void create_order_validates_type_specific_fields() {
        // 按次数型缺次数：退回
        assertTrue(orderService.createOrder(null, "PT00", "内科",
                TMhOrder.TYPE_COUNT, null, null, null, false, "doc-1").isRejected());
        // 按时段型缺时段 / 起止倒挂：退回
        assertTrue(orderService.createOrder(null, "PT00", "内科",
                TMhOrder.TYPE_PERIOD, null, null, null, false, "doc-1").isRejected());
        assertTrue(orderService.createOrder(null, "PT00", "内科", TMhOrder.TYPE_PERIOD, null,
                t("2026-10-02 00:00:00"), t("2026-10-01 00:00:00"), false, "doc-1").isRejected());
        // 缺归属科室：退回（医嘱必须有人认领）
        assertTrue(orderService.createOrder(null, "PT00", null,
                TMhOrder.TYPE_COUNT, 1, null, null, false, "doc-1").isRejected());
        // 单号撞了：退回，不另起一单
        assertTrue(orderService.createOrder("ORD-C3", "PT00", "内科",
                TMhOrder.TYPE_COUNT, 1, null, null, false, "doc-1").isRejected());
    }
}
