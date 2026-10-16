CREATE TABLE IF NOT EXISTS `answer_provenance` (
    `answer_id` VARCHAR(255) NOT NULL COMMENT '初始用户消息ID',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
    `provenance_json` LONGTEXT NOT NULL COMMENT '本次回答的数据来源快照',
    PRIMARY KEY (`answer_id`),
    KEY `idx_answer_provenance_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回答数据来源';
