-- 开发/测试用 H2（MODE=MySQL）的初始结构。
-- JSON 列使用 CLOB：Hibernate 的 @Lob String 在 H2 方言下按 CLOB 校验。

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(64),
    role        VARCHAR(16)  NOT NULL,
    status      INT          NOT NULL,
    create_time TIMESTAMP    NOT NULL,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type  VARCHAR(32)  NOT NULL,
    dict_code  VARCHAR(64)  NOT NULL,
    dict_value VARCHAR(128) NOT NULL,
    sort       INT          NOT NULL,
    status     INT          NOT NULL
);

CREATE TABLE IF NOT EXISTS roadnet (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    roadnet_json  CLOB         NOT NULL,
    is_template   BOOLEAN      NOT NULL,
    create_user   BIGINT,
    create_time   TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS task_set (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    roadnet_id  BIGINT,
    tasks_json  CLOB         NOT NULL,
    create_user BIGINT,
    create_time TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    roadnet_id  BIGINT      NOT NULL,
    task_set_id BIGINT      NOT NULL,
    algorithm   VARCHAR(32) NOT NULL,
    agv_count   INT         NOT NULL,
    seed        BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL,
    create_user BIGINT,
    create_time TIMESTAMP   NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_metric (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id   BIGINT NOT NULL,
    makespan        BIGINT NOT NULL,
    total_path_len  DOUBLE NOT NULL,
    total_wait_tick BIGINT NOT NULL,
    conflict_count  INT    NOT NULL,
    CONSTRAINT uk_experiment_metric_experiment UNIQUE (experiment_id)
);
