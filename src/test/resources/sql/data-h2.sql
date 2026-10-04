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
