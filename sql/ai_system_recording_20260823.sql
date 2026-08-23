-- =====================================================================
-- 录音管理模块数据库迁移
-- 日期: 2026-08-23
-- 说明: 为 ai_call_record 增加录音文件路径、访问 URL、时长、ASR 状态
--       及转写文本字段；同时增加 call_uuid 字段，用于 ESL RECORD_STOP
--       事件回写时按 FreeSWITCH 通道 UUID 关联话单。
-- =====================================================================

-- 1. 通话通道 UUID（FreeSWITCH Unique-ID），入站/外呼均可写入，便于事件关联
ALTER TABLE ai_call_record
    ADD COLUMN call_uuid VARCHAR(64) DEFAULT NULL COMMENT '通话通道UUID(FreeSWITCH Unique-ID)' AFTER ticket_id;

-- 2. 录音文件路径（FreeSWITCH recordings_dir 下的相对路径或绝对路径）
ALTER TABLE ai_call_record
    ADD COLUMN record_file VARCHAR(500) DEFAULT NULL COMMENT '录音文件路径';

-- 3. 录音访问 URL（经后端播放接口拼装的对外可访问地址）
ALTER TABLE ai_call_record
    ADD COLUMN recording_url VARCHAR(500) DEFAULT NULL COMMENT '录音访问URL';

-- 4. 录音时长（秒）
ALTER TABLE ai_call_record
    ADD COLUMN record_duration INT DEFAULT 0 COMMENT '录音时长(秒)';

-- 5. ASR 转写状态：0 待转写 1 转写中 2 已完成 3 失败
ALTER TABLE ai_call_record
    ADD COLUMN asr_status TINYINT DEFAULT 0 COMMENT 'ASR转写状态 0待转写 1转写中 2已完成 3失败';

-- 6. ASR 转写文本
ALTER TABLE ai_call_record
    ADD COLUMN transcript TEXT COMMENT 'ASR转写文本';

-- 7. 为 call_uuid 增加索引，加速 ESL 事件回写时的关联查询
CREATE INDEX idx_ai_call_record_call_uuid ON ai_call_record(call_uuid);
