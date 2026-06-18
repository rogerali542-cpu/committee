-- V19: 产品边界收口为业委会内部使用。
-- 业主大会与全体业主花名册相关数据保留历史兼容，不再作为新版本采集和操作范围。

DELETE FROM role_permissions
WHERE permission_code IN ('owner.create', 'owner.advance', 'owner.data_entry', 'owner.publish');

DELETE FROM permissions
WHERE code IN ('owner.create', 'owner.advance', 'owner.data_entry', 'owner.publish');

ALTER TABLE owner_meetings COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_notifies COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_ballots COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_records COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_topics COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_evidences COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_meeting_publishes COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE owner_minutes_revisions COMMENT = 'DEPRECATED: 业主大会模块已停用，仅保留历史数据';
ALTER TABLE housing_units COMMENT = 'DEPRECATED: 业主大会户代表/双过半核算已停用；不再采集全体业主花名册';
ALTER TABLE housing_unit_owners COMMENT = 'DEPRECATED: 业主大会户代表/双过半核算已停用；不再采集全体业主花名册';
