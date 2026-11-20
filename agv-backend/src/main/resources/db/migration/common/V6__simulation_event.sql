-- 任务级仿真事件，按实验保存并设上限；逐帧位置仍不落库。
CREATE TABLE IF NOT EXISTS simulation_event (
    id            BIGINT      NOT NULL PRIMARY KEY,
    experiment_id BIGINT      NOT NULL,
    seq_no        INT         NOT NULL,
    tick          BIGINT      NOT NULL,
    agv_id        VARCHAR(32),
    type          VARCHAR(32) NOT NULL,
    place         VARCHAR(64),
    detail        VARCHAR(500)
);

CREATE INDEX idx_simulation_event_experiment ON simulation_event (experiment_id, seq_no);

ALTER TABLE experiment ADD COLUMN event_truncated BOOLEAN DEFAULT FALSE;
