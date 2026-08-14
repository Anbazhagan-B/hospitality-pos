-- Insert default permissions
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (1, 'READ_EMPLOYEES', 'Read employee data', 'employees', 'read', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (2, 'WRITE_EMPLOYEES', 'Create/Update employee data', 'employees', 'write', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (3, 'DELETE_EMPLOYEES', 'Delete employee data', 'employees', 'delete', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (4, 'MANAGE_ROLES', 'Manage roles and permissions', 'roles', 'manage', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (5, 'READ_CHECKS', 'Read check data', 'checks', 'read', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (6, 'WRITE_CHECKS', 'Create/Update check data', 'checks', 'write', NOW(), NOW(), 0);
INSERT INTO permissions (id, name, description, resource, action, created_at, updated_at, version)
VALUES (7, 'PROCESS_PAYMENTS', 'Process payments', 'payments', 'process', NOW(), NOW(), 0);

-- Insert default roles
INSERT INTO roles (id, name, description, created_at, updated_at, version)
VALUES (1, 'ADMIN', 'Administrator with full access', NOW(), NOW(), 0);
INSERT INTO roles (id, name, description, created_at, updated_at, version)
VALUES (2, 'MANAGER', 'Manager with limited administrative access', NOW(), NOW(), 0);
INSERT INTO roles (id, name, description, created_at, updated_at, version)
VALUES (3, 'CASHIER', 'Cashier with POS access', NOW(), NOW(), 0);
INSERT INTO roles (id, name, description, created_at, updated_at, version)
VALUES (4, 'KITCHEN', 'Kitchen staff with order display access', NOW(), NOW(), 0);

-- Assign permissions to roles
-- Admin has all permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 1);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 2);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 3);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 4);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 5);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 6);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 7);

-- Manager permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 1);
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 2);
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 5);
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 6);
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 7);

-- Cashier permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES (3, 5);
INSERT INTO role_permissions (role_id, permission_id) VALUES (3, 6);
INSERT INTO role_permissions (role_id, permission_id) VALUES (3, 7);

-- Kitchen permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES (4, 5);

-- Insert default admin user (password: admin123)
INSERT INTO employees (id, username, password, first_name, last_name, email, phone, active, organization_id, created_at, updated_at, version)
VALUES (1, 'admin', '$2a$10$rDkPvvAFV8kqwvKJzwlhMOaVnRaU2U8aSBF7yfPzRxZLl8vCdqDWi', 'System', 'Administrator', 'admin@pos.com', '555-0100', true, 1, NOW(), NOW(), 0);

-- Assign admin role to admin user
INSERT INTO employee_roles (employee_id, role_id) VALUES (1, 1);
