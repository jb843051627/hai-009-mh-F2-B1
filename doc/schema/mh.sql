-- mh 智慧医疗门诊管理系统 -- schema (hai-009)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_009

CREATE TABLE IF NOT EXISTS t_mh_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '就诊档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡年就诊次数',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增就诊次数',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '就诊次数折到的随访保障层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊档案卡';

CREATE TABLE IF NOT EXISTS t_mh_inpat_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '住院处置单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（入院/检查/治疗/出院封卷）',
  status int DEFAULT NULL COMMENT '单子落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '单上记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='住院处置单';

CREATE TABLE IF NOT EXISTS t_mh_med_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '特殊用药签核单号',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..2（开方提/药剂科核/医务科批）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本道应落印数',
  sign_count int DEFAULT NULL COMMENT '本道已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='特殊用药签核单';

CREATE TABLE IF NOT EXISTS t_mh_patient (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '患者底档代号',
  site_name varchar(128) DEFAULT NULL COMMENT '底档名称',
  site_type varchar(32) DEFAULT NULL COMMENT '底档类别(普通/慢病/特需)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档年就诊次数一档上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已注销',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者底档';

CREATE TABLE IF NOT EXISTS t_mh_reg_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '号源转号事项号',
  due_at datetime DEFAULT NULL COMMENT '放号截止那一天最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前转号的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '号源事由与承接科室记要',
  status int DEFAULT NULL COMMENT '条目情形 0待转 1已转 2转不出去',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源到期转号事项条';

-- 急诊分诊判定线（全院唯一口径）：
-- 一条线一行；三个分界数把得分切成 危重/紧急/普通/非急症 四段，正好压在界值上算上（高）一档；
-- 生效窗口含起用日、不含交棒日（旧线管到交棒前一刻，新线从交棒那一刻起），交棒日为空 = 长期有效；
-- 同一时段并着几条线时 priority 数值大的说话，priority 分不出按 rule_code 排后一条（代码序，年年一致）；
-- status 0 现行才参与新定级，1 已让位只供按代号翻旧账复查，不删；
-- 早期入册的线某一界值允许为空：空着就不设那一档，往下一档接着判，不算故障。
CREATE TABLE IF NOT EXISTS t_mh_tria_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '分诊判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  apply_road varchar(32) DEFAULT NULL COMMENT '适用途径（急诊/门诊/留观等，空=不限途径）',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '危重档界值（含），空=不设危重档',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '紧急档界值（含），空=不设紧急档',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '普通档界值（含），空=不设普通档',
  eff_start datetime DEFAULT NULL COMMENT '启用之日（含）',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)，空=长期有效',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_rule_code (rule_code),
  KEY idx_eff (status, del_flag, eff_start, eff_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='急诊分诊判定线';

-- 急诊分诊单：护士逐项录入症状得分，后台按"分诊发生那一刻"生效的现行判定线定级写死。
-- 单上留原始得分、级别、用了哪条线（代号）及该线的起用日；复查只认单上 rule_code，
-- 调那条线按当时口径重判，不许拿今天的标准改昨天的单。
-- 无可用标准时 level 记 0、rule_code 留空并在 remark 明写"无可用标准"，不允许落级、不许凑前后几天的线。
-- level: 1危重 2紧急 3普通 4非急症 0无可用标准；status: 0待复核 1已复核 2退回重录。
CREATE TABLE IF NOT EXISTS t_mh_visit_row (
  id bigint NOT NULL COMMENT '主键',
  ticket_no varchar(64) DEFAULT NULL COMMENT '分诊单号',
  road varchar(32) DEFAULT NULL COMMENT '分诊途径（急诊/门诊/留观）',
  raw_score decimal(12,2) DEFAULT NULL COMMENT '原始症状得分（护士录入，原样留档）',
  level_num int DEFAULT NULL COMMENT '系统判定级别 1危重 2紧急 3普通 4非急症 0无可用标准',
  rule_code varchar(64) DEFAULT NULL COMMENT '定级所用判定线代号（随后台回值落单，无标准时留空）',
  rule_eff_start datetime DEFAULT NULL COMMENT '所用判定线的起用日（随线快照落单）',
  triage_at datetime DEFAULT NULL COMMENT '分诊发生时点（按这一时刻选生效线，不按今天）',
  reviewed_at datetime DEFAULT NULL COMMENT '最近一次复查时点',
  review_level int DEFAULT NULL COMMENT '复查按单上线代号重判的级别（不改原级别）',
  status int DEFAULT NULL COMMENT '分诊单情形 0待复核 1已复核 2退回重录',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（无可用标准等在此明写）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='急诊分诊单';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 初始档案数据：id=0 在用、id=1 已注销
-- 年就诊次数上限 3/10/30 次：等于上限算高一档。
INSERT IGNORE INTO t_mh_patient (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'PT00', '临江市东湖街慢病随访组（在用）', '慢病', '临江省—临江市—东湖区—东湖街道', 3.00, 10.00, 30.00, 0, 0, 'seed', NOW()),
       (1, 'PT01', '临江市城南普通门诊组（已注销）', '普通', '临江省—临江市—城南区—望湖街道', 3.00, 10.00, 30.00, 1, 0, 'seed', NOW());

-- 急诊分诊判定线两版手册换版样本：
-- TRIAGE-V1 为去年旧手册线，今年换版时标已让位，交棒日即新版起用时刻，供去年旧单按旧线复查；
-- TRIAGE-V2 为今年现行线；TRIAGE-EARLY 为早期入册、二档界值留空的现行线（演示空档下跳）。
INSERT IGNORE INTO t_mh_tria_rule
  (id, rule_code, rule_name, apply_road, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES
  (100, 'TRIAGE-V1', '急诊分诊旧手册线（去年版）', '急诊', 20.00, 40.00, 60.00,
   '2025-01-01 00:00:00', '2026-01-01 00:00:00', 100, 1, 0, 'seed', NOW(), '去年旧手册，已让位，仅供旧单复查'),
  (101, 'TRIAGE-V2', '急诊分诊新手册线（今年现行）', '急诊', 15.00, 30.00, 50.00,
   '2026-01-01 00:00:00', NULL, 100, 0, 0, 'seed', NOW(), '今年现行，交棒时刻起管新单'),
  (102, 'TRIAGE-EARLY', '早期入册线（紧急档未设）', '急诊', 15.00, NULL, 50.00,
   '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '顺位低，默认不参选；二档界值为空，命中时跳过紧急档');

-- =====================================================================
-- 住院医嘱执行闭环（hai-009 增补）。全院口径就这一处，定死如下：
--
-- 1) 执行笔幂等：一次计划执行 = (order_no, dose_seq)，全库唯一。同一份医嘱点两次、
--    交接班两人同时提交、网络重试，撞的都是同一个键，库里永远只有一笔；
--    撞键的一律按"重放"返回已落的那笔，不再落第二笔。
-- 2) 闭环判定单一口径：一张单只适用一种算法，由 order_type 定死，不存在两种都算数。
--    按次数型：有效执行笔数（执行人非空）达到 need_count 才闭环；
--    按时段型：过了 period_end 且期内有有效执行才闭环，期内零有效执行到期也不闭环。
--    判定只走 TMhOrderServiceImpl 一处，页面、定时任务、补登都调同一口径。
-- 3) 转科归属：医嘱随病人走。owner_dept 是唯一认领方，转科那一刻换边并写移交流水，
--    原科室清单即刻摘除，任何时点一张单只挂一头；执行笔上的 dept_no 是发生时的快照，
--    不随转科改，白班夜班对账看快照。
-- 4) 审方驳回后坚持要发：必须走强制放行，override_by/override_at/override_reason
--    三样落单才准发药执行，事后按单查得到谁批的；缺一样都不放行。
-- 5) 往年补录：缺执行人的补录笔不算闭环进度，单进 close_status=2 补录待核，
--    补登执行人后按同一口径重算；自动闭环任务不动补录待核的单。
-- 6) 列表与详情同口径：两处执行条数都出自 t_mh_order_exec 的同一条 COUNT，
--    不经任何 JOIN 放大，加起来的数就是库里的数。
-- 7) 并发与重试：执行落笔与闭环推进同一事务；闭环状态由执行笔派生重算、
--    条件更新兜底，重试重放不改变终态。
CREATE TABLE IF NOT EXISTS t_mh_order (
  id bigint NOT NULL COMMENT '主键',
  order_no varchar(64) NOT NULL COMMENT '医嘱单号',
  patient_no varchar(64) DEFAULT NULL COMMENT '患者底档代号',
  owner_dept varchar(64) DEFAULT NULL COMMENT '当前归属科室（唯一认领方，转科只改这里）',
  order_type int DEFAULT NULL COMMENT '闭环算法 1按次数 2按时段（一张单只适用一种）',
  need_count int DEFAULT NULL COMMENT '应执行次数（按次数型必填）',
  period_start datetime DEFAULT NULL COMMENT '执行时段起（按时段型必填，含）',
  period_end datetime DEFAULT NULL COMMENT '执行时段止（按时段型必填，不含）',
  review_status int DEFAULT NULL COMMENT '审方情形 0待审 1通过 2驳回 3驳回后强制放行',
  review_by varchar(64) DEFAULT NULL COMMENT '审方人',
  review_at datetime DEFAULT NULL COMMENT '审方时点',
  review_comment varchar(500) DEFAULT NULL COMMENT '审方意见',
  override_by varchar(64) DEFAULT NULL COMMENT '强制放行审批人（驳回后坚持要发必落）',
  override_at datetime DEFAULT NULL COMMENT '强制放行时点',
  override_reason varchar(500) DEFAULT NULL COMMENT '强制放行理由',
  dispense_status int DEFAULT NULL COMMENT '发药情形 0未发药 1已发药',
  dispense_by varchar(64) DEFAULT NULL COMMENT '发药人',
  dispensed_at datetime DEFAULT NULL COMMENT '发药时点',
  backfill int DEFAULT '0' COMMENT '往年补录单 0否 1是（补录单审方发药从略置妥）',
  close_status int DEFAULT NULL COMMENT '闭环情形 0未闭环 1已闭环 2补录待核',
  closed_at datetime DEFAULT NULL COMMENT '闭环达成时点',
  status int DEFAULT NULL COMMENT '单情形 0在办 1已停嘱',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_owner (status, del_flag, owner_dept)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='住院医嘱单';

CREATE TABLE IF NOT EXISTS t_mh_order_exec (
  id bigint NOT NULL COMMENT '主键',
  order_no varchar(64) NOT NULL COMMENT '所属医嘱单号',
  dose_seq int NOT NULL COMMENT '计划内第几次（与 order_no 组成幂等键）',
  exec_by varchar(64) DEFAULT NULL COMMENT '执行人（正常执行必填；往年补录可空，空则不计闭环）',
  exec_at datetime DEFAULT NULL COMMENT '执行时点',
  dept_no varchar(64) DEFAULT NULL COMMENT '执行时归属科室快照（不随转科改，对账看这里）',
  backfill int DEFAULT '0' COMMENT '该笔为补录 0否 1是',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_dose (order_no, dose_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医嘱执行记录';

CREATE TABLE IF NOT EXISTS t_mh_order_transfer (
  id bigint NOT NULL COMMENT '主键',
  order_no varchar(64) NOT NULL COMMENT '被移交医嘱单号',
  from_dept varchar(64) DEFAULT NULL COMMENT '交出科室',
  to_dept varchar(64) DEFAULT NULL COMMENT '承接科室',
  moved_by varchar(64) DEFAULT NULL COMMENT '移交操作人',
  moved_at datetime DEFAULT NULL COMMENT '移交时点',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医嘱转科移交流水';
