-- H2（MODE=MySQL）内存测试库：只建急诊分诊用到的两张表，列与 mh.sql 对齐
DROP TABLE IF EXISTS t_mh_visit_row;
DROP TABLE IF EXISTS t_mh_tria_rule;
DROP TABLE IF EXISTS t_mh_order;
DROP TABLE IF EXISTS t_mh_order_exec;
DROP TABLE IF EXISTS t_mh_order_transfer;

CREATE TABLE t_mh_tria_rule (
  id BIGINT PRIMARY KEY,
  rule_code VARCHAR(64),
  rule_name VARCHAR(128),
  apply_road VARCHAR(32),
  th1_max DECIMAL(12,2),
  th2_max DECIMAL(12,2),
  th3_max DECIMAL(12,2),
  eff_start TIMESTAMP,
  eff_end TIMESTAMP,
  priority INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_visit_row (
  id BIGINT PRIMARY KEY,
  ticket_no VARCHAR(64) UNIQUE,
  road VARCHAR(32),
  raw_score DECIMAL(12,2),
  level_num INT,
  rule_code VARCHAR(64),
  rule_eff_start TIMESTAMP,
  triage_at TIMESTAMP,
  reviewed_at TIMESTAMP,
  review_level INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500)
);

-- 医嘱执行闭环三张表，列与 mh.sql 对齐
CREATE TABLE t_mh_order (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  patient_no VARCHAR(64),
  owner_dept VARCHAR(64),
  order_type INT,
  need_count INT,
  period_start TIMESTAMP,
  period_end TIMESTAMP,
  review_status INT,
  review_by VARCHAR(64),
  review_at TIMESTAMP,
  review_comment VARCHAR(500),
  override_by VARCHAR(64),
  override_at TIMESTAMP,
  override_reason VARCHAR(500),
  dispense_status INT,
  dispense_by VARCHAR(64),
  dispensed_at TIMESTAMP,
  backfill INT DEFAULT 0,
  close_status INT,
  closed_at TIMESTAMP,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  CONSTRAINT uk_order_no UNIQUE (order_no)
);

CREATE TABLE t_mh_order_exec (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  dose_seq INT NOT NULL,
  exec_by VARCHAR(64),
  exec_at TIMESTAMP,
  dept_no VARCHAR(64),
  backfill INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  CONSTRAINT uk_order_dose UNIQUE (order_no, dose_seq)
);

CREATE TABLE t_mh_order_transfer (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  from_dept VARCHAR(64),
  to_dept VARCHAR(64),
  moved_by VARCHAR(64),
  moved_at TIMESTAMP,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500)
);
