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
        addColumnIfMissing("cr_project", "schedule_cron",
            "ALTER TABLE cr_project ADD COLUMN schedule_cron VARCHAR(128) DEFAULT NULL COMMENT 'schedule cron' AFTER review_days");
        addColumnIfMissing("cr_project", "schedule_enabled",
            "ALTER TABLE cr_project ADD COLUMN schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'schedule enabled' AFTER schedule_cron");
        addColumnIfMissing("cr_review_task", "skipped_commit_count",
            "ALTER TABLE cr_review_task ADD COLUMN skipped_commit_count INT NOT NULL DEFAULT 0 COMMENT 'skipped commit count' AFTER ai_call_count");
        addColumnIfMissing("cr_review_task", "skipped_file_count",
            "ALTER TABLE cr_review_task ADD COLUMN skipped_file_count INT NOT NULL DEFAULT 0 COMMENT 'skipped file count' AFTER skipped_commit_count");
        addColumnIfMissing("cr_review_task", "warning_message",
            "ALTER TABLE cr_review_task ADD COLUMN warning_message TEXT COMMENT 'task warning message' AFTER end_time");
        addColumnIfMissing("cr_review_task", "error_message",
            "ALTER TABLE cr_review_task ADD COLUMN error_message TEXT COMMENT 'task error message' AFTER warning_message");
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
}
