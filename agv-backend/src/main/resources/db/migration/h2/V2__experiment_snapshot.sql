-- 实验输入快照：路网与任务集原文，供回放使用。
-- H2 方言下 @Lob String 按 CLOB 校验。
ALTER TABLE experiment ADD COLUMN roadnet_snapshot CLOB;
ALTER TABLE experiment ADD COLUMN taskset_snapshot CLOB;
