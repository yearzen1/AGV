-- 实验输入快照：路网与任务集原文，供回放使用。
ALTER TABLE experiment ADD COLUMN roadnet_snapshot LONGTEXT NULL;
ALTER TABLE experiment ADD COLUMN taskset_snapshot LONGTEXT NULL;
