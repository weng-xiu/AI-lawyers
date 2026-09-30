-- ============================================================
-- P3-G1-b 字段级 PII 加密（V2.55）：来电人档案 SM4-GCM 密文化 + 盲索引
-- 创建日期：2026-09-30
--
-- 背景：ai_caller_profile.caller_number / caller_id_card 由明文改为
--       SM4-GCM 国密密文（应用层 encp: 前缀），等值查询改走 HMAC-SM3
--       盲索引列；原 caller_number 唯一键随密文化失效（随机 IV），唯一
--       防重语义转移至盲索引唯一键（同号同索引，"一号一档"保持）。
--
-- 执行顺序：本 DDL → 部署新版本 → 管理员调用
--           POST /lawyers/pii/migrateCallerProfile（幂等可重跑）
--           存量明文在迁移前经应用层三态解密兼容继续可读。
-- 回滚：仅列扩长与新增列，不影响旧版本读（旧版本按明文查将查不到，
--       回滚需先确认存量已按明文备份或尚未执行迁移）。
-- ============================================================

-- 1. 列扩长（承载密文）+ 盲索引列 + 索引重建
ALTER TABLE ai_caller_profile
    MODIFY COLUMN caller_number  VARCHAR(128) NOT NULL COMMENT '来电号码（SM4-GCM 密文 encp: 前缀，G1-b）',
    MODIFY COLUMN caller_id_card VARCHAR(128) DEFAULT NULL COMMENT '身份证号（SM4-GCM 密文 encp: 前缀，G1-b）',
    ADD COLUMN caller_number_index  VARCHAR(32) DEFAULT NULL COMMENT '来电号码盲索引（HMAC-SM3 截断 128 位 hex，G1-b）' AFTER caller_number,
    ADD COLUMN caller_id_card_index VARCHAR(32) DEFAULT NULL COMMENT '身份证号盲索引（HMAC-SM3 截断 128 位 hex，G1-b）' AFTER caller_id_card,
    DROP KEY uk_ai_caller_profile_number,
    ADD UNIQUE KEY uk_ai_caller_profile_number_index (caller_number_index),
    ADD KEY idx_ai_caller_profile_id_card_index (caller_id_card_index);

-- 2. 运维接口按钮权限：挂"来电弹屏"菜单（3644）下，自动分配菜单 ID 避免冲突
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_by, create_time, remark)
VALUES ('PII加密迁移', '3644', '3', '', '', '', '',
        1, 0, 'F', '0', '0', 'lawyers:pii:crypt', '#',
        'admin', sysdate(), 'G1-b PII 存量加密迁移');
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE perms = 'lawyers:pii:crypt' AND menu_name = 'PII加密迁移';
