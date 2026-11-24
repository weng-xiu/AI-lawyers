-- F9 SLA 看板接口冒烟测试数据（临时，验证后由 sla_board_smoke_cleanup.sql 删除）
-- 统一 ticket_no 前缀 SLASMK，record_id 用负数避免与真实通话冲突
-- 覆盖：已办结准时2、已办结超时1、进行中超期1、进行中临近超时1、进行中正常1

INSERT INTO ai_call_ticket
(ticket_id, ticket_no, record_id, title, content, priority, status, overtime_flag,
 create_time, due_time, close_time, assign_user_id, assign_user_name, create_by, external_type)
VALUES
(900101,'SLASMK-001',-900101,'SLA冒烟-已办结准时','准时办结工单','2','2', 0,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 2 DAY), INTERVAL 48 HOUR),
 DATE_SUB(NOW(), INTERVAL 1 DAY), 1,'管理员','admin', NULL),
(900102,'SLASMK-002',-900102,'SLA冒烟-条线法援准时','法律援助条线','2','3', 0,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 24 HOUR),
 DATE_SUB(NOW(), INTERVAL 2 DAY), 1,'管理员','admin', 'LEGAL_AID'),
(900103,'SLASMK-003',-900103,'SLA冒烟-已办结超时','办结晚于due','1','2', 1,
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 4 DAY), INTERVAL 12 HOUR),
 DATE_SUB(NOW(), INTERVAL 3 DAY), 1,'管理员','admin', NULL),
(900104,'SLASMK-004',-900104,'SLA冒烟-进行中已超期','due已过未结','1','1', 1,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL, 1,'管理员','admin', 'MEDIATION'),
(900105,'SLASMK-005',-900105,'SLA冒烟-临近超时','剩余30分钟','2','0', 0,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 MINUTE), NULL, 1,'管理员','admin', NULL),
(900106,'SLASMK-006',-900106,'SLA冒烟-进行中正常','时限充足','3','1', 0,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), NULL, NULL, NULL,'admin', 'NOTARY');

-- 转办流水：12345 OUT 2 笔（1 DONE 1 PENDING）、LEGAL_AID IN 1 DONE
INSERT INTO ai_ticket_transfer
(transfer_id, ticket_id, ticket_no, direction, external_type, external_ticket_no, external_status, transfer_status,
 idempotent_key, retry_count, request_payload, callback_payload, create_by, create_time, transfer_time, callback_time)
VALUES
(900201, 900104, 'SLASMK-004', 'OUT', 'HOTLINE_12345', 'EXT-SMK-001', 'DONE', 1,
 'SLASMK-TR-001', 0, '{}', '{}', 'admin', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(900202, 900102, 'SLASMK-002', 'OUT', 'HOTLINE_12345', 'EXT-SMK-002', 'PENDING', 0,
 'SLASMK-TR-002', 1, '{}', NULL, 'admin', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
(900203, 900102, 'SLASMK-002', 'IN', 'LEGAL_AID', 'EXT-SMK-003', 'DONE', 1,
 'SLASMK-TR-003', 0, '{}', '{}', 'admin', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));
