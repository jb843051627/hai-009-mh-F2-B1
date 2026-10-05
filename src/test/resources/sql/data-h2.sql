-- 固定种子，断言按这些数字写：
-- V1 旧手册（已让位但确有交棒日）：20/40/60，窗口 2025 全年
-- V2 新手册（现行）：15/30/50，2026-01-01 交棒时刻起长期有效
-- EARLY 现行但顺位低，且二档界值为空：15/null/50
-- DEAD  已让位且没有交棒日的脏线：任何时点都不许参与新定级
-- GENERAL 不限途径的通用线：10/25/45，顺位 50
MERGE INTO t_mh_tria_rule
  (id, rule_code, rule_name, apply_road, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, remark)
KEY(id) VALUES
  (1, 'TRIAGE-V1', '旧手册线', '急诊', 20.00, 40.00, 60.00,
   TIMESTAMP '2025-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 100, 1, 0, '旧版已让位'),
  (2, 'TRIAGE-V2', '新手册线', '急诊', 15.00, 30.00, 50.00,
   TIMESTAMP '2026-01-01 00:00:00', NULL, 100, 0, 0, '今年现行'),
  (3, 'TRIAGE-EARLY', '早期线(无紧急档)', '急诊', 15.00, NULL, 50.00,
   TIMESTAMP '2026-01-01 00:00:00', NULL, 10, 0, 0, '二档空'),
  (4, 'TRIAGE-ZDEAD', '让位无交棒日线', '急诊', 5.00, 10.00, 20.00,
   TIMESTAMP '2025-01-01 00:00:00', NULL, 100, 1, 0, '脏停用线'),
  (5, 'TRIAGE-GENERAL', '不限途径通用线', NULL, 10.00, 25.00, 45.00,
   TIMESTAMP '2026-01-01 00:00:00', NULL, 50, 0, 0, '通用');

-- 医嘱闭环固定种子，断言按这些单写：
-- ORD-C3     按次数型需 3 次，审讫已发药，归属内科（幂等/并发/同口径用）
-- ORD-C2     按次数型需 2 次，审讫已发药（按次数闭环用）
-- ORD-P-FUT  按时段型 2026-10-01~2099-12-31（未到期不闭环用；截止日放得足够远，不依赖跑测试的机器时钟）
-- ORD-P-JOB  按时段型 2026-09 已过期且期内已有一笔有效执行（定时任务收闭环用）
-- ORD-P-EMPTY 按时段型 2026-09 已过期但期内零执行（到期也不许闭环用）
-- ORD-NEW    待审（未审方不许发药执行用）
-- ORD-REJ    已驳回（强制放行审计用）
-- ORD-BF     往年补录单，按次数需 2 次，审方发药从略置妥（缺执行人不闭环用）
-- ORD-NODISP 审讫未发药（未发药不许执行用）
-- ORD-XFER   按次数型需 2 次，归属内科（转科移交用）
MERGE INTO t_mh_order
  (id, order_no, patient_no, owner_dept, order_type, need_count, period_start, period_end,
   review_status, review_by, review_at, dispense_status, dispense_by, dispensed_at,
   backfill, close_status, status, del_flag, create_by, create_time, remark)
KEY(id) VALUES
  (1, 'ORD-C3', 'PT00', '内科', 1, 3, NULL, NULL,
   1, 'phar-1', TIMESTAMP '2026-10-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-10-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '按次数型需3次'),
  (2, 'ORD-C2', 'PT00', '内科', 1, 2, NULL, NULL,
   1, 'phar-1', TIMESTAMP '2026-10-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-10-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '按次数型需2次'),
  (3, 'ORD-P-FUT', 'PT00', '内科', 2, NULL, TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2099-12-31 00:00:00',
   1, 'phar-1', TIMESTAMP '2026-10-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-10-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '按时段型未到期'),
  (4, 'ORD-P-JOB', 'PT00', '内科', 2, NULL, TIMESTAMP '2026-09-01 00:00:00', TIMESTAMP '2026-09-30 00:00:00',
   1, 'phar-1', TIMESTAMP '2026-09-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-09-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-09-01 08:00:00', '按时段型已过期期内有执行'),
  (5, 'ORD-P-EMPTY', 'PT00', '内科', 2, NULL, TIMESTAMP '2026-09-01 00:00:00', TIMESTAMP '2026-09-30 00:00:00',
   1, 'phar-1', TIMESTAMP '2026-09-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-09-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-09-01 08:00:00', '按时段型已过期期内零执行'),
  (6, 'ORD-NEW', 'PT00', '内科', 1, 1, NULL, NULL,
   0, NULL, NULL, 0, NULL, NULL,
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '待审'),
  (7, 'ORD-REJ', 'PT00', '内科', 1, 1, NULL, NULL,
   2, 'phar-1', TIMESTAMP '2026-10-01 09:00:00', 0, NULL, NULL,
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '审方已驳回'),
  (8, 'ORD-BF', 'PT00', '内科', 1, 2, NULL, NULL,
   1, NULL, NULL, 1, NULL, NULL,
   1, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '往年补录，审方发药从略'),
  (9, 'ORD-NODISP', 'PT00', '内科', 1, 1, NULL, NULL,
   1, 'phar-1', TIMESTAMP '2026-10-01 08:00:00', 0, NULL, NULL,
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '审讫未发药'),
  (10, 'ORD-XFER', 'PT00', '内科', 1, 2, NULL, NULL,
   1, 'phar-1', TIMESTAMP '2026-10-01 08:00:00', 1, 'phar-1', TIMESTAMP '2026-10-01 08:05:00',
   0, 0, 0, 0, 'seed', TIMESTAMP '2026-10-01 08:00:00', '转科移交用');

MERGE INTO t_mh_order_exec
  (id, order_no, dose_seq, exec_by, exec_at, dept_no, backfill, del_flag, create_by, create_time)
KEY(id) VALUES
  (1, 'ORD-P-JOB', 1, 'nurse-a', TIMESTAMP '2026-09-15 10:00:00', '内科', 0, 0, 'seed', TIMESTAMP '2026-09-15 10:00:00');
