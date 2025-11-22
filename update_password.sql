USE ai-law;
UPDATE sys_user SET password = '$2a$10$7JB720yubVSOfvVWbfXCOOxjTOQcQjmrJF1ZM4nAVccp/.rkMlDW' WHERE user_name = 'testuser01';
SELECT user_id, user_name, password FROM sys_user WHERE user_name = 'testuser01';