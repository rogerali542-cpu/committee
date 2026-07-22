-- 录音转写结果落库：解决"后端重启后单段转写查不到、合并稿只剩前端本地缓存"的问题。
-- 当前环境 Flyway 默认关闭，实际建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
-- 只有 ADD，ddl-auto:update 会自动加列，无需手工执行。
-- 注意：重启前已识别完成的旧录音没有这个值（当时没落库），前端单段查看提供「重新识别」入口找回。

ALTER TABLE meeting_recordings
    ADD COLUMN asr_json LONGTEXT NULL COMMENT '该条录音的 ASR 转写结果（AsrResult JSON），识别完成即写入';
