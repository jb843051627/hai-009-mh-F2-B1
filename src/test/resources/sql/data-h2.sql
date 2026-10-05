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

-- 医嘱闭环固定种子，断言按这些单子写：
-- 901 次数型 3 剂、执行中、内科：已落两笔有效执行（第 1、2 剂），再顶一剂即闭环
-- 902 次数型 2 剂、执行中、内科：往年补录混进来一笔缺执行人的脏记录（第 1 剂），有效数其实是 0
-- 903 时段型 2 剂、执行中、外科：窗口 2026-01-01（含）~2026-02-01（不含）
-- 904 次数型 1 剂、已驳回：审方打回留痕，等医生坚持放行
MERGE INTO t_mh_order
  (id, order_no, patient_no, order_type, plan_count, window_start, window_end, dept_id, status,
   review_by, review_at, review_comment, dispense_by, dispense_at, del_flag, create_by, create_time, remark)
KEY(id) VALUES
  (901, 'MO-SEED-901', 'PT00', 1, 3, NULL, NULL, 'D-NEI', 2,
   'phar01', TIMESTAMP '2026-03-01 08:00:00', '审方通过', 'phar01', TIMESTAMP '2026-03-01 09:00:00',
   0, 'seed', TIMESTAMP '2026-03-01 07:00:00', '种子：次数型三剂已执行两剂'),
  (902, 'MO-SEED-902', 'PT00', 1, 2, NULL, NULL, 'D-NEI', 2,
   'phar01', TIMESTAMP '2026-03-01 08:00:00', '审方通过', 'phar01', TIMESTAMP '2026-03-01 09:00:00',
   0, 'seed', TIMESTAMP '2026-03-01 07:00:00', '种子：带往年补录脏记录'),
  (903, 'MO-SEED-903', 'PT00', 2, 2, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-02-01 00:00:00',
   'D-WAI', 2,
   'phar01', TIMESTAMP '2025-12-31 08:00:00', '审方通过', 'phar01', TIMESTAMP '2025-12-31 09:00:00',
   0, 'seed', TIMESTAMP '2025-12-31 07:00:00', '种子：时段型两剂'),
  (904, 'MO-SEED-904', 'PT00', 1, 1, NULL, NULL, 'D-NEI', 4,
   'phar01', TIMESTAMP '2026-03-02 08:00:00', '相互作用存疑，打回', NULL, NULL,
   0, 'seed', TIMESTAMP '2026-03-02 07:00:00', '种子：已驳回等放行');

MERGE INTO t_mh_order_exec
  (id, order_id, dose_seq, idem_key, executor, executed_at, dept_id, backfilled, del_flag, create_by, create_time, remark)
KEY(id) VALUES
  (9001, 901, 1, 'IK-901-1', 'nurseA', TIMESTAMP '2026-03-01 10:00:00', 'D-NEI', 0, 0, 'seed', TIMESTAMP '2026-03-01 10:00:00', NULL),
  (9002, 901, 2, 'IK-901-2', 'nurseB', TIMESTAMP '2026-03-01 14:00:00', 'D-NEI', 0, 0, 'seed', TIMESTAMP '2026-03-01 14:00:00', NULL),
  (9003, 902, 1, 'IK-902-1', NULL, TIMESTAMP '2025-06-01 10:00:00', 'D-NEI', 1, 0, 'seed', TIMESTAMP '2025-06-01 10:00:00', '往年补录缺执行人的脏记录');
