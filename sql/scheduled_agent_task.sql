CREATE TABLE IF NOT EXISTS `scheduled_agent_task` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `name` VARCHAR(255) NOT NULL COMMENT 'Task name',
    `prompt` TEXT NOT NULL COMMENT 'Prompt sent to the agent on each run',
    `schedule_type` VARCHAR(32) NOT NULL COMMENT 'DAILY, INTERVAL, or CRON',
    `schedule_expr` VARCHAR(128) NOT NULL COMMENT 'HH:mm[:ss], ISO-8601 duration, or cron',
    `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Task timezone',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether dispatch can pick up the task',
    `running` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether a run currently owns the task',
    `lock_until` DATETIME DEFAULT NULL COMMENT 'Run lock expiration',
    `session_mode` VARCHAR(32) NOT NULL DEFAULT 'NEW_EACH_RUN' COMMENT 'NEW_EACH_RUN or FIXED_SESSION',
    `session_id` VARCHAR(255) DEFAULT NULL COMMENT 'Fixed session id when session_mode is FIXED_SESSION',
    `next_run_at` DATETIME NOT NULL COMMENT 'Next due time',
    `last_run_at` DATETIME DEFAULT NULL COMMENT 'Last dispatch time',
    `last_status` VARCHAR(32) DEFAULT NULL COMMENT 'Last run status',
    `last_error` TEXT DEFAULT NULL COMMENT 'Last run error',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    KEY `idx_due_task` (`enabled`, `next_run_at`),
    KEY `idx_lock` (`running`, `lock_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Scheduled agent task';

CREATE TABLE IF NOT EXISTS `scheduled_agent_task_run` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `task_id` INT NOT NULL COMMENT 'Task id',
    `session_id` VARCHAR(255) NOT NULL COMMENT 'Agent session id',
    `status` VARCHAR(32) NOT NULL COMMENT 'RUNNING, SUCCESS, FAILED, or NEEDS_USER',
    `report_id` INT DEFAULT NULL COMMENT 'Generated report id',
    `output_summary` TEXT DEFAULT NULL COMMENT 'Final agent text or summary',
    `error_message` TEXT DEFAULT NULL COMMENT 'Failure reason',
    `started_at` DATETIME NOT NULL COMMENT 'Start time',
    `finished_at` DATETIME DEFAULT NULL COMMENT 'Finish time',
    PRIMARY KEY (`id`),
    KEY `idx_task_started_at` (`task_id`, `started_at`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Scheduled agent task run';
