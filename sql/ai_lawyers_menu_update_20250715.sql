-- AI律师系统菜单和权限配置更新
-- 创建日期：2025-07-15

-- 1. 为法律知识库添加导入和审核按钮权限
INSERT INTO sys_menu VALUES('3206', '知识导入', '3200', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:import', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3207', '知识审核', '3200', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:audit', '#', 'admin', sysdate(), '', null, '');

-- 2. 为超级管理员角色分配新增的菜单权限
INSERT INTO sys_role_menu VALUES (1, 3206);
INSERT INTO sys_role_menu VALUES (1, 3207);

-- 3. 添加审核状态字典类型
INSERT INTO sys_dict_type VALUES('100', '审核状态', 'lawyers_audit_status', '0', 'admin', sysdate(), '', null, '审核状态列表');

-- 4. 添加审核状态字典数据
INSERT INTO sys_dict_data VALUES('1000', 1, '待审核', '0', 'lawyers_audit_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '待审核状态');
INSERT INTO sys_dict_data VALUES('1001', 2, '审核通过', '1', 'lawyers_audit_status', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '审核通过状态');
INSERT INTO sys_dict_data VALUES('1002', 3, '审核不通过', '2', 'lawyers_audit_status', '', 'danger', 'N', '0', 'admin', sysdate(), '', null, '审核不通过状态');