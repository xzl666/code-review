CREATE DATABASE IF NOT EXISTS code_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE code_review;

CREATE TABLE IF NOT EXISTS cr_project (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
  project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
  project_type VARCHAR(32) NOT NULL COMMENT '项目类型：FRONTEND/BACKEND',
  repo_url VARCHAR(512) NOT NULL COMMENT 'Gitee 仓库地址',
  project_token VARCHAR(1024) DEFAULT NULL COMMENT '项目单独访问令牌，明文存储',
  use_default_token TINYINT NOT NULL DEFAULT 1 COMMENT '是否使用默认访问令牌',
  default_branch VARCHAR(128) NOT NULL DEFAULT 'master' COMMENT '默认分支',
  owner_name VARCHAR(64) DEFAULT NULL COMMENT '责任人',
  review_days INT NOT NULL DEFAULT 7 COMMENT '默认检视最近 N 天提交',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_project_code (project_code),
  KEY idx_project_type (project_type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

CREATE TABLE IF NOT EXISTS cr_review_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
  rule_kind VARCHAR(32) NOT NULL COMMENT '规则种类：AI/SCRIPT',
  rule_type VARCHAR(32) NOT NULL COMMENT '规则类型',
  severity VARCHAR(32) NOT NULL COMMENT '默认严重等级',
  project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '适用项目类型',
  prompt_template TEXT COMMENT 'AI 提示词模板',
  skill_id BIGINT DEFAULT NULL COMMENT '关联 Skill ID',
  script_id BIGINT DEFAULT NULL COMMENT '关联脚本 ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_rule_kind (rule_kind),
  KEY idx_rule_type (rule_type),
  KEY idx_skill_id (skill_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视规则表';

CREATE TABLE IF NOT EXISTS cr_ai_skill (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  skill_name VARCHAR(128) NOT NULL COMMENT 'Skill 名称',
  skill_code VARCHAR(64) NOT NULL COMMENT 'Skill 编码',
  function_name VARCHAR(128) NOT NULL COMMENT 'Function Calling 函数名',
  function_description VARCHAR(512) DEFAULT NULL COMMENT '函数描述',
  parameters_schema MEDIUMTEXT NOT NULL COMMENT 'JSON Schema 定义',
  version VARCHAR(32) NOT NULL DEFAULT '1.0.0' COMMENT '版本号',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_skill_code (skill_code),
  KEY idx_function_name (function_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Skill 表';

CREATE TABLE IF NOT EXISTS cr_script_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  script_name VARCHAR(128) NOT NULL COMMENT '脚本名称',
  script_code VARCHAR(64) NOT NULL COMMENT '脚本编码',
  script_language VARCHAR(32) NOT NULL COMMENT '脚本语言：SHELL/PYTHON/NODE',
  script_content MEDIUMTEXT NOT NULL COMMENT '脚本内容',
  parameter_template TEXT COMMENT '参数模板',
  timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '超时时间',
  generated_by_ai TINYINT NOT NULL DEFAULT 0 COMMENT '是否由 AI 生成',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_script_code (script_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本规则表';

CREATE TABLE IF NOT EXISTS cr_review_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_no VARCHAR(64) NOT NULL COMMENT '任务编号',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称冗余',
  trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式：MANUAL/SCHEDULE',
  review_branch VARCHAR(128) NOT NULL COMMENT '检视分支',
  review_days INT NOT NULL COMMENT '检视最近 N 天',
  commit_count INT NOT NULL DEFAULT 0 COMMENT '提交数量',
  diff_file_count INT NOT NULL DEFAULT 0 COMMENT 'diff 文件数量',
  issue_count INT NOT NULL DEFAULT 0 COMMENT '问题总数',
  blocker_count INT NOT NULL DEFAULT 0 COMMENT 'blocker 数量',
  critical_count INT NOT NULL DEFAULT 0 COMMENT 'critical 数量',
  major_count INT NOT NULL DEFAULT 0 COMMENT 'major 数量',
  minor_count INT NOT NULL DEFAULT 0 COMMENT 'minor 数量',
  info_count INT NOT NULL DEFAULT 0 COMMENT 'info 数量',
  ai_call_count INT NOT NULL DEFAULT 0 COMMENT 'AI 调用次数',
  status VARCHAR(32) NOT NULL COMMENT '任务状态',
  start_time DATETIME DEFAULT NULL COMMENT '开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '结束时间',
  error_message TEXT COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_task_no (task_no),
  KEY idx_project_id (project_id),
  KEY idx_status (status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视任务表';

CREATE TABLE IF NOT EXISTS cr_review_issue (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  rule_id BIGINT DEFAULT NULL COMMENT '命中的规则 ID',
  skill_id BIGINT DEFAULT NULL COMMENT '关联 Skill ID',
  issue_source VARCHAR(32) NOT NULL COMMENT '来源：AI/SCRIPT',
  severity VARCHAR(32) NOT NULL COMMENT '严重等级',
  issue_type VARCHAR(64) NOT NULL COMMENT '问题类型',
  file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
  start_line INT DEFAULT NULL COMMENT '起始行',
  end_line INT DEFAULT NULL COMMENT '结束行',
  summary VARCHAR(512) NOT NULL COMMENT '问题摘要',
  detail TEXT COMMENT '问题详情',
  suggestion TEXT COMMENT '修改建议',
  code_snippet MEDIUMTEXT COMMENT '相关代码片段',
  raw_response MEDIUMTEXT COMMENT 'AI 或脚本原始响应',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '问题状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_task_id (task_id),
  KEY idx_project_id (project_id),
  KEY idx_rule_id (rule_id),
  KEY idx_severity (severity),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视问题表';

CREATE TABLE IF NOT EXISTS cr_notify_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
  channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型：WEBHOOK',
  webhook_url VARCHAR(1024) NOT NULL COMMENT 'Webhook 地址',
  secret_encrypt VARCHAR(1024) DEFAULT NULL COMMENT 'Webhook 密钥，加密存储',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_channel_type (channel_type),
  KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知配置表';

CREATE TABLE IF NOT EXISTS cr_notify_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
  template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
  channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  template_content TEXT NOT NULL COMMENT '模板内容',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_template_code (template_code),
  KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板表';

CREATE TABLE IF NOT EXISTS cr_system_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key VARCHAR(128) NOT NULL COMMENT '配置 Key',
  config_value TEXT COMMENT '配置值',
  config_desc VARCHAR(512) DEFAULT NULL COMMENT '配置说明',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';

CREATE TABLE IF NOT EXISTS cr_distributed_lock (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  lock_key VARCHAR(128) NOT NULL COMMENT '锁 Key',
  lock_owner VARCHAR(128) NOT NULL COMMENT '锁持有者',
  expire_time DATETIME NOT NULL COMMENT '过期时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lock_key (lock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分布式锁表';

CREATE TABLE IF NOT EXISTS cr_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
  target_type VARCHAR(64) DEFAULT NULL COMMENT '对象类型',
  target_id BIGINT DEFAULT NULL COMMENT '对象 ID',
  request_content MEDIUMTEXT COMMENT '请求内容',
  result_content MEDIUMTEXT COMMENT '结果内容',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_operation_type (operation_type),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
