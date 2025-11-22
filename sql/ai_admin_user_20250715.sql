USE `ai-law`;

-- 创建普通用户（用户类型为'01'）
INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark) 
VALUES (101, 103, 'user01', '普通用户01', '01', 'user01@example.com', '13800138001', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NOW(), NOW(), 'admin', NOW(), '', NULL, '普通用户01');

-- 分配用户角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (101, 2);