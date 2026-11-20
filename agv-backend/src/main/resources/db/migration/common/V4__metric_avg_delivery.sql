-- 平均配送时长（tick）。H2 与 MySQL 都支持 ADD COLUMN，脚本放在 common 目录。
-- 历史实验该列为 NULL，回放时跳过这一项比对。
ALTER TABLE experiment_metric ADD COLUMN avg_delivery_tick BIGINT;
