package com.fc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TMhOrder;
import com.fc.v2.model.auto.TMhOrderExec;
import com.fc.v2.model.auto.TMhOrderTransfer;
import com.fc.v2.model.custom.OrderActionResult;
import com.fc.v2.model.custom.OrderExecResult;
import com.fc.v2.model.custom.OrderSummary;
import com.fc.v2.service.impl.TMhOrderExecServiceImpl;
import com.fc.v2.service.impl.TMhOrderServiceImpl;

/**
 * 医嘱执行闭环口径测试。
 *
 * <p>每个用例对应住院部报过的一笔账：双击两笔、交接班并发两笔、两种闭环算法混算、
 * 转科两边挂、驳回后放行查不到人、补录缺执行人自动算完成、列表详情对不上、
 * 并发重试闭环错位。口径没钉死之前，这些账全是护士长打电话一笔一笔追回来的。</p>
 */
public class MhOrderClosedLoopTest extends AbstractH2Test {

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- 全流程：自己录一条，照常走一遍 ----------

    @Test
    void walk_through_full_loop() {
        // 录一条次数型医嘱，照常走审方、发药、执行，多走一步看闭环
        OrderActionResult created = orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 2,
                null, null, "D-NEI", "doc01", "全流程走一遍");
        assertTrue(created.isSuccess());
        Long id = created.getOrder().getId();
        assertNotNull(created.getOrder().getOrderNo());
        assertEquals(TMhOrder.STATUS_DRAFT, created.getOrder().getStatus().intValue());

        assertTrue(orderService.review(id, "phar01", true, "用法无误").isSuccess());
        assertEquals(TMhOrder.STATUS_REVIEWED, orderService.selectTMhOrderById(id).getStatus().intValue());
        assertEquals("phar01", orderService.selectTMhOrderById(id).getReviewBy());

        assertTrue(orderService.dispense(id, "phar01").isSuccess());
        assertEquals(TMhOrder.STATUS_EXECUTING, orderService.selectTMhOrderById(id).getStatus().intValue());

        OrderExecResult e1 = orderService.execute(id, 1, "nurseA", t("2026-03-03 08:00:00"), null, false);
        assertTrue(e1.isSuccess());
        assertFalse(e1.isOrderClosed());

        OrderExecResult e2 = orderService.execute(id, 2, "nurseB", t("2026-03-03 12:00:00"), null, false);
        assertTrue(e2.isSuccess());
        assertTrue(e2.isOrderClosed());

