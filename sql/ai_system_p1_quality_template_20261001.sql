-- =====================================================================
-- P1-7（V2.60）：质检模板自定义化——维度/权重/评分 prompt 可配
-- 日期：2026-10-01
--
-- 维度限定在系统已知四个 key：serviceNorm/answerAccuracy/emotionAttitude/compliance
-- （规则引擎只能评 serviceNorm/compliance；自定义 prompt 也须返回这些 key），
-- 但可对四维度做删减/调权；score_prompt 留空则用代码内置默认 prompt。
-- 仅一条 is_default='1' 且 status='0' 的模板生效。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_quality_template (
    template_id   BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_name VARCHAR(64)  NOT NULL COMMENT '模板名称',
    dimensions    VARCHAR(500) NOT NULL COMMENT '维度权重JSON：[{"key":"serviceNorm","name":"服务规范","weight":1.0}]',
    score_prompt  TEXT         NULL COMMENT '自定义评分系统提示词（留空用内置默认）',
    is_default    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '1默认生效模板（仅一条）',
    status        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    create_by     VARCHAR(64)  NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64)  NULL,
    update_time   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检评分模板（P1-7 维度/权重/prompt 自定义）';

-- 默认模板 seed（幂等）：四维等权
INSERT INTO ai_quality_template
    (template_name, dimensions, is_default, status, create_by)
SELECT '默认热线质检模板',
    '[{"key":"serviceNorm","name":"服务规范","weight":1.0},{"key":"answerAccuracy","name":"答复准确","weight":1.0},{"key":"emotionAttitude","name":"情绪态度","weight":1.0},{"key":"compliance","name":"合规话术","weight":1.0}]',
    '1', '0', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_quality_template WHERE is_default = '1');
