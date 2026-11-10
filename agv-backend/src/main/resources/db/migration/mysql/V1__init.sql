-- 论文/生产环境 MySQL 8+ 的初始结构。
-- JSON 列使用 LONGTEXT：Hibernate 的 @Lob String 在 MySQL 方言下按 LONGTEXT 校验。
-- 布尔列使用 BIT(1)：Hibernate 6 MySQL 方言把 Boolean 映射为 BIT。

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(64),
    role        VARCHAR(16)  NOT NULL,
    status      INT          NOT NULL,
    create_time DATETIME(6)  NOT NULL,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type  VARCHAR(32)  NOT NULL,
    dict_code  VARCHAR(64)  NOT NULL,
    dict_value VARCHAR(128) NOT NULL,
    sort       INT          NOT NULL,
    status     INT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS roadnet (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    roadnet_json LONGTEXT     NOT NULL,
    is_template  BIT(1)       NOT NULL,
    create_user  BIGINT,
    create_time  DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_set (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    roadnet_id  BIGINT,
    tasks_json  LONGTEXT     NOT NULL,
    create_user BIGINT,
    create_time DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS experiment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    roadnet_id  BIGINT      NOT NULL,
    task_set_id BIGINT      NOT NULL,
    algorithm   VARCHAR(32) NOT NULL,
    agv_count   INT         NOT NULL,
    seed        BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL,
    create_user BIGINT,
    create_time DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS experiment_metric (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id   BIGINT NOT NULL,
    makespan        BIGINT NOT NULL,
    total_path_len  DOUBLE NOT NULL,
    total_wait_tick BIGINT NOT NULL,
    conflict_count  INT    NOT NULL,
    CONSTRAINT uk_experiment_metric_experiment UNIQUE (experiment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
