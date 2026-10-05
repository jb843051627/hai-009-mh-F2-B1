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

-- 医嘱执行闭环（全院唯一口径，护士长对账就按这几条）：
-- 一、一张单只认一种闭环算法，order_type 落单时定死、终身不改：
--     1 次数型——有效执行笔数达到 plan_count 即闭环，不看执行时间；
--     2 时段型——只有落在 [window_start, window_end) 窗口内的有效执行才计数，达标即闭环，
--     窗口外的执行照收留痕、备注明写，但不计入闭环。不存在"两种算法都算数"的单。
-- 二、有效执行 = 未删除且执行人非空。缺执行人的笔（往年补录的脏数据也算）永远不计入闭环；
--     执行入口（含补录）执行人必填，空着直接拒收、不落笔。闭环没有第二个"自动算完成"的入口，
--     只能由有效执行逐笔顶到 plan_count。
-- 三、同一剂（order_id, dose_seq）全库至多一笔有效记录，同一幂等键（idem_key）全库至多一笔：
--     同一份医嘱点两次、交接班两人同时提交同一剂、网络重试，撞上的那方拿到的是已落的那笔，
--     库里不会多笔。作废一笔执行不毁账：del_flag 置 1、dose_seq 与 idem_key 置空让位给重录，
--     原值连同经手人、缘由记进备注，行留着供复查。
-- 四、归属只有一个字段 dept_id（当前归属科室），执行责任随病人走：
--     转科 = 原子改 dept_id + 写一笔 t_mh_order_transfer 轨迹，未执行的剂次随单归新科室，
--     已执行的账留在执行时科室（t_mh_order_exec.dept_id 是落笔当时的快照，转科不改历史）。
--     待办列表只按 dept_id 查，一单同时只属于一个科室，不存在"两边都挂着"。
-- 五、审方驳回后医生坚持要发的，只能走"坚持放行"：override_by/override_at/override_reason
--     三件套必填落账，事后查"谁批的"就查这三个字段，查不到就是没走过这一步。
-- 六、状态机 0待审方 1待发药 2执行中 3已闭环 4已驳回，所有推进都是条件更新（CAS），
--     并发与重试叠在一起也只有一方改得动；闭环时点 closed_at 随 2→3 那一下写死。
--     闭环只由有效执行推导，双向收口：有效执行被作废跌下达标线，已闭环的单当场 3→2 重开、
--     closed_at 清空，不许挂假账。
CREATE TABLE IF NOT EXISTS t_mh_order (
  id bigint NOT NULL COMMENT '主键',
  order_no varchar(64) DEFAULT NULL COMMENT '医嘱单号',
  patient_no varchar(64) DEFAULT NULL COMMENT '患者底档代号',
  order_type int DEFAULT NULL COMMENT '闭环算法型别 1次数型 2时段型（落单定死，终身不改）',
  plan_count int DEFAULT NULL COMMENT '计划剂次数（闭环达标线，>0）',
  window_start datetime DEFAULT NULL COMMENT '时段型窗口起（含）；次数型留空',
  window_end datetime DEFAULT NULL COMMENT '时段型窗口止（不含）；次数型留空',
  dept_id varchar(64) DEFAULT NULL COMMENT '当前归属科室代号（唯一归属，转科即改）',
  status int DEFAULT NULL COMMENT '0待审方 1待发药 2执行中 3已闭环 4已驳回',
  review_by varchar(64) DEFAULT NULL COMMENT '审方药剂师',
  review_at datetime DEFAULT NULL COMMENT '审方时点',
  review_comment varchar(500) DEFAULT NULL COMMENT '审方意见（驳回必写缘由）',
  override_by varchar(64) DEFAULT NULL COMMENT '驳回后坚持放行的医生（事后查谁批的认这个字段）',
  override_at datetime DEFAULT NULL COMMENT '坚持放行时点',
  override_reason varchar(500) DEFAULT NULL COMMENT '坚持放行理由（必填，不许空着放）',
  dispense_by varchar(64) DEFAULT NULL COMMENT '发药人',
  dispense_at datetime DEFAULT NULL COMMENT '发药时点',
  closed_at datetime DEFAULT NULL COMMENT '闭环时点（最后一笔有效执行顶到达标线那一下）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_dept_status (dept_id, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医嘱单';

CREATE TABLE IF NOT EXISTS t_mh_order_exec (
  id bigint NOT NULL COMMENT '主键',
  order_id bigint NOT NULL COMMENT '医嘱单主键',
  dose_seq int DEFAULT NULL COMMENT '剂次（1..plan_count，同一医嘱下唯一）',
  idem_key varchar(64) DEFAULT NULL COMMENT '幂等键（同一次执行意图同一个键，重试/双击不重复落笔）',
  executor varchar(64) DEFAULT NULL COMMENT '执行人（闭环硬条件：空着不计入，入口拒收空执行人）',
  executed_at datetime DEFAULT NULL COMMENT '执行时点（时段型按它判在不在窗口内）',
  dept_id varchar(64) DEFAULT NULL COMMENT '落笔当时归属科室（账留当时科室，转科不改历史）',
  backfilled int DEFAULT '0' COMMENT '0正常执行 1往年补录（补录同样要执行人）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（窗口外执行不计入闭环等在此明写）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_dose (order_id, dose_seq),
  UNIQUE KEY uk_idem_key (idem_key),
  KEY idx_order (order_id, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医嘱执行记录';

CREATE TABLE IF NOT EXISTS t_mh_order_transfer (
  id bigint NOT NULL COMMENT '主键',
  order_id bigint NOT NULL COMMENT '医嘱单主键',
  from_dept varchar(64) DEFAULT NULL COMMENT '转出科室代号',
  to_dept varchar(64) DEFAULT NULL COMMENT '接收科室代号',
  moved_by varchar(64) DEFAULT NULL COMMENT '转科经手人',
  moved_at datetime DEFAULT NULL COMMENT '转科时点',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医嘱转科轨迹';
