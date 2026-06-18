-- 产品边界决策：物业不参与小区行政，业委会会议与业主大会一律不对物业开放。
-- 故"是否对物业开放"配置不再需要，移除该列。见 产品边界定稿.md §2.1 / §10。

ALTER TABLE communities DROP COLUMN disclose_to_property;
