-- Back up first. Resolve duplicate session owners before adding the unique constraint.
-- SELECT session_id, COUNT(*) FROM user_session GROUP BY session_id HAVING COUNT(*) > 1;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id INT NOT NULL,
    permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色功能权限';

ALTER TABLE user_session ADD UNIQUE KEY uk_session_id (session_id);
-- No automatic grants. Super administrators configure existing roles after upgrade.
