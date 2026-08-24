-- ============================================================
-- sys_user 增加律师扩展字段
-- 律师与系统用户统一建模：不新建律师表，直接在 sys_user 上扩展，
-- 通过 lawyer_flag='1' 标记律师身份，律师也可同时拥有坐席分机号。
-- 适用于 MySQL 5.7+ / 8.x
-- 注：user_type 字段在若依初始化脚本 ry_20250522.sql 中已存在，此处不再新增。
-- ============================================================

ALTER TABLE `sys_user`
    ADD COLUMN `lawyer_flag`     char(1)      DEFAULT '0'  COMMENT '是否为律师（0否 1是）' AFTER `user_type`,
    ADD COLUMN `lawyer_license`  varchar(50)  DEFAULT NULL COMMENT '执业证号' AFTER `lawyer_flag`,
    ADD COLUMN `law_firm`        varchar(200) DEFAULT NULL COMMENT '所属律所' AFTER `lawyer_license`,
    ADD COLUMN `specialty`       varchar(500) DEFAULT NULL COMMENT '专业领域（多个用逗号分隔）' AFTER `law_firm`,
    ADD COLUMN `practice_years`  int(11)      DEFAULT NULL COMMENT '执业年限' AFTER `specialty`,
    ADD COLUMN `lawyer_intro`    text         DEFAULT NULL COMMENT '律师简介' AFTER `practice_years`;
