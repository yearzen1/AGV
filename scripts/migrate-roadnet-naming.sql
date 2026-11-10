-- 一次性迁移：统一路网命名（Phase 3）
-- 目标：warehouse_map -> roadnet，map_id -> roadnet_id
-- 适用于 H2（MODE=MySQL）与 MySQL 8+。
--
-- 执行前必须备份：
--   H2：停止后端，复制 agv-backend/data/agvdb.mv.db
--   MySQL：mysqldump agv_sim > backup.sql
--
-- H2 手动执行示例（在 agv-backend 目录）：
--   java -cp <h2.jar> org.h2.tools.Shell \
--     -url "jdbc:h2:file:./data/agvdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE" \
--     -user sa -password "" -sql "RUNSCRIPT FROM '../scripts/migrate-roadnet-naming.sql'"
--
-- MySQL 手动执行：
--   mysql -uroot -p agv_sim < scripts/migrate-roadnet-naming.sql
--
-- 只执行一次；重复执行会因表/列已改名而失败。

ALTER TABLE warehouse_map RENAME TO roadnet;
ALTER TABLE task_set RENAME COLUMN map_id TO roadnet_id;
ALTER TABLE experiment RENAME COLUMN map_id TO roadnet_id;

-- 校验：三条查询应分别返回 3 / 1 / 实验总数（以实际数据为准）
SELECT COUNT(*) AS roadnet_count FROM roadnet;
SELECT COUNT(*) AS task_set_count FROM task_set;
SELECT COUNT(*) AS experiment_count FROM experiment;