        TMhOrder closed = orderService.selectTMhOrderById(id);
        assertEquals(TMhOrder.STATUS_CLOSED, closed.getStatus().intValue());
        assertNotNull(closed.getClosedAt());
        assertEquals(2, orderExecService.countEffectiveExec(closed));
    }

    // ---------- 账一：同一份医嘱点两次，库里只许一笔 ----------

    @Test
    void double_click_same_dose_records_once() {
        Long id = createExecutingCountOrder(2);
        OrderExecResult first = orderService.execute(id, 1, "nurseA", null, "IK-DBL-1", false);
        assertTrue(first.isSuccess());

        // 同一次执行点两次（同一个幂等键）：第二下返回已有那笔，库里不多笔
        OrderExecResult second = orderService.execute(id, 1, "nurseA", null, "IK-DBL-1", false);
        assertTrue(second.isDuplicated());
        assertEquals(first.getExec().getId(), second.getExec().getId());
        assertEquals(1, orderExecService.listExecOfOrder(id).size());

        // 连幂等键都没传的双击（服务端按同一剂兜底）：照样不多笔
        OrderExecResult third = orderService.execute(id, 1, "nurseA", null, null, false);
        assertTrue(third.isDuplicated());
        assertEquals(1, orderExecService.listExecOfOrder(id).size());
    }

    @Test
    void idem_key_borrowed_from_other_order_rejected() {
        // 幂等键撞了别家医嘱的键：拒收，不许张冠李戴
        Long first = createExecutingCountOrder(1);
        assertTrue(orderService.execute(first, 1, "nurseA", null, "IK-SHARED", false).isSuccess());
        Long second = createExecutingCountOrder(1);
        OrderExecResult r = orderService.execute(second, 1, "nurseB", null, "IK-SHARED", false);
        assertTrue(r.isRejected());
        assertEquals(0, orderExecService.listExecOfOrder(second).size());
    }

    // ---------- 账二：交接班两人同时提交同一剂，库里只许一笔 ----------

    @Test
    void concurrent_handover_same_dose_records_once() throws Exception {
        final Long id = createExecutingCountOrder(1);
        final int threads = 2;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch fire = new CountDownLatch(1);
        final List<OrderExecResult> results = Collections.synchronizedList(new ArrayList<OrderExecResult>());
        final List<Exception> errors = Collections.synchronizedList(new ArrayList<Exception>());
        List<Thread> pool = new ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            final String nurse = "nurse-shift-" + i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    // 每个线程自己的会话与服务实例：生产上每个请求也是各自的数据库会话
                    try (SqlSession s = factory.openSession(true)) {
                        TMhOrderServiceImpl svc = newOrderService(s);
                        ready.countDown();
                        fire.await();
                        results.add(svc.execute(id, 1, nurse, null, null, false));
                    } catch (Exception e) {
                        errors.add(e);
                    }
                }
            });
            pool.add(t);
            t.start();
        }
        ready.await();
        fire.countDown();
        for (Thread t : pool) {
            t.join();
        }

        assertTrue(errors.isEmpty(), "并发执行不应有异常漏出");
        assertEquals(2, results.size());
        long ok = results.stream().filter(OrderExecResult::isSuccess).count();
        long dup = results.stream().filter(OrderExecResult::isDuplicated).count();
        assertEquals(1, ok, "两人同时提交同一剂：只许一方落笔");
        assertEquals(1, dup, "撞上的那方拿到已落的那笔");
        // 另开会话验收（主会话一级缓存里还躺着旧快照；生产上每个请求本来就是新会话）：
        // 库里只落一笔，闭环状态与执行记录对齐
        try (SqlSession verify = factory.openSession(true)) {
            QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
            q.eq("order_id", id).eq("del_flag", 0);
            assertEquals(1, verify.getMapper(com.fc.v2.mapper.auto.TMhOrderExecMapper.class)
                    .selectCount(q).intValue());
            TMhOrder after = verify.getMapper(com.fc.v2.mapper.auto.TMhOrderMapper.class).selectById(id);
            assertEquals(TMhOrder.STATUS_CLOSED, after.getStatus().intValue());
            assertNotNull(after.getClosedAt());
        }
    }

    // ---------- 账三：一张单只认一种闭环算法，两种算法不许混 ----------

    @Test
    void count_type_closes_by_count_regardless_of_time() {
        // 种子 901：次数型 3 剂已落 2 笔。次数型不看时间，任意时刻的有效执行都计入
        TMhOrder o901 = orderService.selectTMhOrderById(901L);
        assertEquals(Integer.valueOf(TMhOrder.TYPE_COUNT), o901.getOrderType());
        assertEquals(2, orderExecService.countEffectiveExec(o901));
        assertFalse(orderService.isClosed(o901));

        OrderExecResult r = orderService.execute(901L, 3, "nurseC", t("2026-03-05 08:00:00"), null, false);
        assertTrue(r.isSuccess());
        assertTrue(r.isOrderClosed());
        assertEquals(TMhOrder.STATUS_CLOSED, orderService.selectTMhOrderById(901L).getStatus().intValue());
    }

    @Test
    void window_type_never_closed_by_count_algorithm() {
        // 种子 903：时段型 2 剂，窗口 2026-01-01（含）~2026-02-01（不含）。
        // 两剂都落在窗口外：总笔数 2 已够 plan——若按次数型算法就"完成"了，
        // 但这单是时段型，只数窗口内，有效 0，永远不许闭环。同一单两种算法不混。
        OrderExecResult late1 = orderService.execute(903L, 1, "nurseWai", t("2026-03-01 09:00:00"), null, false);
        assertTrue(late1.isSuccess());
        assertTrue(late1.getExec().getRemark().contains("窗口外执行"));

        OrderExecResult late2 = orderService.execute(903L, 2, "nurseWai", t("2026-03-02 09:00:00"), null, false);
        assertTrue(late2.isSuccess());
        assertFalse(late2.isOrderClosed());

        TMhOrder o903 = orderService.selectTMhOrderById(903L);
        assertEquals(2, orderExecService.listExecOfOrder(903L).size());
        assertEquals(0, orderExecService.countEffectiveExec(o903));
        assertFalse(orderService.isClosed(o903));
        assertEquals(TMhOrder.STATUS_EXECUTING, o903.getStatus().intValue());
    }

    @Test
    void window_type_closes_only_with_in_window_doses() {
        Long id = createExecutingWindowOrder(2, "2026-01-01 00:00:00", "2026-02-01 00:00:00");
        // 窗口含起：起日零点整算窗内
        assertTrue(orderService.execute(id, 1, "n1", t("2026-01-01 00:00:00"), null, false).isSuccess());
        // 窗口内第二剂顶到达标线：闭环
        OrderExecResult r2 = orderService.execute(id, 2, "n2", t("2026-01-31 23:59:59"), null, false);
        assertTrue(r2.isSuccess());
        assertTrue(r2.isOrderClosed());

        // 窗口不含止：止日零点整那笔算窗外，不计入
        Long id2 = createExecutingWindowOrder(1, "2026-01-01 00:00:00", "2026-02-01 00:00:00");
        OrderExecResult atEnd = orderService.execute(id2, 1, "n1", t("2026-02-01 00:00:00"), null, false);
        assertTrue(atEnd.isSuccess());
        assertEquals(0, orderExecService.countEffectiveExec(orderService.selectTMhOrderById(id2)));
        assertFalse(atEnd.isOrderClosed());
    }

    // ---------- 账四：转科归属定死——随病人走，单边认领 ----------

    @Test
    void transfer_moves_pending_to_new_dept_and_keeps_history() {
        // 种子 901：内科执行中，已落两笔（账在内科）。转外科。
        OrderActionResult moved = orderService.transfer(901L, "D-WAI", "nurseLead01");
        assertTrue(moved.isSuccess());
        assertEquals("D-WAI", orderService.selectTMhOrderById(901L).getDeptId());

        // 轨迹落一笔：谁经手、什么时候、从哪到哪，事后对账就认它
        List<TMhOrderTransfer> logs = orderTransferMapper.selectList(null);
        assertEquals(1, logs.size());
        assertEquals("D-NEI", logs.get(0).getFromDept());
        assertEquals("D-WAI", logs.get(0).getToDept());
        assertEquals("nurseLead01", logs.get(0).getMovedBy());
        assertNotNull(logs.get(0).getMovedAt());

        // 已执行的账留在执行时科室，转科不改历史
        assertEquals("D-NEI", orderExecMapper.selectById(9001L).getDeptId());
        assertEquals("D-NEI", orderExecMapper.selectById(9002L).getDeptId());

        // 一单同时只属于一个科室：旧科室待办没有它，新科室待办有它，不存在两边都挂
        assertFalse(orderService.listDeptSummaries("D-NEI").stream()
                .anyMatch(s -> s.getOrder().getId().equals(901L)));
        assertTrue(orderService.listDeptSummaries("D-WAI").stream()
                .anyMatch(s -> s.getOrder().getId().equals(901L)));

        // 未执行的剂次随单走：转科后在新科室执行，账挂新科室，闭环照常
        OrderExecResult exec = orderService.execute(901L, 3, "nurseWai", t("2026-03-01 16:00:00"), null, false);
        assertTrue(exec.isSuccess());
        assertEquals("D-WAI", exec.getExec().getDeptId());
        assertTrue(exec.isOrderClosed());
    }

    @Test
    void transfer_rejects_closed_order_and_self_move() {
        // 原地转不算转
        assertTrue(orderService.transfer(901L, "D-NEI", "nurseLead01").isRejected());
        // 已闭环的单没有待执行，不存在归属问题，不许转
        assertTrue(orderService.execute(901L, 3, "nurseC", null, null, false).isOrderClosed());
        assertTrue(orderService.transfer(901L, "D-WAI", "nurseLead01").isRejected());
        // 经手人必填，事后对账认这个人
        assertTrue(orderService.transfer(902L, "D-WAI", null).isRejected());
    }

    // ---------- 账五：驳回后坚持要发的单，事后必须查得出谁批的 ----------

    @Test
    void override_after_reject_records_who_approved() {
        // 种子 904：药剂师已打回，缘由在账上
        TMhOrder o904 = orderService.selectTMhOrderById(904L);
        assertEquals(TMhOrder.STATUS_REJECTED, o904.getStatus().intValue());
        assertEquals("phar01", o904.getReviewBy());

        // 医生坚持要发：三件套落账，事后查"谁批的"就认 override_by
        OrderActionResult r = orderService.overrideRelease(904L, "docWang", "病人急需，知情同意自担风险");
        assertTrue(r.isSuccess());
        TMhOrder after = orderService.selectTMhOrderById(904L);
        assertEquals(TMhOrder.STATUS_REVIEWED, after.getStatus().intValue());
        assertEquals("docWang", after.getOverrideBy());
        assertNotNull(after.getOverrideAt());
        assertEquals("病人急需，知情同意自担风险", after.getOverrideReason());

        // 放行后照常发药、执行、闭环，链路不断
        assertTrue(orderService.dispense(904L, "phar01").isSuccess());
        OrderExecResult e = orderService.execute(904L, 1, "nurseA", null, null, false);
        assertTrue(e.isSuccess());
        assertTrue(e.isOrderClosed());
    }

    @Test
    void override_requires_operator_reason_and_rejected_state() {
        // 空着人、空着理由都不许放
        assertTrue(orderService.overrideRelease(904L, null, "理由").isRejected());
        assertTrue(orderService.overrideRelease(904L, "docWang", "  ").isRejected());
        // 不是驳回单走不了放行（901 在执行中）
        assertTrue(orderService.overrideRelease(901L, "docWang", "理由").isRejected());
        // 审方驳回必须写缘由，审方必须落人
        Long id = createDraftCountOrder();
        assertTrue(orderService.review(id, "phar01", false, null).isRejected());
        assertTrue(orderService.review(id, "phar01", false, "  ").isRejected());
        assertTrue(orderService.review(id, null, true, null).isRejected());
    }

    // ---------- 账六：往年补录缺执行人，永远不许自动算完成 ----------

    @Test
    void backfill_without_executor_rejected_and_dirty_row_never_counted() {
        // 种子 902：库里躺着一笔往年补录的脏记录（缺执行人）。有效数照样是 0。
        TMhOrder o902 = orderService.selectTMhOrderById(902L);
        assertEquals(1, orderExecService.listExecOfOrder(902L).size());
        assertEquals(0, orderExecService.countEffectiveExec(o902));
        assertFalse(orderService.isClosed(o902));

        // 补录也休想空着执行人落笔
        OrderExecResult noExec = orderService.execute(902L, 2, "  ", t("2025-06-02 09:00:00"), null, true);
        assertTrue(noExec.isRejected());
        assertEquals(1, orderExecService.listExecOfOrder(902L).size());

        // 补录带执行人：照落，补录标记在账上
        OrderExecResult filled = orderService.execute(902L, 2, "nurseOld", t("2025-06-02 09:00:00"), null, true);
        assertTrue(filled.isSuccess());
        assertEquals(Integer.valueOf(1), filled.getExec().getBackfilled());

        // 脏记录占着第 1 剂又不算数：有效 1 < 2，闭不了环——往年老单的真实困境，
        // 系统不许装看不见，必须人工处置
        assertFalse(orderService.isClosed(orderService.selectTMhOrderById(902L)));

        // 人工作废脏记录（留痕不毁账），第 1 剂让位，按规矩重落（一样要执行人），这才闭环
        OrderExecResult voided = orderService.voidExec(9003L, "nurseLead01", "往年补录脏数据缺执行人，作废重录");
        assertTrue(voided.isSuccess());
        TMhOrderExec voidRow = orderExecMapper.selectById(9003L);
        assertEquals(Integer.valueOf(1), voidRow.getDelFlag());
        assertNull(voidRow.getDoseSeq());
        assertNull(voidRow.getIdemKey());
        assertTrue(voidRow.getRemark().contains("原第 1 剂"));
        assertTrue(voidRow.getRemark().contains("nurseLead01"));

        OrderExecResult redo = orderService.execute(902L, 1, "nurseOld", t("2025-06-01 10:00:00"), null, true);
        assertTrue(redo.isSuccess());
        assertTrue(redo.isOrderClosed());
        assertEquals(2, orderExecService.countEffectiveExec(orderService.selectTMhOrderById(902L)));
    }

    @Test
    void void_exec_reopens_closed_order_and_frees_dose() {
        // 落满三剂闭环
        assertTrue(orderService.execute(901L, 3, "nurseA", t("2026-03-01 16:00:00"), null, false).isOrderClosed());
        assertEquals(TMhOrder.STATUS_CLOSED, orderService.selectTMhOrderById(901L).getStatus().intValue());

        // 作废最后一剂：闭环状态跟着有效执行走，当场拽回执行中，闭环时点清空，不许挂假账
        TMhOrderExec dose3 = orderExecService.findDose(901L, 3);
        OrderExecResult voided = orderService.voidExec(dose3.getId(), "nurseLead01", "执行人录错，作废重录");
        assertTrue(voided.isSuccess());
        assertFalse(voided.isOrderClosed());
        TMhOrder reopened = orderService.selectTMhOrderById(901L);
        assertEquals(TMhOrder.STATUS_EXECUTING, reopened.getStatus().intValue());
        assertNull(reopened.getClosedAt());
        assertEquals(2, orderExecService.countEffectiveExec(reopened));

        // 剂次已让位：第 3 剂可按规矩重落，闭环回来
        OrderExecResult redo = orderService.execute(901L, 3, "nurseB", t("2026-03-01 16:30:00"), null, false);
        assertTrue(redo.isSuccess());
        assertTrue(redo.isOrderClosed());

        // 作废也要落人落缘由；已作废的笔不许再作废
        assertTrue(orderService.voidExec(dose3.getId(), "nurseLead01", "重复作废").isRejected());
        assertTrue(orderService.voidExec(redo.getExec().getId(), null, "没人").isRejected());
        assertTrue(orderService.voidExec(redo.getExec().getId(), "nurseLead01", "  ").isRejected());
    }

    // ---------- 账七：列表、详情、库，三处的数必须一致 ----------

    @Test
    void list_detail_and_db_counts_agree() {
        // 内科种子：901 有效 2、902 有效 0（脏记录不计）、904 有效 0
        List<OrderSummary> list = orderService.listDeptSummaries("D-NEI");
        assertEquals(3, list.size());
        long listTotal = 0;
        long detailTotal = 0;
        for (OrderSummary row : list) {
            OrderSummary detail = orderService.detailOf(row.getOrder().getId());
            // 列表与详情同一个汇总口径，两处必然对得上
            assertEquals(row.getEffectiveCount(), detail.getEffectiveCount());
            listTotal += row.getEffectiveCount();
            detailTotal += detail.getEffectiveCount();
            // 与库直查一致：未删除且执行人非空（种子单全是次数型，不限时间）
            QueryWrapper<TMhOrderExec> q = new QueryWrapper<TMhOrderExec>();
            q.eq("order_id", row.getOrder().getId()).eq("del_flag", 0).isNotNull("executor");
            assertEquals(orderExecMapper.selectCount(q).longValue(), row.getEffectiveCount());
        }
        assertEquals(listTotal, detailTotal);
        assertEquals(2, listTotal);
    }

    // ---------- 账八：并发与重试叠在一起，闭环状态与执行记录始终对齐 ----------

    @Test
    void recheck_closure_idempotent_under_retry() {
        // 落最后一剂闭环
        OrderExecResult r = orderService.execute(901L, 3, "nurseA", t("2026-03-01 16:00:00"), "IK-901-3", false);
        assertTrue(r.isSuccess());
        assertTrue(r.isOrderClosed());
        Date closedAt = orderService.selectTMhOrderById(901L).getClosedAt();
        assertNotNull(closedAt);

        // 重试/对账反复触发重算：状态不动、闭环时点不动、不加笔
        for (int i = 0; i < 5; i++) {
            orderService.recheckClosure(901L);
        }
        TMhOrder after = orderService.selectTMhOrderById(901L);
        assertEquals(TMhOrder.STATUS_CLOSED, after.getStatus().intValue());
        assertEquals(closedAt, after.getClosedAt());
        assertEquals(3, orderExecService.listExecOfOrder(901L).size());

        // 同一剂网络重试：幂等命中已有那笔，库里不多笔
        OrderExecResult retry = orderService.execute(901L, 3, "nurseA", t("2026-03-01 16:00:00"), "IK-901-3", false);
        assertTrue(retry.isDuplicated());
        assertEquals(3, orderExecService.listExecOfOrder(901L).size());

        // 已闭环后计划外执行：拒收，闭环状态谁也顶不动
        assertTrue(orderService.execute(901L, 4, "nurseA", null, null, false).isRejected());
        assertEquals(TMhOrder.STATUS_CLOSED, orderService.selectTMhOrderById(901L).getStatus().intValue());
    }

    // ---------- 状态机：走错步一律拒收 ----------

    @Test
    void illegal_transitions_rejected() {
        Long id = createDraftCountOrder();
        // 未审方就发药、就执行
        assertTrue(orderService.dispense(id, "phar01").isRejected());
        assertTrue(orderService.execute(id, 1, "n1", null, null, false).isRejected());
        // 审方驳回后不能发药
        assertTrue(orderService.review(id, "phar01", false, "剂量存疑").isSuccess());
        assertTrue(orderService.dispense(id, "phar01").isRejected());
        // 重复审方：并发下已被他人审过的，CAS 撞墙回明话
        assertTrue(orderService.review(id, "phar02", true, null).isRejected());

        // 已闭环的单：再审、再发药全拒
        assertTrue(orderService.execute(901L, 3, "nurseC", null, null, false).isOrderClosed());
        assertTrue(orderService.review(901L, "phar01", true, null).isRejected());
        assertTrue(orderService.dispense(901L, "phar01").isRejected());
    }

    // ---------- 录入：型别与归属落单定死 ----------

    @Test
    void create_order_validates_shape() {
        // 型别只认 1次数型/2时段型
        assertTrue(orderService.createOrder("PT00", 9, 2, null, null, "D-NEI", "doc01", null).isRejected());
        // 计划剂次必须大于 0
        assertTrue(orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 0, null, null, "D-NEI", "doc01", null).isRejected());
        // 时段型必须给全窗口且起早于止
        assertTrue(orderService.createOrder("PT00", TMhOrder.TYPE_WINDOW, 2, null, null, "D-NEI", "doc01", null).isRejected());
        assertTrue(orderService.createOrder("PT00", TMhOrder.TYPE_WINDOW, 2,
                t("2026-02-01 00:00:00"), t("2026-01-01 00:00:00"), "D-NEI", "doc01", null).isRejected());
        // 归属科室、录入人、患者底档一个不能少
        assertTrue(orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 2, null, null, null, "doc01", null).isRejected());
        assertTrue(orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 2, null, null, "D-NEI", null, null).isRejected());
        assertTrue(orderService.createOrder(null, TMhOrder.TYPE_COUNT, 2, null, null, "D-NEI", "doc01", null).isRejected());
        // 次数型传了窗口也一律置空，不留脏值误导闭环判定
        OrderActionResult ok = orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 2,
                t("2026-01-01 00:00:00"), t("2026-02-01 00:00:00"), "D-NEI", "doc01", null);
        assertTrue(ok.isSuccess());
        assertNull(ok.getOrder().getWindowStart());
        assertNull(ok.getOrder().getWindowEnd());
    }

    // ---------- helpers ----------

    private Long createDraftCountOrder() {
        OrderActionResult r = orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, 2,
                null, null, "D-NEI", "doc01", null);
        assertTrue(r.isSuccess());
        return r.getOrder().getId();
    }

    private Long createExecutingCountOrder(int planCount) {
        OrderActionResult created = orderService.createOrder("PT00", TMhOrder.TYPE_COUNT, planCount,
                null, null, "D-NEI", "doc01", null);
        assertTrue(created.isSuccess());
        Long id = created.getOrder().getId();
        assertTrue(orderService.review(id, "phar01", true, null).isSuccess());
        assertTrue(orderService.dispense(id, "phar01").isSuccess());
        return id;
    }

    private Long createExecutingWindowOrder(int planCount, String windowStart, String windowEnd) {
        OrderActionResult created = orderService.createOrder("PT00", TMhOrder.TYPE_WINDOW, planCount,
                t(windowStart), t(windowEnd), "D-WAI", "doc01", null);
        assertTrue(created.isSuccess());
        Long id = created.getOrder().getId();
        assertTrue(orderService.review(id, "phar01", true, null).isSuccess());
        assertTrue(orderService.dispense(id, "phar01").isSuccess());
        return id;
    }

    /** 为并发用例在指定会话上装配一套服务（每线程各用各的会话，与生产一致） */
    private TMhOrderServiceImpl newOrderService(SqlSession s) throws Exception {
        TMhOrderExecServiceImpl execSvc = new TMhOrderExecServiceImpl();
        inject(execSvc, "mhOrderExecMapper",
                s.getMapper(com.fc.v2.mapper.auto.TMhOrderExecMapper.class));
        TMhOrderServiceImpl svc = new TMhOrderServiceImpl();
        inject(svc, "mhOrderMapper", s.getMapper(com.fc.v2.mapper.auto.TMhOrderMapper.class));
        inject(svc, "mhOrderTransferMapper", s.getMapper(com.fc.v2.mapper.auto.TMhOrderTransferMapper.class));
        inject(svc, "orderExecService", execSvc);
        return svc;
    }
}
