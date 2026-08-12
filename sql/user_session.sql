CREATE TABLE IF NOT EXISTS `user_session` (
    `user_id`    INT          NOT NULL COMMENT '用户ID，关联 sys_user.id',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID（对应 agentscope_sessions.session_id）',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `session_id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-会话归属表';
