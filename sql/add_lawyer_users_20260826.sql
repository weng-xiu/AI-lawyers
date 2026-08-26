-- 为 4 名已存在坐席记录但缺失系统账号的律师创建 sys_user，并绑定普通角色
-- 默认密码：123456（BCrypt）

INSERT INTO sys_user
  (user_id, dept_id, user_name, nick_name, user_type, lawyer_flag, law_firm, specialty,
   agent_id, call_mode, password, status, del_flag, sex, create_by, create_time, remark)
VALUES
  (1001, 100, 'zhangls',  '张律师', '00', '1', '12348法律服务中心', '婚姻家事、离婚财产分割、子女抚养', 102, '0',
   '$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy', '0', '0', '0', 'admin', NOW(), '婚姻家事坐席'),
  (1002, 100, 'lils',     '李律师', '00', '1', '12348法律服务中心', '婚姻家事、遗产继承、财产公证', 103, '0',
   '$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy', '0', '0', '1', 'admin', NOW(), '婚姻家事坐席'),
  (1003, 100, 'wangls',   '王律师', '00', '1', '12348法律服务中心', '合同纠纷、买卖合同、债务债权', 104, '0',
   '$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy', '0', '0', '0', 'admin', NOW(), '合同纠纷坐席'),
  (1004, 100, 'zhaols',   '赵律师', '00', '1', '12348法律服务中心', '合同纠纷、劳动合同、建筑工程', 105, '0',
   '$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy', '0', '0', '1', 'admin', NOW(), '合同纠纷坐席');

-- 绑定普通角色（role_id=2 普通角色）
INSERT INTO sys_user_role (user_id, role_id) VALUES
  (1001, 2), (1002, 2), (1003, 2), (1004, 2);

-- 绑定普通员工岗位（post_id=4）
INSERT INTO sys_user_post (user_id, post_id) VALUES
  (1001, 4), (1002, 4), (1003, 4), (1004, 4);
