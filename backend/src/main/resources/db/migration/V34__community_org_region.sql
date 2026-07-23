-- 演示小区「户口」（0723 用户定，B1）：落款全称需要市+区前缀（真实落款如「上海市黄浦区瞿溪新村业主委员会（第三届）」）。
-- 虚构设定：江州市望江区阳光花园小区，第一届（启动时 CommunityProfileSeeder 补默认值，只补空不覆盖）。
-- 注：运行时由 ddl-auto:update 自动建列，本文件仅作变更记录。
ALTER TABLE communities ADD COLUMN org_region VARCHAR(60) NULL;
