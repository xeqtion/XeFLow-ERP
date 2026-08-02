USE `erp`;

-- Permission/data-scope test data for the XeFlow open-source edition.
-- Password for all test users: DemoOnly-ChangeMe1!
-- Users:
--   sales_alice / DemoOnly-ChangeMe1!  -> salesman, owns Alice test data only
--   sales_bob   / DemoOnly-ChangeMe1!  -> salesman, owns Bob test data only
--   sales_mgr   / DemoOnly-ChangeMe1!  -> sales_manager, can view all sales data and approve sales quotes

SET @pwd_demo = '$2b$12$WlgDY8JCZYpF5kd3opGbmuQoK1fSXjUmn.VWcsCdxiOeylrV/ZB/K';

-- Make role permissions match the intended test rule.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'salesman'
  AND p.perm_key IN ('contract_price:approve', 'data:all');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.role_key = 'sales_manager'
  AND p.perm_key IN ('contract_price:approve', 'data:all');

-- Clean old test data.
DELETE FROM user_message
WHERE sender_id BETWEEN 910001 AND 910099
   OR recipient_id BETWEEN 910001 AND 910099;
DELETE FROM sys_notification WHERE recipient_id BETWEEN 910001 AND 910099;
DELETE FROM personal_task WHERE owner_id BETWEEN 910001 AND 910099;
DELETE FROM sales_pi_item WHERE id BETWEEN 910600 AND 910699;
DELETE FROM sales_pi WHERE id BETWEEN 910500 AND 910599;
DELETE FROM contract_price WHERE id BETWEEN 910400 AND 910499;
DELETE FROM standard_price WHERE id BETWEEN 910300 AND 910399;
DELETE FROM order_oem_file WHERE oem_id BETWEEN 910700 AND 910799;
DELETE FROM order_oem WHERE id BETWEEN 910700 AND 910799;
DELETE FROM customer_extra_field WHERE customer_id BETWEEN 910100 AND 910199;
DELETE FROM customer WHERE id BETWEEN 910100 AND 910199;
DELETE FROM product WHERE id BETWEEN 910200 AND 910299;
DELETE FROM sys_user_role WHERE user_id BETWEEN 910001 AND 910099;
DELETE FROM sys_user WHERE id BETWEEN 910001 AND 910099;

