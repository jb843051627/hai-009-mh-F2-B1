-- H2（MODE=MySQL）内存测试库：只建急诊分诊用到的两张表，列与 mh.sql 对齐
DROP TABLE IF EXISTS t_mh_visit_row;
DROP TABLE IF EXISTS t_mh_tria_rule;

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
