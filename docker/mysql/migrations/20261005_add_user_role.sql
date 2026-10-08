-- Existing deployments: protect system administration endpoints with a server-side role.
ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN' AFTER points;

-- Grant deliberately, for example:
-- UPDATE sys_user SET role = 'ADMIN' WHERE account = 'your-admin-account';
