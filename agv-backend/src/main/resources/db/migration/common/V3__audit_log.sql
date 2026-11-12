-- 业务审计日志。H2 与 MySQL 都支持这些类型，脚本放在 common 目录。
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id          BIGINT      NOT NULL PRIMARY KEY,
    username    VARCHAR(64),
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    result      VARCHAR(16) NOT NULL,
    detail      VARCHAR(500),
    ip          VARCHAR(64),
    create_time DATETIME    NOT NULL
);

CREATE INDEX idx_audit_action ON sys_audit_log (action);
CREATE INDEX idx_audit_create_time ON sys_audit_log (create_time);
