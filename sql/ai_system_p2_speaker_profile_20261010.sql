-- ============================================================
-- P2-15 声纹聚类中心表（表结构设计先行，应用层暂不写入）
-- 创建日期：2026-10-10
--
-- 背景：说话人分离分两步落地（见调研报告 6.2-E/6.3-#15）：
--   ① 通话内说话人分离（SpeakerDiarizationService）：仅质检分角色，
--      不跨通话、不建身份档案，结果只在内存返回，合规风险低——已先行落地；
--   ② 跨通话"一号一档"声纹建档：声纹属《个人信息保护法》生物识别敏感
--      个人信息，须单独同意 + 个人信息保护影响评估（PIA），法务评估通过
--      前应用层不得写入本表。本 DDL 仅做表结构储备。
--
-- 合规与安全约定：
--   - 不存号码明文/密文，仅以 ai_caller_profile 同款号码盲索引关联
--     （HMAC-SM3 截断 128 位 hex，等值关联不泄露号码）；
--   - 中心向量为 L2 归一后的 float32 向量（CAM++ 16k，维度 192），
--     以 BLOB 存原始 little-endian 字节，不引入向量库依赖；
--   - profile_status：0 草稿/待确认 1 生效（已取得单独同意+PIA 通过）
--     2 撤销/失效（用户撤回同意等，停止参与任何匹配）；
--   - 不支持删除明细审计另由 del_flag + update_by/update_time 留痕。
--
-- 执行顺序：本 DDL 可先行执行（仅建表，无任何读写流量）。
-- 回滚：DROP TABLE ai_speaker_profile;（无数据时无影响）
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_speaker_profile (
    profile_id        BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '声纹档案ID',
    caller_number_index VARCHAR(32) DEFAULT NULL COMMENT '来电号码盲索引（HMAC-SM3 截断128位hex，关联 ai_caller_profile.caller_number_index，不存号码）',
    embedding_model   VARCHAR(64)   NOT NULL DEFAULT 'campplus_zh_16k' COMMENT '声纹模型标识（CAM++ 16k 中文，ONNX 导出版本）',
    embedding_dim     INT(11)       NOT NULL DEFAULT 192 COMMENT '中心向量维度',
    centroid_vector   BLOB          DEFAULT NULL COMMENT '聚类中心向量（L2 归一 float32 小端字节，维度与 embedding_dim 一致）',
    confidence        DECIMAL(5,4)  DEFAULT NULL COMMENT '建档置信度（0~1，各样本对中心平均余弦）',
    sample_count      INT(11)       NOT NULL DEFAULT 0 COMMENT '参与建中心的语音段数',
    profile_status    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态（0草稿待确认 1生效 2撤销/失效）',
    consent_time      DATETIME      DEFAULT NULL COMMENT '单独同意取得时间（状态置1前必须非空）',
    pia_ref           VARCHAR(128)  DEFAULT NULL COMMENT '个人信息保护影响评估（PIA）归档编号',
    last_match_time   DATETIME      DEFAULT NULL COMMENT '最近一次跨通话匹配时间',
    create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
    remark            VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    del_flag          CHAR(1)       DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_ai_speaker_profile_number_model (caller_number_index, embedding_model),
    KEY idx_ai_speaker_profile_status (profile_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹聚类中心表（P2-15 设计先行；跨通话建档待 PIA/法务评估，评估通过前禁止应用层写入）';
