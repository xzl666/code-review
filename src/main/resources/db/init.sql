CREATE DATABASE IF NOT EXISTS code_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE code_review;

CREATE TABLE IF NOT EXISTS cr_system_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_name VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  employee_id VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_user_id (user_id), UNIQUE KEY uk_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cr_project_owner (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_project_user (project_id, user_id), KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cr_system_user (user_name,user_id,employee_id) VALUES
('管理员','00000000000000000000000000000001','ADMIN'),
('苏宇','48DE319DD62C759A0C5ACDE0CAE0CF69','80320234'),('李继宏','2A612E9FCDEAEB73BE6691586151355D','80322994'),
('杨岳涛','8AB93426A2AE3DBDF83655B29DD34496','80323345'),('谢习田','DB74EE10A85103C89E1BD8A7D063ED71','80325591'),
('王仁传','C37242491BE2BC25913794EA41A672AE','80326302'),('谢飞','70485B212AA49BE4973273834E57F00D','80371040'),
('徐梓琅','FCD122540D98DF1D1D8D449F3CD132B6','80397201'),('聂源','C4E18C29BE8F340585BB60A6492FA77D','80400072'),
('隆兴','BF86F9E1E09115EE13598D0D80472499','80404244'),('全爱平','6CCC26701288956DEB041F1634BB586E','80404295'),
('李均','7DF54B8EC072468A56F367182002CC28','IT011576'),('钟亮','5B167DDCEA0C83F153DBCA099EEF3A03','80273204'),
('安奎霖','57B2BFF8AF719297CF02E10946A181C5','01239699'),('刘凯','44568C4C354BB15F2A296539771B8368','80323467'),
('赵曦','8E29A94510AA2E97FD35182FC0A69CF0','80324381'),('卢英','AE2E213EB06B6857C5BB48BCBE7E1732','80326840'),
('张佳琪','29D06AF664EE5A0568AEE8447729AB80','80380034'),('周蓉晶','865F403F82FDFEF1B84AE5ABF27D7927','80404363'),
('马姜','F687442E162253F70F8FF0C5B78EAF1E','80404403'),('熊能','55CDE5BCBCF3431B552FD527642347C8','IT011573'),
('王秋林','F61BBB0FC047F04A05338E98057E7597','IT011710'),('何国庆','4A337A0961A5E95AD3EDD6A7CBED3E41','IT011826')
ON DUPLICATE KEY UPDATE user_name=VALUES(user_name),employee_id=VALUES(employee_id),status=1,deleted=0;

CREATE TABLE IF NOT EXISTS cr_project (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
  project_type VARCHAR(32) NOT NULL COMMENT '项目类型：FRONTEND/BACKEND',
  repo_url VARCHAR(512) NOT NULL COMMENT 'Gitee 仓库地址',
  project_token VARCHAR(1024) DEFAULT NULL COMMENT '项目单独访问令牌，明文存储',
  use_default_token TINYINT NOT NULL DEFAULT 1 COMMENT '是否使用默认访问令牌',
  default_branch VARCHAR(128) NOT NULL DEFAULT 'dev' COMMENT '默认分支',
  owner_name VARCHAR(64) DEFAULT NULL COMMENT '责任人',
  review_days INT NOT NULL DEFAULT 7 COMMENT '默认检视最近 N 天提交',
  schedule_cron VARCHAR(128) DEFAULT NULL COMMENT '定时检视 Cron 表达式',
  schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用定时检视',
  notify_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否发送检视结果通知',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_project_type (project_type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

CREATE TABLE IF NOT EXISTS cr_review_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
  rule_kind VARCHAR(32) NOT NULL DEFAULT 'OCR' COMMENT '规则种类：OCR',
  rule_type VARCHAR(32) NOT NULL COMMENT '规则类型',
  severity VARCHAR(32) NOT NULL COMMENT '默认严重等级',
  project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '适用项目类型',
  prompt_template TEXT COMMENT 'OpenCodeReview 规则内容',
  path_pattern VARCHAR(512) NOT NULL DEFAULT '**/*' COMMENT 'OpenCodeReview 路径匹配模式',
  merge_system_rule TINYINT NOT NULL DEFAULT 1 COMMENT '是否合并 OpenCodeReview 内置规则',
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
  version VARCHAR(32) NOT NULL DEFAULT '1.0.0' COMMENT '版本号',
  project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '适用项目类型：ALL/FRONTEND/BACKEND',
  rule_matching_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 Skill 匹配规则',
  match_rules TEXT COMMENT 'Skill 匹配规则',
  review_guidelines MEDIUMTEXT COMMENT 'Skill 检视关注点',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_skill_code (skill_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Skill 表';

CREATE TABLE IF NOT EXISTS cr_script_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  script_name VARCHAR(128) NOT NULL COMMENT '脚本名称',
  script_code VARCHAR(64) NOT NULL COMMENT '脚本编码',
  project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '适用项目类型：ALL/FRONTEND/BACKEND',
  rule_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT '问题类型',
  severity VARCHAR(32) NOT NULL DEFAULT 'HIGH' COMMENT '默认严重等级',
  description VARCHAR(512) DEFAULT NULL COMMENT '规则说明',
  script_content MEDIUMTEXT NOT NULL COMMENT '脚本内容',
  timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '超时时间',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_script_code (script_code),
  KEY idx_project_type (project_type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Python 脚本规则表';

INSERT INTO cr_script_rule
(script_name, script_code, project_type, rule_type, severity, description, script_content, timeout_seconds, status, sort_order)
SELECT '后端 Java 命名规范检查',
       'DEFAULT_BACKEND_JAVA_NAMING',
       'BACKEND',
       'NAMING',
       'MEDIUM',
       '检查 Java diff 新增行中的类名、方法名、变量名、常量名和包名命名规范。',
       'import json, re, sys
data = json.load(sys.stdin)
issues = []
upper = re.compile(r"^[A-Z][A-Za-z0-9]*$")
lower = re.compile(r"^[a-z][A-Za-z0-9]*$")
constant = re.compile(r"^[A-Z][A-Z0-9_]*$")
package = re.compile(r"^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$")
def add(file_path, line, summary, detail, suggestion, snippet):
    issues.append({"severity":"MINOR","issueType":"NAMING","filePath":file_path,"startLine":line,"endLine":line,"summary":summary,"detail":detail,"suggestion":suggestion,"codeSnippet":snippet})
file_path = None
new_line = None
for raw in data.get("diffContent", "").splitlines():
    if raw.startswith("diff --git "):
        parts = raw.split()
        file_path = parts[3][2:] if len(parts) >= 4 and parts[3].startswith("b/") else None
        continue
    if raw.startswith("+++ b/"):
        file_path = raw[6:]
        continue
    m = re.match(r"@@ -\d+(?:,\d)? \+(\d+)(?:,\d+)? @@", raw)
    if m:
        new_line = int(m.group(1))
        continue
    if new_line is None or not file_path:
        continue
    if raw.startswith("+") and not raw.startswith("+++"):
        line = raw[1:]
        text = line.strip()
        if file_path.endswith(".java") and text and not text.startswith(("//", "*", "/*", "@")):
            pm = re.match(r"^package\s+([A-Za-z0-9_.]+)\s*;", text)
            if pm and not package.match(pm.group(1)):
                add(file_path, new_line, "包名不符合小写命名规范", "Java 包名应仅使用小写字母、数字和点号。", "将包名调整为全小写分段。", line)
            tm = re.search(r"\b(class|interface|enum|@interface)\s+([A-Za-z_$][\w$]*)", text)
            if tm and not upper.match(tm.group(2)):
                add(file_path, new_line, "类型名不符合大驼峰命名规范", "类、接口、枚举和注解类型名应使用 UpperCamelCase。", "将类型名重命名为 UpperCamelCase。", line)
            cm = re.search(r"\b(static\s+final|final\s+static)\b[^=;]*\s+([A-Za-z_$][\w$]*)\s*[=;]", text)
            if cm and not constant.match(cm.group(2)):
                add(file_path, new_line, "常量名不符合全大写下划线规范", "static final 常量应使用全大写下划线命名。", "将常量名改为 UPPER_SNAKE_CASE。", line)
            mm = re.search(r"\b(?:public|protected|private)?\s*(?:static\s+)?[A-Za-z0-9_<>, ?\[\]]+\s+([A-Za-z_$][\w$]*)\s*\(", text)
            if mm and mm.group(1) not in ("if","for","while","switch","catch") and not lower.match(mm.group(1)):
                add(file_path, new_line, "方法名不符合小驼峰命名规范", "Java 方法名应使用 lowerCamelCase。", "将方法名重命名为 lowerCamelCase。", line)
        new_line += 1
    elif raw.startswith("-") and not raw.startswith("---"):
        continue
    else:
        new_line += 1
print(json.dumps({"issues": issues}, ensure_ascii=False))',
       20,
       1,
       10
WHERE NOT EXISTS (
  SELECT 1 FROM cr_script_rule WHERE script_code = 'DEFAULT_BACKEND_JAVA_NAMING' AND deleted = 0
);

-- OpenCodeReview 不支持平台旧有的 Skill 和 Python 脚本执行链路。
DELETE FROM cr_ai_skill;
DELETE FROM cr_script_rule;

INSERT INTO cr_review_rule
(rule_name, rule_code, rule_kind, rule_type, severity, project_type, prompt_template, path_pattern, merge_system_rule, status, sort_order)
SELECT '后端 Java 默认检视规则', 'DEFAULT_BACKEND_JAVA_OCR', 'OCR', 'OTHER', 'HIGH', 'ALL',
       '重点检查空指针、事务失效、SQL 注入、敏感信息泄露、资源泄漏、并发一致性、鉴权与参数校验问题。',
       '**/*.{java,xml,properties,yml,yaml}', 1, 1, 10
WHERE NOT EXISTS (
  SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_BACKEND_JAVA_OCR' AND deleted = 0
);

INSERT INTO cr_review_rule
(rule_name, rule_code, rule_kind, rule_type, severity, project_type, prompt_template, path_pattern, merge_system_rule, status, sort_order)
SELECT '前端 Web 默认检视规则', 'DEFAULT_FRONTEND_WEB_OCR', 'OCR', 'OTHER', 'HIGH', 'ALL',
       '重点检查状态竞态、Hooks 依赖、XSS、权限绕过、异常态遗漏、资源清理和明显性能问题。',
       '**/*.{js,jsx,ts,tsx,vue,css,scss}', 1, 1, 20
WHERE NOT EXISTS (
  SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_FRONTEND_WEB_OCR' AND deleted = 0
);

CREATE TABLE IF NOT EXISTS cr_review_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_no VARCHAR(64) NOT NULL COMMENT '任务编号',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称冗余',
  trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式：MANUAL/SCHEDULE',
  review_branch VARCHAR(128) NOT NULL COMMENT '检视分支',
  review_days INT NOT NULL COMMENT '检视最近 N 天',
  review_mode VARCHAR(32) NOT NULL DEFAULT 'RANGE' COMMENT 'OCR 检视方式：RANGE/COMMIT/WORKSPACE/SCAN',
  base_ref VARCHAR(255) DEFAULT NULL COMMENT '区间起始引用',
  target_ref VARCHAR(255) DEFAULT NULL COMMENT '区间目标引用',
  commit_ref VARCHAR(255) DEFAULT NULL COMMENT '单提交引用',
  scan_path VARCHAR(2000) DEFAULT NULL COMMENT '扫描路径',
  scan_exclude VARCHAR(2000) DEFAULT NULL COMMENT '扫描排除规则',
  scan_no_plan TINYINT NOT NULL DEFAULT 0 COMMENT '扫描时跳过 PLAN_TASK',
  max_tokens_budget BIGINT NOT NULL DEFAULT 500000 COMMENT '扫描 Token 预算',
  review_background TEXT COMMENT '检视背景',
  review_start_time DATETIME DEFAULT NULL COMMENT '定时检视窗口开始时间',
  review_end_time DATETIME DEFAULT NULL COMMENT '定时检视窗口结束时间',
  notify_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否发送机器人通知',
  commit_count INT NOT NULL DEFAULT 0 COMMENT '提交数量',
  diff_file_count INT NOT NULL DEFAULT 0 COMMENT 'diff 文件数量',
  issue_count INT NOT NULL DEFAULT 0 COMMENT '问题总数',
  critical_count INT NOT NULL DEFAULT 0 COMMENT 'OCR critical 数量',
  high_count INT NOT NULL DEFAULT 0 COMMENT 'OCR high 数量',
  medium_count INT NOT NULL DEFAULT 0 COMMENT 'OCR medium 数量',
  low_count INT NOT NULL DEFAULT 0 COMMENT 'OCR low 数量',
  ai_call_count INT NOT NULL DEFAULT 0 COMMENT '大模型调用总次数',
  ai_success_count INT NOT NULL DEFAULT 0 COMMENT '大模型调用成功次数',
  ai_failure_count INT NOT NULL DEFAULT 0 COMMENT '大模型调用失败次数',
  input_token_count BIGINT NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
  output_token_count BIGINT NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
  total_token_count BIGINT NOT NULL DEFAULT 0 COMMENT '总 Token 数',
  cache_read_token_count BIGINT NOT NULL DEFAULT 0 COMMENT '缓存读取 Token 数',
  cache_write_token_count BIGINT NOT NULL DEFAULT 0 COMMENT '缓存写入 Token 数',
  skipped_commit_count INT NOT NULL DEFAULT 0 COMMENT '跳过提交数量',
  skipped_file_count INT NOT NULL DEFAULT 0 COMMENT '跳过文件数量',
  status VARCHAR(32) NOT NULL COMMENT '任务状态',
  start_time DATETIME DEFAULT NULL COMMENT '开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '结束时间',
  warning_message TEXT COMMENT '成功任务提示信息',
  error_message TEXT COMMENT '错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_task_no (task_no),
  KEY idx_project_id (project_id),
  KEY idx_status (status),
  KEY idx_create_time (create_time),
  UNIQUE KEY uk_schedule_window (project_id, trigger_type, review_start_time, review_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视任务表';

CREATE TABLE IF NOT EXISTS cr_review_issue (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  assignee_user_id VARCHAR(64) DEFAULT NULL COMMENT '问题责任人用户 ID',
  assignee_name VARCHAR(64) DEFAULT NULL COMMENT '问题责任人姓名',
  assignee_employee_id VARCHAR(32) DEFAULT NULL COMMENT '问题责任人工号',
  commit_author VARCHAR(128) DEFAULT NULL COMMENT 'Git 提交人',
  rule_id BIGINT DEFAULT NULL COMMENT '命中的规则 ID',
  skill_id BIGINT DEFAULT NULL COMMENT '关联 Skill ID',
  script_id BIGINT DEFAULT NULL COMMENT '关联脚本 ID',
  rule_name VARCHAR(128) DEFAULT NULL COMMENT '命中规则名称快照',
  skill_name VARCHAR(128) DEFAULT NULL COMMENT '命中 Skill 名称快照',
  script_name VARCHAR(128) DEFAULT NULL COMMENT '命中脚本名称快照',
  issue_source VARCHAR(32) NOT NULL COMMENT '来源：OCR',
  severity VARCHAR(32) NOT NULL COMMENT '严重等级',
  issue_type VARCHAR(64) NOT NULL COMMENT '问题类型',
  file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
  start_line INT DEFAULT NULL COMMENT '起始行',
  end_line INT DEFAULT NULL COMMENT '结束行',
  summary VARCHAR(512) NOT NULL COMMENT '问题摘要',
  detail TEXT COMMENT '问题详情',
  suggestion TEXT COMMENT '修改建议',
  code_snippet MEDIUMTEXT COMMENT '相关代码片段',
  raw_response MEDIUMTEXT COMMENT 'OpenCodeReview 原始响应',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '问题状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_task_id (task_id),
  KEY idx_assignee_user_id (assignee_user_id),
  KEY idx_project_id (project_id),
  KEY idx_rule_id (rule_id),
  KEY idx_script_id (script_id),
  KEY idx_severity (severity),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视问题表';

CREATE TABLE IF NOT EXISTS cr_review_report (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '检视任务 ID',
  task_no VARCHAR(64) NOT NULL COMMENT '检视任务编号',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  report_title VARCHAR(256) NOT NULL COMMENT '报告标题',
  report_content MEDIUMTEXT NOT NULL COMMENT '报告 HTML 内容',
  active_issue_count INT NOT NULL DEFAULT 0 COMMENT '有效问题数',
  ignored_issue_count INT NOT NULL DEFAULT 0 COMMENT '已忽略问题数',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_project_id (project_id),
  KEY idx_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检视报告表';

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

CREATE TABLE IF NOT EXISTS cr_notify_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_id BIGINT DEFAULT NULL COMMENT '通知配置 ID',
  task_id BIGINT DEFAULT NULL COMMENT '检视任务 ID',
  task_no VARCHAR(64) DEFAULT NULL COMMENT '检视任务编号',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型',
  webhook_url VARCHAR(1024) NOT NULL COMMENT 'Webhook 地址',
  request_content MEDIUMTEXT COMMENT '请求内容',
  response_content MEDIUMTEXT COMMENT '响应内容',
  status VARCHAR(32) NOT NULL COMMENT '投递状态：PENDING/SUCCESS/FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  next_retry_time DATETIME DEFAULT NULL COMMENT '下次重试时间',
  last_error TEXT COMMENT '最近错误',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_status_retry (status, next_retry_time),
  KEY idx_task (task_id),
  KEY idx_config (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知投递日志表';

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

CREATE TABLE IF NOT EXISTS cr_model_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
  provider_type VARCHAR(64) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '服务类型',
  base_url VARCHAR(1024) NOT NULL COMMENT 'OpenAI 兼容 Base URL 或 chat completions URL',
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  api_key TEXT COMMENT 'API Key',
  enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_enabled (enabled),
  KEY idx_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型服务配置表';

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
