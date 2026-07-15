package com.cmbchina.codereview.infrastructure.persistence;

import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        createTableIfMissing("cr_model_config",
            "CREATE TABLE IF NOT EXISTS cr_model_config ("
                + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',"
                + "config_name VARCHAR(128) NOT NULL COMMENT 'config name',"
                + "provider_type VARCHAR(64) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'provider type',"
                + "base_url VARCHAR(1024) NOT NULL COMMENT 'OpenAI compatible base url or chat completions url',"
                + "model_name VARCHAR(128) NOT NULL COMMENT 'model name',"
                + "api_key TEXT COMMENT 'api key',"
                + "enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'enabled flag',"
                + "remark VARCHAR(512) DEFAULT NULL COMMENT 'remark',"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',"
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',"
                + "deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete',"
                + "PRIMARY KEY (id),"
                + "KEY idx_enabled (enabled),"
                + "KEY idx_provider_type (provider_type)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='model service config'");
        createTableIfMissing("cr_notify_delivery_log",
            "CREATE TABLE IF NOT EXISTS cr_notify_delivery_log ("
                + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',"
                + "config_id BIGINT DEFAULT NULL COMMENT 'notify config id',"
                + "task_id BIGINT DEFAULT NULL COMMENT 'review task id',"
                + "task_no VARCHAR(64) DEFAULT NULL COMMENT 'review task no',"
                + "event_type VARCHAR(64) NOT NULL COMMENT 'event type',"
                + "channel_type VARCHAR(32) NOT NULL COMMENT 'channel type',"
                + "webhook_url VARCHAR(1024) NOT NULL COMMENT 'webhook url',"
                + "request_content MEDIUMTEXT COMMENT 'request content',"
                + "response_content MEDIUMTEXT COMMENT 'response content',"
                + "status VARCHAR(32) NOT NULL COMMENT 'delivery status',"
                + "retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry count',"
                + "next_retry_time DATETIME DEFAULT NULL COMMENT 'next retry time',"
                + "last_error TEXT COMMENT 'last error',"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',"
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',"
                + "deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete',"
                + "PRIMARY KEY (id),"
                + "KEY idx_status_retry (status, next_retry_time),"
                + "KEY idx_task (task_id),"
                + "KEY idx_config (config_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notification delivery log'");
        createTableIfMissing("cr_review_report",
            "CREATE TABLE IF NOT EXISTS cr_review_report ("
                + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',"
                + "task_id BIGINT NOT NULL COMMENT 'review task id',"
                + "task_no VARCHAR(64) NOT NULL COMMENT 'review task no',"
                + "project_id BIGINT NOT NULL COMMENT 'project id',"
                + "report_title VARCHAR(256) NOT NULL COMMENT 'report title',"
                + "report_content MEDIUMTEXT NOT NULL COMMENT 'report html content',"
                + "active_issue_count INT NOT NULL DEFAULT 0 COMMENT 'active issue count',"
                + "ignored_issue_count INT NOT NULL DEFAULT 0 COMMENT 'ignored issue count',"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',"
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',"
                + "deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete',"
                + "PRIMARY KEY (id),"
                + "UNIQUE KEY uk_task_id (task_id),"
                + "KEY idx_project_id (project_id),"
                + "KEY idx_task_no (task_no)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='review report'");

        addColumnIfMissing("cr_project", "schedule_cron",
            "ALTER TABLE cr_project ADD COLUMN schedule_cron VARCHAR(128) DEFAULT NULL COMMENT 'schedule cron' AFTER review_days");
        addColumnIfMissing("cr_project", "schedule_enabled",
            "ALTER TABLE cr_project ADD COLUMN schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'schedule enabled' AFTER schedule_cron");
        addColumnIfMissing("cr_project", "notify_enabled",
            "ALTER TABLE cr_project ADD COLUMN notify_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'notify enabled' AFTER schedule_enabled");
        addColumnIfMissing("cr_project", "notify_webhook_url",
            "ALTER TABLE cr_project ADD COLUMN notify_webhook_url VARCHAR(1024) DEFAULT NULL COMMENT 'project notify webhook url' AFTER notify_enabled");
        addColumnIfMissing("cr_project", "notify_extra_params",
            "ALTER TABLE cr_project ADD COLUMN notify_extra_params TEXT COMMENT 'project notify extra params json' AFTER notify_webhook_url");
        addColumnIfMissing("cr_review_task", "skipped_commit_count",
            "ALTER TABLE cr_review_task ADD COLUMN skipped_commit_count INT NOT NULL DEFAULT 0 COMMENT 'skipped commit count' AFTER ai_call_count");
        addColumnIfMissing("cr_review_task", "skipped_file_count",
            "ALTER TABLE cr_review_task ADD COLUMN skipped_file_count INT NOT NULL DEFAULT 0 COMMENT 'skipped file count' AFTER skipped_commit_count");
        addColumnIfMissing("cr_review_task", "warning_message",
            "ALTER TABLE cr_review_task ADD COLUMN warning_message TEXT COMMENT 'task warning message' AFTER end_time");
        addColumnIfMissing("cr_review_task", "error_message",
            "ALTER TABLE cr_review_task ADD COLUMN error_message TEXT COMMENT 'task error message' AFTER warning_message");
        addColumnIfMissing("cr_review_issue", "script_id",
            "ALTER TABLE cr_review_issue ADD COLUMN script_id BIGINT DEFAULT NULL COMMENT 'matched script id' AFTER skill_id");
        addColumnIfMissing("cr_review_issue", "rule_name",
            "ALTER TABLE cr_review_issue ADD COLUMN rule_name VARCHAR(128) DEFAULT NULL COMMENT 'matched rule name snapshot' AFTER script_id");
        addColumnIfMissing("cr_review_issue", "skill_name",
            "ALTER TABLE cr_review_issue ADD COLUMN skill_name VARCHAR(128) DEFAULT NULL COMMENT 'matched skill name snapshot' AFTER rule_name");
        addColumnIfMissing("cr_review_issue", "script_name",
            "ALTER TABLE cr_review_issue ADD COLUMN script_name VARCHAR(128) DEFAULT NULL COMMENT 'matched script name snapshot' AFTER skill_name");

        addColumnIfMissing("cr_ai_skill", "project_type",
            "ALTER TABLE cr_ai_skill ADD COLUMN project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'applicable project type' AFTER version");
        addColumnIfMissing("cr_ai_skill", "rule_matching_enabled",
            "ALTER TABLE cr_ai_skill ADD COLUMN rule_matching_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'rule matching enabled' AFTER project_type");
        addColumnIfMissing("cr_ai_skill", "match_rules",
            "ALTER TABLE cr_ai_skill ADD COLUMN match_rules TEXT COMMENT 'skill match rules' AFTER rule_matching_enabled");
        addColumnIfMissing("cr_ai_skill", "review_guidelines",
            "ALTER TABLE cr_ai_skill ADD COLUMN review_guidelines MEDIUMTEXT COMMENT 'skill review guidelines' AFTER match_rules");
        deleteLegacyAiSkillsWithoutGuidelines();
        dropIndexIfExists("cr_ai_skill", "idx_function_name");
        dropColumnIfExists("cr_ai_skill", "function_name");
        dropColumnIfExists("cr_ai_skill", "function_description");
        dropColumnIfExists("cr_ai_skill", "parameters_schema");
        normalizeScriptRuleSchema();

        backfillReviewIssueSourceNames();
        seedModelConfig();
        seedDefaultAiSkills();
        seedDefaultPythonScriptRules();
    }

    private void normalizeScriptRuleSchema() {
        if (tableExists("cr_script_rule")
            && (columnExists("cr_script_rule", "script_language") || !columnExists("cr_script_rule", "project_type"))) {
            if (tableExists("cr_review_rule")) {
                jdbcTemplate.update("DELETE FROM cr_review_rule WHERE rule_kind = 'SCRIPT'");
            }
            jdbcTemplate.execute("DROP TABLE cr_script_rule");
        }
        createTableIfMissing("cr_script_rule",
            "CREATE TABLE IF NOT EXISTS cr_script_rule ("
                + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',"
                + "script_name VARCHAR(128) NOT NULL COMMENT 'script rule name',"
                + "script_code VARCHAR(64) NOT NULL COMMENT 'script rule code',"
                + "project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'applicable project type',"
                + "rule_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT 'issue type',"
                + "severity VARCHAR(32) NOT NULL DEFAULT 'MAJOR' COMMENT 'default severity',"
                + "description VARCHAR(512) DEFAULT NULL COMMENT 'rule description',"
                + "script_content MEDIUMTEXT NOT NULL COMMENT 'python script content',"
                + "timeout_seconds INT NOT NULL DEFAULT 30 COMMENT 'timeout seconds',"
                + "status TINYINT NOT NULL DEFAULT 1 COMMENT 'status',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT 'sort order',"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',"
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',"
                + "deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete',"
                + "PRIMARY KEY (id),"
                + "KEY idx_script_code (script_code),"
                + "KEY idx_project_type (project_type),"
                + "KEY idx_status (status)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='python script rule'");
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }

    private void createTableIfMissing(String tableName, String ddl) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        if (columnExists(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        }
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
            Integer.class,
            tableName,
            indexName
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private void deleteLegacyAiSkillsWithoutGuidelines() {
        if (!tableExists("cr_ai_skill") || !columnExists("cr_ai_skill", "review_guidelines")) {
            return;
        }
        if (tableExists("cr_review_rule")) {
            jdbcTemplate.update(
                "DELETE r FROM cr_review_rule r "
                    + "JOIN cr_ai_skill s ON r.skill_id = s.id "
                    + "WHERE r.rule_kind = 'AI' AND (s.review_guidelines IS NULL OR TRIM(s.review_guidelines) = '')"
            );
        }
        jdbcTemplate.update("DELETE FROM cr_ai_skill WHERE review_guidelines IS NULL OR TRIM(review_guidelines) = ''");
    }

    private void backfillReviewIssueSourceNames() {
        if (!tableExists("cr_review_issue") || !tableExists("cr_review_rule")
            || !tableExists("cr_ai_skill") || !tableExists("cr_script_rule")) {
            return;
        }
        jdbcTemplate.update(
            "UPDATE cr_review_issue i "
                + "LEFT JOIN cr_review_rule r ON i.rule_id = r.id "
                + "LEFT JOIN cr_ai_skill s ON i.skill_id = s.id "
                + "LEFT JOIN cr_script_rule sc ON COALESCE(i.script_id, r.script_id) = sc.id "
                + "SET i.script_id = COALESCE(i.script_id, r.script_id), "
                + "i.rule_name = COALESCE(i.rule_name, r.rule_name), "
                + "i.skill_name = COALESCE(i.skill_name, s.skill_name), "
                + "i.script_name = COALESCE(i.script_name, sc.script_name) "
                + "WHERE i.deleted = 0 AND (i.rule_name IS NULL OR i.skill_name IS NULL OR i.script_name IS NULL OR i.script_id IS NULL)"
        );
    }

    private void seedModelConfig() {
        if (!tableExists("cr_model_config") || hasModelConfig()) {
            return;
        }
        String apiKey = configValue("DEEPSEEK_API_KEY");
        String baseUrl = configValue("DEEPSEEK_URL");
        String modelName = configValue("DEEPSEEK_MODEL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://zhenze-huhehaote.cmecloud.cn";
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            modelName = "deepseek-v4-flash";
        }
        jdbcTemplate.update(
            "INSERT INTO cr_model_config (config_name, provider_type, base_url, model_name, api_key, enabled, remark) "
                + "VALUES (?, 'OPENAI_COMPATIBLE', ?, ?, ?, 1, ?)",
            "DeepSeek 默认配置",
            baseUrl,
            modelName,
            apiKey,
            "由旧 DeepSeek 配置自动迁移"
        );
    }

    private boolean hasModelConfig() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cr_model_config WHERE deleted = 0",
            Integer.class
        );
        return count != null && count > 0;
    }

    private String configValue(String key) {
        if (!tableExists("cr_system_config")) {
            return null;
        }
        return jdbcTemplate.query(
            "SELECT config_value FROM cr_system_config WHERE config_key = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
            resultSet -> resultSet.next() ? resultSet.getString("config_value") : null,
            key
        );
    }

    private void seedDefaultAiSkills() {
        if (!tableExists("cr_ai_skill") || !tableExists("cr_review_rule")) {
            return;
        }
        Long frontendSkillId = seedSkill(
            "前端 React Web 默认检视 Skill",
            "DEFAULT_FRONTEND_REACT_WEB_REVIEW",
            "1. 组件状态更新必须避免竞态、重复请求和卸载后 setState。\n"
                + "2. Hooks 依赖必须完整，不能因为缺失依赖导致闭包脏数据。\n"
                + "3. 外部输入渲染到页面前必须转义或校验，避免 XSS。\n"
                + "4. 表单、路由参数和接口返回必须处理空值、异常态和权限态。\n"
                + "5. 列表、轮询、定时器、订阅和事件监听必须有清理逻辑，避免性能和内存问题。",
            "FRONTEND",
            frontendMatchRules()
        );
        Long backendSkillId = seedSkill(
            "后端 Java Web 默认检视 Skill",
            "DEFAULT_BACKEND_JAVA_WEB_REVIEW",
            "1. Controller 入参必须校验鉴权、越权、必填、范围和格式，不能直接信任前端参数。\n"
                + "2. SQL、路径、命令、URL、表达式等不能由外部输入直接拼接，必须参数化或白名单校验。\n"
                + "3. 事务边界必须清晰，不能吞掉异常、异步跨事务误用或导致回滚失效。\n"
                + "4. 异常处理不能泄露敏感信息，日志不能输出 token、密码、身份证、手机号等敏感数据。\n"
                + "5. 数据库查询必须考虑分页、索引、N+1、批量操作和锁竞争，避免生产性能风险。\n"
                + "6. 文件上传下载、外部 URL 访问、反序列化、回调验签必须做安全校验。\n"
                + "7. IO、连接、流、线程池、锁等资源必须正确释放，避免泄漏和阻塞。\n"
                + "8. 并发更新必须考虑幂等、重复提交、乐观锁或分布式锁，避免数据不一致。",
            "BACKEND",
            backendMatchRules()
        );
        seedAiRule("前端 React Web 默认 AI 检视", "DEFAULT_FRONTEND_REACT_WEB_AI_REVIEW", "FRONTEND", frontendSkillId, 10);
        seedAiRule("后端 Java Web 默认 AI 检视", "DEFAULT_BACKEND_JAVA_WEB_AI_REVIEW", "BACKEND", backendSkillId, 20);
    }

    private void seedDefaultPythonScriptRules() {
        if (!tableExists("cr_script_rule") || scriptRuleExists("DEFAULT_BACKEND_JAVA_NAMING")) {
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO cr_script_rule "
                + "(script_name, script_code, project_type, rule_type, severity, description, script_content, timeout_seconds, status, sort_order) "
                + "VALUES (?, ?, 'BACKEND', 'NAMING', 'MINOR', ?, ?, 20, 1, 10)",
            "后端 Java 命名规范检查",
            "DEFAULT_BACKEND_JAVA_NAMING",
            "检查 Java diff 新增行中的类名、方法名、变量名、常量名和包名命名规范。",
            defaultJavaNamingScript()
        );
    }

    private boolean scriptRuleExists(String scriptCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cr_script_rule WHERE script_code = ? AND deleted = 0",
            Integer.class,
            scriptCode
        );
        return count != null && count > 0;
    }

    private Long seedSkill(String name, String code, String guidelines, String projectType, String matchRules) {
        Long id = skillId(code);
        if (id != null) {
            jdbcTemplate.update(
                "UPDATE cr_ai_skill SET skill_name = ?, version = '1.0.0', project_type = ?, rule_matching_enabled = 1, "
                    + "match_rules = ?, review_guidelines = ?, status = 1 WHERE id = ?",
                name,
                projectType,
                matchRules,
                guidelines,
                id
            );
            return id;
        }
        jdbcTemplate.update(
            "INSERT INTO cr_ai_skill (skill_name, skill_code, version, project_type, rule_matching_enabled, match_rules, review_guidelines, status) "
                + "VALUES (?, ?, '1.0.0', ?, 1, ?, ?, 1)",
            name,
            code,
            projectType,
            matchRules,
            guidelines
        );
        return skillId(code);
    }

    private void seedAiRule(String name, String code, String projectType, Long skillId, int sortOrder) {
        if (skillId == null) {
            return;
        }
        if (ruleExists(code)) {
            jdbcTemplate.update(
                "UPDATE cr_review_rule SET rule_name = ?, rule_type = 'CUSTOM', severity = 'MAJOR', project_type = ?, "
                    + "prompt_template = ?, skill_id = ?, status = 1, sort_order = ? WHERE rule_code = ? AND deleted = 0",
                name,
                projectType,
                defaultPromptTemplate(),
                skillId,
                sortOrder,
                code
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO cr_review_rule (rule_name, rule_code, rule_kind, rule_type, severity, project_type, prompt_template, skill_id, status, sort_order) "
                + "VALUES (?, ?, 'AI', 'CUSTOM', 'MAJOR', ?, ?, ?, 1, ?)",
            name,
            code,
            projectType,
            defaultPromptTemplate(),
            skillId,
            sortOrder
        );
    }

    private Long skillId(String skillCode) {
        return jdbcTemplate.query(
            "SELECT id FROM cr_ai_skill WHERE skill_code = ? AND deleted = 0 ORDER BY id LIMIT 1",
            resultSet -> resultSet.next() ? resultSet.getLong("id") : null,
            skillCode
        );
    }

    private boolean ruleExists(String ruleCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cr_review_rule WHERE rule_code = ? AND deleted = 0",
            Integer.class,
            ruleCode
        );
        return count != null && count > 0;
    }

    private String frontendMatchRules() {
        return "ext:js,jsx,ts,tsx,vue\n"
            + "path:**/src/**\n"
            + "contains:useEffect\n"
            + "contains:dangerouslySetInnerHTML\n"
            + "contains:localStorage\n"
            + "contains:fetch(";
    }

    private String backendMatchRules() {
        return "ext:java\n"
            + "path:**/src/main/java/**\n"
            + "contains:@RestController\n"
            + "contains:@Controller\n"
            + "contains:@Service\n"
            + "contains:@Transactional";
    }

    private String defaultJavaNamingScript() {
        return String.join("\n",
            "import json",
            "import re",
            "import sys",
            "",
            "data = json.load(sys.stdin)",
            "issues = []",
            "",
            "UPPER_CAMEL = re.compile(r'^[A-Z][A-Za-z0-9]*$')",
            "LOWER_CAMEL = re.compile(r'^[a-z][A-Za-z0-9]*$')",
            "CONSTANT = re.compile(r'^[A-Z][A-Z0-9_]*$')",
            "PACKAGE = re.compile(r'^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$')",
            "",
            "def add(file_path, line, summary, detail, suggestion, snippet):",
            "    issues.append({",
            "        'severity': 'MINOR',",
            "        'issueType': 'NAMING',",
            "        'filePath': file_path,",
            "        'startLine': line,",
            "        'endLine': line,",
            "        'summary': summary,",
            "        'detail': detail,",
            "        'suggestion': suggestion,",
            "        'codeSnippet': snippet",
            "    })",
            "",
            "def added_lines(diff):",
            "    current_file = None",
            "    new_line = None",
            "    for raw in diff.splitlines():",
            "        if raw.startswith('diff --git '):",
            "            parts = raw.split()",
            "            current_file = parts[3][2:] if len(parts) >= 4 and parts[3].startswith('b/') else None",
            "            new_line = None",
            "            continue",
            "        if raw.startswith('+++ b/'):",
            "            current_file = raw[6:]",
            "            continue",
            "        match = re.match(r'@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@', raw)",
            "        if match:",
            "            new_line = int(match.group(1))",
            "            continue",
            "        if new_line is None or current_file is None:",
            "            continue",
            "        if raw.startswith('+') and not raw.startswith('+++'):",
            "            yield current_file, new_line, raw[1:]",
            "            new_line += 1",
            "        elif raw.startswith('-') and not raw.startswith('---'):",
            "            continue",
            "        else:",
            "            new_line += 1",
            "",
            "def clean(line):",
            "    return line.strip()",
            "",
            "def skip(line):",
            "    text = clean(line)",
            "    return not text or text.startswith('//') or text.startswith('*') or text.startswith('/*') or text.startswith('@')",
            "",
            "for file_path, line_no, line in added_lines(data.get('diffContent', '')):",
            "    if not file_path.endswith('.java') or skip(line):",
            "        continue",
            "    text = clean(line)",
            "    package_match = re.match(r'^package\\s+([A-Za-z0-9_.]+)\\s*;', text)",
            "    if package_match and not PACKAGE.match(package_match.group(1)):",
            "        add(file_path, line_no, '包名不符合小写命名规范', 'Java 包名应仅使用小写字母、数字和点号。', '将包名调整为全小写分段。', line)",
            "    type_match = re.search(r'\\b(class|interface|enum|@interface)\\s+([A-Za-z_$][\\w$]*)', text)",
            "    if type_match and not UPPER_CAMEL.match(type_match.group(2)):",
            "        add(file_path, line_no, '类型名不符合大驼峰命名规范', '类、接口、枚举和注解类型名应以大写字母开头并使用 UpperCamelCase。', '将类型名重命名为 UpperCamelCase。', line)",
            "    const_match = re.search(r'\\b(static\\s+final|final\\s+static)\\b[^=;]*\\s+([A-Za-z_$][\\w$]*)\\s*[=;]', text)",
            "    if const_match and not CONSTANT.match(const_match.group(2)):",
            "        add(file_path, line_no, '常量名不符合全大写下划线规范', 'static final 常量应使用全大写下划线命名。', '将常量名改为 UPPER_SNAKE_CASE。', line)",
            "    method_match = re.search(r'\\b(?:public|protected|private)?\\s*(?:static\\s+)?[A-Za-z0-9_<>, ?\\[\\]]+\\s+([A-Za-z_$][\\w$]*)\\s*\\(', text)",
            "    if method_match and method_match.group(1) not in ('if', 'for', 'while', 'switch', 'catch') and not LOWER_CAMEL.match(method_match.group(1)):",
            "        add(file_path, line_no, '方法名不符合小驼峰命名规范', 'Java 方法名应以小写字母开头并使用 lowerCamelCase。', '将方法名重命名为 lowerCamelCase。', line)",
            "    var_match = re.search(r'\\b(?:[A-Z][\\w<>?\\[\\]]+|String|Integer|Long|Boolean|Double|BigDecimal|List<[^>]+>|Map<[^>]+>)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=|;|,)', text)",
            "    if var_match and not LOWER_CAMEL.match(var_match.group(1)) and not CONSTANT.match(var_match.group(1)):",
            "        add(file_path, line_no, '对象名不符合小驼峰命名规范', '变量、参数和对象名应以小写字母开头并使用 lowerCamelCase。', '将对象名重命名为 lowerCamelCase。', line)",
            "",
            "print(json.dumps({'issues': issues}, ensure_ascii=False))");
    }

    private String defaultPromptTemplate() {
        return "请检视项目 ${projectName}（${projectType}）在分支 ${branch} 最近 ${reviewDays} 天的代码变更。\n"
            + "只针对 diff 中新增的新文件行报告真实、可定位、可修复的问题，忽略纯上下文代码。\n"
            + "重点结合 SkillReviewGuidelines 中定义的检视关注点判断问题，不要输出泛泛建议。\n"
            + "所有 summary、detail、suggestion 等描述性字段必须使用中文。\n"
            + "必须通过函数调用返回 {\"issues\":[...]}；没有明确问题时返回空 issues 数组。\n\n"
            + "${diffContent}";
    }
}
