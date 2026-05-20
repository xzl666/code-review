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
        addColumnIfMissing("cr_ai_skill", "project_type",
            "ALTER TABLE cr_ai_skill ADD COLUMN project_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'applicable project type' AFTER version");
        addColumnIfMissing("cr_ai_skill", "rule_matching_enabled",
            "ALTER TABLE cr_ai_skill ADD COLUMN rule_matching_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'rule matching enabled' AFTER project_type");
        addColumnIfMissing("cr_ai_skill", "match_rules",
            "ALTER TABLE cr_ai_skill ADD COLUMN match_rules TEXT COMMENT 'skill match rules' AFTER rule_matching_enabled");
        seedDefaultAiSkills();
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class,
            tableName,
            columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void createTableIfMissing(String tableName, String ddl) {
        if (!tableExists(tableName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private void seedDefaultAiSkills() {
        if (!tableExists("cr_ai_skill") || !tableExists("cr_review_rule")) {
            return;
        }
        Long frontendSkillId = seedSkill(
            "前端 React Web 默认检视 Skill",
            "DEFAULT_FRONTEND_REACT_WEB_REVIEW",
            "面向 React Web 前端项目，重点检视组件状态、Hooks、异步请求、XSS、表单校验、性能和可维护性问题。",
            "FRONTEND",
            frontendMatchRules()
        );
        Long backendSkillId = seedSkill(
            "后端 Java Web 默认检视 Skill",
            "DEFAULT_BACKEND_JAVA_WEB_REVIEW",
            "面向 Java Web 后端项目，重点检视接口安全、事务边界、异常处理、参数校验、并发、资源释放和数据库访问问题。",
            "BACKEND",
            backendMatchRules()
        );
        seedAiRule("前端 React Web 默认 AI 检视", "DEFAULT_FRONTEND_REACT_WEB_AI_REVIEW", "FRONTEND", frontendSkillId, 10);
        seedAiRule("后端 Java Web 默认 AI 检视", "DEFAULT_BACKEND_JAVA_WEB_AI_REVIEW", "BACKEND", backendSkillId, 20);
    }

    private Long seedSkill(String name, String code, String description, String projectType, String matchRules) {
        Long id = skillId(code);
        if (id != null) {
            return id;
        }
        jdbcTemplate.update(
            "INSERT INTO cr_ai_skill (skill_name, skill_code, function_name, function_description, parameters_schema, version, project_type, rule_matching_enabled, match_rules, status) "
                + "VALUES (?, ?, 'submit_review_issues', ?, ?, '1.0.0', ?, 1, ?, 1)",
            name,
            code,
            description,
            defaultIssueSchema(),
            projectType,
            matchRules
        );
        return skillId(code);
    }

    private void seedAiRule(String name, String code, String projectType, Long skillId, int sortOrder) {
        if (skillId == null || ruleExists(code)) {
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

    private String defaultPromptTemplate() {
        return "请检视项目 ${projectName}（${projectType}）在分支 ${branch} 最近 ${reviewDays} 天的代码变更。"
            + "只针对 diff 中新增的新文件行报告真实问题，忽略纯上下文代码。"
            + "请重点关注 Web 项目的安全、稳定性、可维护性、性能、异常处理、输入校验和边界条件。"
            + "输出字段必须使用中文描述，并通过函数调用返回结构化 issues。"
            + "\n\n${diffContent}";
    }

    private String defaultIssueSchema() {
        return "{\n"
            + "  \"type\": \"object\",\n"
            + "  \"properties\": {\n"
            + "    \"issues\": {\n"
            + "      \"type\": \"array\",\n"
            + "      \"items\": {\n"
            + "        \"type\": \"object\",\n"
            + "        \"properties\": {\n"
            + "          \"issueType\": { \"type\": \"string\" },\n"
            + "          \"severity\": { \"type\": \"string\" },\n"
            + "          \"filePath\": { \"type\": \"string\" },\n"
            + "          \"startLine\": { \"type\": \"integer\" },\n"
            + "          \"endLine\": { \"type\": \"integer\" },\n"
            + "          \"summary\": { \"type\": \"string\" },\n"
            + "          \"detail\": { \"type\": \"string\" },\n"
            + "          \"suggestion\": { \"type\": \"string\" },\n"
            + "          \"codeSnippet\": { \"type\": \"string\" }\n"
            + "        },\n"
            + "        \"required\": [\"issueType\", \"severity\", \"filePath\", \"summary\", \"suggestion\"]\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"required\": [\"issues\"]\n"
            + "}";
    }
}