-- Users.
INSERT INTO sys_user
  (id, username, password, real_name, nickname, email, phone, status, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910001, 'sales_alice', @pwd_demo, '业务员 Alice', 'Alice', 'sales_alice@example.com', '13800001001', 1, '权限测试: 业务员, 只能看自己的客户/订单/业绩', 1, NOW(), 1, NOW(), 0),
  (910002, 'sales_bob',   @pwd_demo, '业务员 Bob',   'Bob',   'sales_bob@example.com',   '13800001002', 1, '权限测试: 业务员, 只能看自己的客户/订单/业绩', 1, NOW(), 1, NOW(), 0),
  (910003, 'sales_mgr',   @pwd_demo, '销售主管 Mary', 'Mary',  'sales_mgr@example.com',   '13800001003', 1, '权限测试: 销售主管, 查看全部销售数据并审核销售报价', 1, NOW(), 1, NOW(), 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 910001, id FROM sys_role WHERE role_key = 'salesman'
UNION ALL
SELECT 910002, id FROM sys_role WHERE role_key = 'salesman'
UNION ALL
SELECT 910003, id FROM sys_role WHERE role_key = 'sales_manager';

-- Products and standard prices.
INSERT INTO product
  (id, prod_code, product_name, company_model, factory_model, factory_price, prod_name_cn, prod_name_en, unit, cost_price, currency, safety_stock, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910201, 'TST-LAMP-A', 'LED Table Lamp A', 'XF-TL-A', 'FAC-TL-A', 12.0000, 'LED台灯A', 'LED Table Lamp A', 'PCS', 12.0000, 'CNY', 100, '权限测试产品', 1, NOW(), 1, NOW(), 0),
  (910202, 'TST-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', 'FAC-PL-B', 28.0000, 'LED吊灯B', 'LED Pendant Lamp B', 'PCS', 28.0000, 'CNY', 80, '权限测试产品', 1, NOW(), 1, NOW(), 0),
  (910203, 'TST-LAMP-C', 'LED Wall Lamp C', 'XF-WL-C', 'FAC-WL-C', 18.0000, 'LED壁灯C', 'LED Wall Lamp C', 'PCS', 18.0000, 'CNY', 60, '权限测试产品', 1, NOW(), 1, NOW(), 0);

INSERT INTO standard_price
  (id, product_id, prod_code, product_name, company_model, customer_id, price, standard_unit_price, discount_unit_price, floor_price, currency, effective_date, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910301, 910201, 'TST-LAMP-A', 'LED Table Lamp A', 'XF-TL-A', NULL, 24.0000, 24.0000, 22.0000, 20.0000, 'USD', '2026-07-01', '权限测试标准价', 1, NOW(), 1, NOW(), 0),
  (910302, 910202, 'TST-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', NULL, 48.0000, 48.0000, 45.0000, 42.0000, 'USD', '2026-07-01', '权限测试标准价', 1, NOW(), 1, NOW(), 0),
  (910303, 910203, 'TST-LAMP-C', 'LED Wall Lamp C', 'XF-WL-C', NULL, 32.0000, 32.0000, 30.0000, 28.0000, 'USD', '2026-07-01', '权限测试标准价', 1, NOW(), 1, NOW(), 0);

-- Customers owned by different salespeople.
INSERT INTO customer
  (id, owner_id, pool_type, owner_assigned_at, cust_code, contact_name, contact_position, country, company_name, address, currency, credit_limit, website, phone, email, social_contact, remark, cust_name_cn, cust_name_en, contact, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910101, 910001, 'PERSONAL', NOW(), 'US9101', 'Emma Stone', 'Purchasing Manager', 'US', 'Alice Lighting LLC', '100 Market Street, Los Angeles, CA', 'USD', 50000.00, 'https://alice-lighting.example.com', '+1-213-555-0101', 'emma@alice-lighting.example.com', 'WhatsApp +1-213-555-0101', 'Alice 业务员客户', 'Alice照明有限公司', 'Alice Lighting LLC', 'Emma Stone', 910001, NOW(), 910001, NOW(), 0),
  (910102, 910001, 'PERSONAL', NOW(), 'DE9102', 'Lukas Meyer', 'Buyer', 'DE', 'Nord Licht GmbH', 'Alexanderplatz 12, Berlin', 'EUR', 42000.00, 'https://nord-licht.example.com', '+49-30-555-0102', 'lukas@nord-licht.example.com', 'LinkedIn Lukas Meyer', 'Alice 业务员客户', '北方照明贸易', 'Nord Licht GmbH', 'Lukas Meyer', 910001, NOW(), 910001, NOW(), 0),
  (910103, 910002, 'PERSONAL', NOW(), 'GB9103', 'Oliver Green', 'Director', 'GB', 'Bob Home Lights Ltd', '221B Baker Street, London', 'GBP', 38000.00, 'https://bob-home-lights.example.com', '+44-20-555-0103', 'oliver@bob-home-lights.example.com', 'WhatsApp +44-20-555-0103', 'Bob 业务员客户', 'Bob家居灯饰', 'Bob Home Lights Ltd', 'Oliver Green', 910002, NOW(), 910002, NOW(), 0),
  (910104, 910002, 'PERSONAL', NOW(), 'AU9104', 'Mia Wilson', 'Sourcing Lead', 'AU', 'Southern Star Lighting Pty', '88 Harbour Road, Sydney', 'USD', 46000.00, 'https://southern-star.example.com', '+61-2-555-0104', 'mia@southern-star.example.com', 'Teams Mia Wilson', 'Bob 业务员客户', '南星照明', 'Southern Star Lighting Pty', 'Mia Wilson', 910002, NOW(), 910002, NOW(), 0);

-- Sales quotes: PASS, PENDING, REJECTED examples. PENDING rows are for manager approval tests.
INSERT INTO contract_price
  (id, customer_id, customer_name, product_id, product_name, product_model, prod_code, standard_price, contract_price, deviation, currency, audit_status, auditor_id, audit_time, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910401, 910101, 'Alice Lighting LLC',       910201, 'LED Table Lamp A',   'XF-TL-A', 'TST-LAMP-A', 24.0000, 23.0000, -0.041667, 'USD', 'PASS',    NULL,    NOW(), 'Alice 成交价在折扣区间内, 自动通过',       910001, NOW(), 910001, NOW(), 0),
  (910402, 910102, 'Nord Licht GmbH',          910202, 'LED Pendant Lamp B', 'XF-PL-B', 'TST-LAMP-B', 48.0000, 50.0000,  0.041667, 'USD', 'PENDING', NULL,    NULL,  'Alice 高于标准价, 等待销售主管审核',       910001, NOW(), 910001, NOW(), 0),
  (910403, 910103, 'Bob Home Lights Ltd',      910203, 'LED Wall Lamp C',    'XF-WL-C', 'TST-LAMP-C', 32.0000, 29.5000, -0.078125, 'USD', 'PENDING', NULL,    NULL,  'Bob 低于折扣价但高于底价, 等待主管审核', 910002, NOW(), 910002, NOW(), 0),
  (910404, 910104, 'Southern Star Lighting Pty', 910202, 'LED Pendant Lamp B', 'XF-PL-B', 'TST-LAMP-B', 48.0000, 41.0000, -0.145833, 'USD', 'REJECTED', 910003, NOW(), 'Bob 低于底价, 用于驳回测试',             910002, NOW(), 910003, NOW(), 0);

-- Sales PI orders. These drive sales reports and salesman performance.
INSERT INTO sales_pi
  (id, owner_id, pi_no, customer_id, currency, total_amount, status, sign_date, payment_method, trade_term, delivery_date, dest_port, shipping_marks, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910501, 910001, 'PI-TST-A-202607-001', 910101, 'USD', 2300.0000, 'CONFIRMED', '2026-07-02', 'T/T 30% deposit, 70% before shipment', 'FOB Ningbo', '2026-08-15', 'Los Angeles', 'ALICE-9101', 'Alice 业务员订单', 910001, NOW(), 910001, NOW(), 0),
  (910502, 910001, 'PI-TST-A-202607-002', 910102, 'USD', 5000.0000, 'DRAFT',     '2026-07-03', 'T/T 100% before shipment',            'EXW',        '2026-08-20', 'Hamburg',     'NORD-9102',  'Alice 业务员订单', 910001, NOW(), 910001, NOW(), 0),
  (910503, 910002, 'PI-TST-B-202607-001', 910103, 'USD', 2950.0000, 'CONFIRMED', '2026-07-04', 'T/T 30% deposit, 70% before shipment', 'FOB Ningbo', '2026-08-25', 'London',      'BOB-9103',   'Bob 业务员订单',   910002, NOW(), 910002, NOW(), 0),
  (910504, 910002, 'PI-TST-B-202607-002', 910104, 'USD', 4100.0000, 'DRAFT',     '2026-07-05', 'L/C at sight',                         'CIF',        '2026-09-01', 'Sydney',      'STAR-9104',  'Bob 业务员订单',   910002, NOW(), 910002, NOW(), 0);

INSERT INTO sales_pi_item
  (id, pi_id, product_id, prod_code, product_name, product_model, factory_model, unit, quote_id, quote_source, standard_price, discount_unit_price, floor_price, qty, unit_price, amount, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910601, 910501, 910201, 'TST-LAMP-A', 'LED Table Lamp A',   'XF-TL-A', 'FAC-TL-A', 'PCS', 910401, 'SALES_QUOTE', 24.0000, 22.0000, 20.0000, 100.0000, 23.0000, 2300.0000, 'Alice PI 明细', 910001, NOW(), 910001, NOW(), 0),
  (910602, 910502, 910202, 'TST-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', 'FAC-PL-B', 'PCS', 910402, 'SALES_QUOTE', 48.0000, 45.0000, 42.0000, 100.0000, 50.0000, 5000.0000, 'Alice PI 明细', 910001, NOW(), 910001, NOW(), 0),
  (910603, 910503, 910203, 'TST-LAMP-C', 'LED Wall Lamp C',    'XF-WL-C', 'FAC-WL-C', 'PCS', 910403, 'SALES_QUOTE', 32.0000, 30.0000, 28.0000, 100.0000, 29.5000, 2950.0000, 'Bob PI 明细',   910002, NOW(), 910002, NOW(), 0),
  (910604, 910504, 910202, 'TST-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', 'FAC-PL-B', 'PCS', 910404, 'SALES_QUOTE', 48.0000, 45.0000, 42.0000, 100.0000, 41.0000, 4100.0000, 'Bob PI 明细',   910002, NOW(), 910002, NOW(), 0);

-- OEM order examples, also owner-scoped.
INSERT INTO order_oem
  (id, owner_id, oem_no, pi_id, pi_no, customer_id, customer_code, customer_name, product_id, product_model, factory_model, requirement, status, create_by, create_time, update_by, update_time, deleted)
VALUES
  (910701, 910001, 'OEM20260702-0001', 910501, 'PI-TST-A-202607-001', 910101, 'US9101', 'Alice Lighting LLC',          910201, 'XF-TL-A', 'FAC-TL-A', 'Alice 客户要求白盒彩盒, 贴客户条码。', 'DRAFT',     910001, NOW(), 910001, NOW(), 0),
  (910702, 910001, 'OEM20260702-0002', 910502, 'PI-TST-A-202607-002', 910102, 'DE9102', 'Nord Licht GmbH',             910202, 'XF-PL-B', 'FAC-PL-B', 'Alice 客户要求德文说明书。',           'CONFIRMED', 910001, NOW(), 910001, NOW(), 0),
  (910703, 910002, 'OEM20260702-0003', 910503, 'PI-TST-B-202607-001', 910103, 'GB9103', 'Bob Home Lights Ltd',         910203, 'XF-WL-C', 'FAC-WL-C', 'Bob 客户要求英规插头。',              'DRAFT',     910002, NOW(), 910002, NOW(), 0),
  (910704, 910002, 'OEM20260702-0004', 910504, 'PI-TST-B-202607-002', 910104, 'AU9104', 'Southern Star Lighting Pty',  910202, 'XF-PL-B', 'FAC-PL-B', 'Bob 客户要求澳规认证标识。',          'CONFIRMED', 910002, NOW(), 910002, NOW(), 0);
