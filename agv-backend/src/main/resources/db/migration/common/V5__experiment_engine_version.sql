-- 记录创建实验时的引擎规则版本，用于回放时判断指标是否可直接比较。
-- 旧实验为空，不回填。
ALTER TABLE experiment ADD COLUMN engine_version VARCHAR(16);
