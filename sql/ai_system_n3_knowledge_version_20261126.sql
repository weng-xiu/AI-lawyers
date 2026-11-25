-- =====================================================================
-- N3 语料版本治理：失效法条不得召回 + 效力变更全程留痕（语雀《下一步开发计划V1.2》7.5 N3）
-- 1) ai_knowledge_audit.action 由 VARCHAR(10) 扩到 VARCHAR(20)，容纳新动作 VALID_STATUS
-- 2) 知识审核动作字典追加「效力变更 VALID_STATUS」
-- 幂等：可重复执行；列宽扩容用 INFORMATION_SCHEMA 判断，字典按 dict_type+dict_value 去重
-- 日期：2026-11-26
-- =====================================================================

-- 1. 扩展 action 列长度（MySQL 8 动态 DDL，仅当当前长度不足 20 时执行）
SET @ddl := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'ai_knowledge_audit'
              AND COLUMN_NAME = 'action'
              AND CHARACTER_MAXIMUM_LENGTH < 20
        ),
        'ALTER TABLE ai_knowledge_audit MODIFY COLUMN action VARCHAR(20) NOT NULL COMMENT ''动作 SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE/VALID_STATUS''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 字典数据（按 dict_type + dict_value 幂等，dict_sort=6 接续 OFFLINE=5）
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
                           is_default, status, create_by, create_time, remark)
SELECT 6 AS a, '效力变更' AS b, 'VALID_STATUS' AS c, 'ai_knowledge_audit_action' AS d,
       '' AS e, 'primary' AS f, 'N' AS g, '0' AS h, 'admin' AS i, NOW() AS j,
       'N3 法规效力状态变更（现行有效/已修订/已失效）留痕' AS k
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
    WHERE d.dict_type = 'ai_knowledge_audit_action' AND d.dict_value = 'VALID_STATUS'
);
