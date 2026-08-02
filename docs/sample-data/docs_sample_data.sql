-- =====================================================================
-- ERP documentation sample data.
-- ---------------------------------------------------------------------
-- Local screenshot/demo use only. Do not include this file in formal
-- database initialization and do not run it against trial/production data.
-- Load manually:
--   mysql -u root -proot --default-character-set=utf8mb4 -D erp -e "source docs/sample-data/docs_sample_data.sql"
-- =====================================================================

USE `erp`;
SET NAMES utf8mb4;

SET @admin_id := 1;
SET @alice_id := 910001;
SET @bob_id := 910002;
SET @mgr_id := 910003;
SET @pwd_demo := '$2b$12$WlgDY8JCZYpF5kd3opGbmuQoK1fSXjUmn.VWcsCdxiOeylrV/ZB/K';

-- Clean previous documentation sample data.
DELETE FROM user_message WHERE sender_id BETWEEN 910001 AND 910099 OR recipient_id BETWEEN 910001 AND 910099;
DELETE FROM sys_notification WHERE recipient_id BETWEEN 910001 AND 910099 OR id BETWEEN 990000 AND 990999;
DELETE FROM personal_task WHERE owner_id BETWEEN 910001 AND 910099 OR id BETWEEN 990000 AND 990999;
DELETE FROM finance_entry WHERE id BETWEEN 990000 AND 990999;
DELETE FROM shipment WHERE id BETWEEN 990000 AND 990999;
DELETE FROM shipping_instruction WHERE id BETWEEN 990000 AND 990999;
DELETE FROM purchase_order_item WHERE id BETWEEN 990000 AND 990999;
DELETE FROM purchase_order WHERE id BETWEEN 990000 AND 990999;
DELETE FROM order_oem_file WHERE oem_id BETWEEN 990000 AND 990999;
DELETE FROM order_oem WHERE id BETWEEN 990000 AND 990999;
DELETE FROM sales_pi_item WHERE id BETWEEN 990000 AND 990999;
DELETE FROM sales_pi WHERE id BETWEEN 990000 AND 990999;
DELETE FROM contract_price WHERE id BETWEEN 990000 AND 990999;
DELETE FROM standard_price WHERE id BETWEEN 990000 AND 990999;
DELETE FROM stock_movement WHERE id BETWEEN 990000 AND 990999;
DELETE FROM bom_item WHERE id BETWEEN 990000 AND 990999;
DELETE FROM bom WHERE id BETWEEN 990000 AND 990999;
DELETE FROM product WHERE id BETWEEN 990000 AND 990999;
DELETE FROM customer_extra_field WHERE customer_id BETWEEN 990000 AND 990999;
DELETE FROM customer WHERE id BETWEEN 990000 AND 990999;
DELETE FROM shared_file WHERE id BETWEEN 990000 AND 990999;
DELETE FROM sys_user_role WHERE user_id BETWEEN 910001 AND 910099;
DELETE FROM sys_user WHERE id BETWEEN 910001 AND 910099;

-- Users for role documentation.
INSERT INTO sys_user
  (id, username, password, real_name, nickname, email, phone, status, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (@alice_id, 'sales_alice', @pwd_demo, '业务员 Alice', 'Alice', 'sales_alice@example.com', '13800001001', 1, '文档演示: 业务员', @admin_id, NOW(), @admin_id, NOW(), 0),
  (@bob_id, 'sales_bob', @pwd_demo, '业务员 Bob', 'Bob', 'sales_bob@example.com', '13800001002', 1, '文档演示: 业务员', @admin_id, NOW(), @admin_id, NOW(), 0),
  (@mgr_id, 'sales_mgr', @pwd_demo, '销售主管 Mary', 'Mary', 'sales_mgr@example.com', '13800001003', 1, '文档演示: 销售主管', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT @alice_id, id FROM sys_role WHERE role_key = 'salesman'
UNION ALL SELECT @bob_id, id FROM sys_role WHERE role_key = 'salesman'
UNION ALL SELECT @mgr_id, id FROM sys_role WHERE role_key = 'sales_manager';

-- Collaboration.
INSERT INTO personal_task
  (id, owner_id, title, content, task_date, priority, status, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990001, @admin_id, '审核销售报价', '确认 PI-DOC-202607-001 的折扣报价', CURDATE(), 'HIGH', 'PENDING', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990002, @admin_id, '跟进出货指示', '检查洛杉矶客户出货地址和装柜信息', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'NORMAL', 'PENDING', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO sys_notification
  (id, recipient_id, title, content, type, group_key, is_read, create_by, create_time)
VALUES
  (990010, @admin_id, '销售报价待审核', 'Nord Licht GmbH 的销售报价等待销售主管审核。', 'QUOTE_APPROVAL', 'docs-demo', 0, @alice_id, NOW()),
  (990011, @mgr_id, '销售报价待审核', 'Bob Home Lights Ltd 的销售报价等待销售主管审核。', 'QUOTE_APPROVAL', 'docs-demo', 0, @bob_id, NOW());

INSERT INTO user_message
  (id, sender_id, sender_name, recipient_id, recipient_name, content, is_read, sender_deleted, recipient_deleted, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990020, @alice_id, '业务员 Alice', @mgr_id, '销售主管 Mary', 'Mary，PI-DOC-202607-001 的报价已提交，请帮忙审核。', 0, 0, 0, @alice_id, NOW(), @alice_id, NOW(), 0),
  (990021, @mgr_id, '销售主管 Mary', @alice_id, '业务员 Alice', '收到，我会结合标准价和客户信用额度确认。', 1, 0, 0, @mgr_id, NOW(), @mgr_id, NOW(), 0);

INSERT INTO shared_file
  (id, file_name, stored_name, content_type, file_format, size, category, uploader_name, remark, expire_time, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990030, '客户资料收集模板.xlsx', 'docs-customer-template.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'XLSX', 18432, '销售资料', 'admin', '文档演示文件, 无真实业务数据', DATE_ADD(NOW(), INTERVAL 90 DAY), @admin_id, NOW(), @admin_id, NOW(), 0),
  (990031, '出货指示样例.pdf', 'docs-shipping-sample.pdf', 'application/pdf', 'PDF', 96512, '出货资料', 'admin', '文档演示文件, 无真实业务数据', DATE_ADD(NOW(), INTERVAL 30 DAY), @admin_id, NOW(), @admin_id, NOW(), 0);

-- Master data.
INSERT INTO customer
  (id, owner_id, cust_code, contact_name, contact_position, country, company_name, address, currency, credit_limit, website, phone, email, social_contact, remark, cust_name_cn, cust_name_en, contact, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990101, @alice_id, 'US9901', 'Emma Stone', 'Purchasing Manager', 'US', 'Alice Lighting LLC', '100 Market Street, Los Angeles, CA', 'USD', 50000.00, 'https://alice-lighting.example.com', '+1-213-555-0101', 'emma@alice-lighting.example.com', 'WhatsApp +1-213-555-0101', '文档演示客户', 'Alice 照明有限公司', 'Alice Lighting LLC', 'Emma Stone', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990102, @alice_id, 'DE9902', 'Lukas Meyer', 'Buyer', 'DE', 'Nord Licht GmbH', 'Alexanderplatz 12, Berlin', 'EUR', 42000.00, 'https://nord-licht.example.com', '+49-30-555-0102', 'lukas@nord-licht.example.com', 'LinkedIn Lukas Meyer', '文档演示客户', '北方照明贸易', 'Nord Licht GmbH', 'Lukas Meyer', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990103, @bob_id, 'GB9903', 'Oliver Green', 'Director', 'GB', 'Bob Home Lights Ltd', '221B Baker Street, London', 'GBP', 38000.00, 'https://bob-home-lights.example.com', '+44-20-555-0103', 'oliver@bob-home-lights.example.com', 'WhatsApp +44-20-555-0103', '文档演示客户', 'Bob 家居灯饰', 'Bob Home Lights Ltd', 'Oliver Green', @bob_id, NOW(), @bob_id, NOW(), 0);

INSERT INTO customer_extra_field
  (id, customer_id, field_key, field_value, sort, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990111, 990101, '银行账户', 'Demo Bank **** 0101', 1, @alice_id, NOW(), @alice_id, NOW(), 0),
  (990112, 990101, 'VAT No.', 'US-DEMO-9901', 2, @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO product
  (id, prod_code, product_name, company_model, factory_model, factory_price, prod_name_cn, prod_name_en, unit, cost_price, currency, safety_stock, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990201, 'DOC-LAMP-A', 'LED Table Lamp A', 'XF-TL-A', 'FAC-TL-A', 12.0000, 'LED 台灯 A', 'LED Table Lamp A', 'PCS', 12.0000, 'CNY', 100, '文档演示产品', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990202, 'DOC-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', 'FAC-PL-B', 28.0000, 'LED 吊灯 B', 'LED Pendant Lamp B', 'PCS', 28.0000, 'CNY', 80, '文档演示产品', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990203, 'DOC-LAMP-C', 'LED Wall Lamp C', 'XF-WL-C', 'FAC-WL-C', 18.0000, 'LED 壁灯 C', 'LED Wall Lamp C', 'PCS', 18.0000, 'CNY', 60, '文档演示产品', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO bom
  (id, bom_code, product_id, prod_code, name_cn, name_en, version, support_models, universal, spec, qty, supplier, supplier_phone, supplier_contact, unit_price, currency, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990301, 'MT-DOC-001', 990201, 'DOC-LAMP-A', '电源适配器', 'Power Adapter', 'V1', 'XF-TL-A,XF-PL-B', 0, '24V/2A', 1.0000, 'Demo Power Supplier', '+86-755-5555-0101', 'Chen', 18.5000, 'CNY', '文档演示配件', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO standard_price
  (id, product_id, prod_code, product_name, company_model, customer_id, price, standard_unit_price, discount_unit_price, floor_price, currency, effective_date, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990401, 990201, 'DOC-LAMP-A', 'LED Table Lamp A', 'XF-TL-A', NULL, 24.0000, 24.0000, 22.0000, 20.0000, 'USD', '2026-07-01', '文档演示标准价', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990402, 990202, 'DOC-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', NULL, 48.0000, 48.0000, 45.0000, 42.0000, 'USD', '2026-07-01', '文档演示标准价', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO contract_price
  (id, customer_id, customer_name, product_id, product_name, product_model, prod_code, standard_price, contract_price, deviation, currency, audit_status, auditor_id, audit_time, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990501, 990101, 'Alice Lighting LLC', 990201, 'LED Table Lamp A', 'XF-TL-A', 'DOC-LAMP-A', 24.0000, 23.0000, -0.041667, 'USD', 'PASS', NULL, NOW(), '自动通过报价', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990502, 990102, 'Nord Licht GmbH', 990202, 'LED Pendant Lamp B', 'XF-PL-B', 'DOC-LAMP-B', 48.0000, 50.0000, 0.041667, 'USD', 'PENDING', NULL, NULL, '等待销售主管审核', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990503, 990103, 'Bob Home Lights Ltd', 990203, 'LED Wall Lamp C', 'XF-WL-C', 'DOC-LAMP-C', 32.0000, 29.5000, -0.078125, 'USD', 'PENDING', NULL, NULL, '等待销售主管审核', @bob_id, NOW(), @bob_id, NOW(), 0);

INSERT INTO sales_pi
  (id, owner_id, pi_no, customer_id, currency, total_amount, status, sign_date, payment_method, trade_term, delivery_date, dest_port, shipping_marks, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990601, @alice_id, 'PI-DOC-202607-001', 990101, 'USD', 2300.0000, 'CONFIRMED', '2026-07-02', 'T/T 30% deposit, 70% before shipment', 'FOB Ningbo', '2026-08-15', 'Los Angeles', 'ALICE-9901', '文档演示 PI', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990602, @alice_id, 'PI-DOC-202607-002', 990102, 'USD', 5000.0000, 'DRAFT', '2026-07-03', 'T/T 100% before shipment', 'EXW', '2026-08-20', 'Hamburg', 'NORD-9902', '文档演示 PI', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990603, @bob_id, 'PI-DOC-202607-003', 990103, 'USD', 2950.0000, 'CONFIRMED', '2026-07-04', 'T/T 30% deposit, 70% before shipment', 'FOB Ningbo', '2026-08-25', 'London', 'BOB-9903', '文档演示 PI', @bob_id, NOW(), @bob_id, NOW(), 0);

INSERT INTO sales_pi_item
  (id, pi_id, product_id, prod_code, product_name, product_model, factory_model, unit, quote_id, quote_source, standard_price, discount_unit_price, floor_price, qty, unit_price, amount, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990611, 990601, 990201, 'DOC-LAMP-A', 'LED Table Lamp A', 'XF-TL-A', 'FAC-TL-A', 'PCS', 990501, 'SALES_QUOTE', 24.0000, 22.0000, 20.0000, 100.0000, 23.0000, 2300.0000, '文档演示明细', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990612, 990602, 990202, 'DOC-LAMP-B', 'LED Pendant Lamp B', 'XF-PL-B', 'FAC-PL-B', 'PCS', 990502, 'SALES_QUOTE', 48.0000, 45.0000, 42.0000, 100.0000, 50.0000, 5000.0000, '文档演示明细', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990613, 990603, 990203, 'DOC-LAMP-C', 'LED Wall Lamp C', 'XF-WL-C', 'FAC-WL-C', 'PCS', 990503, 'SALES_QUOTE', 32.0000, 30.0000, 28.0000, 100.0000, 29.5000, 2950.0000, '文档演示明细', @bob_id, NOW(), @bob_id, NOW(), 0);

INSERT INTO order_oem
  (id, owner_id, oem_no, pi_id, pi_no, customer_id, customer_code, customer_name, product_id, product_model, factory_model, requirement, status, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990701, @alice_id, 'OEM20260702-9901', 990601, 'PI-DOC-202607-001', 990101, 'US9901', 'Alice Lighting LLC', 990201, 'XF-TL-A', 'FAC-TL-A', '白盒彩盒, 贴客户条码, 英文说明书。', 'DRAFT', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990702, @alice_id, 'OEM20260702-9902', 990602, 'PI-DOC-202607-002', 990102, 'DE9902', 'Nord Licht GmbH', 990202, 'XF-PL-B', 'FAC-PL-B', '德文说明书, 外箱贴 EU 标签。', 'CONFIRMED', @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO purchase_order
  (id, po_no, pi_id, pi_no, supplier, currency, total_amount, status, order_date, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990801, 'PO-DOC-202607-001', 990601, 'PI-DOC-202607-001', 'Template Supplier Ltd.', 'USD', 1250.0000, '已入库', '2026-07-06', '文档演示采购订单', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990802, 'PO-DOC-202607-002', 990602, 'PI-DOC-202607-002', 'Demo Metal Factory', 'USD', 2800.0000, '草稿', '2026-07-07', '文档演示采购订单', @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO purchase_order_item
  (id, po_id, product_id, prod_code, qty, unit_price, amount, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990811, 990801, 990201, 'DOC-LAMP-A', 100.0000, 12.5000, 1250.0000, @alice_id, NOW(), @alice_id, NOW(), 0),
  (990812, 990802, 990202, 'DOC-LAMP-B', 100.0000, 28.0000, 2800.0000, @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO shipping_instruction
  (id, si_no, customer_id, pi_id, ship_date, shipping_address, delivery_address, contact_name, phone, order_no, order_country, product_model, factory_model, quantity, carton_count, order_requirement, owner_name, order_date, port, status, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990901, 'SI20260706-9901', 990101, 990601, '2026-08-15', '宁波仓库 3 号门', '100 Market Street, Los Angeles, CA', 'Emma Stone', '+1-213-555-0101', 'PI-DOC-202607-001', 'United States', 'XF-TL-A', 'FAC-TL-A', 100.0000, 100.0000, '按 PI 明细出货, 外箱贴客户唛头。', '业务员 Alice', '2026-07-02', 'Los Angeles', 'DRAFT', '文档演示出货指示', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990902, 'SI20260706-9902', 990102, 990602, '2026-08-20', '义乌集货仓 A 区', 'Alexanderplatz 12, Berlin', 'Lukas Meyer', '+49-30-555-0102', 'PI-DOC-202607-002', 'Germany', 'XF-PL-B', 'FAC-PL-B', 100.0000, 100.0000, '德文说明书随货, 注意易碎标识。', '业务员 Alice', '2026-07-03', 'Hamburg', 'DRAFT', '文档演示出货指示', @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO shipment
  (id, shipment_no, si_id, pi_id, ship_date, qty, package_count, gross_weight, net_weight, volume_cbm, container_type, container_no, bl_no, status, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990951, 'SHP20260706-9901', 990901, 990601, '2026-08-15', 100.0000, 100, 1260.500, 1188.000, 15.680, '40HQ', 'XFHU9900101', 'BL-XF-99001', 'SHIPPED', '文档演示出货单', @alice_id, NOW(), @alice_id, NOW(), 0),
  (990952, 'SHP20260706-9902', 990902, 990602, '2026-08-20', 100.0000, 100, 168.200, 152.400, 2.360, '20GP', 'XFHU9900201', 'BL-XF-99002', 'DRAFT', '文档演示出货单', @alice_id, NOW(), @alice_id, NOW(), 0);

INSERT INTO finance_entry
  (id, entry_no, type, direction, customer_id, amount, currency, related_doc_type, related_doc_no, entry_date, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990971, 'SK20260706-99001', 'RECEIVABLE', 'in', 990101, 690.0000, 'USD', 'PI', 'PI-DOC-202607-001', '2026-07-06', '30% deposit received', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990972, 'SK20260810-99002', 'RECEIVABLE', 'in', 990101, 1610.0000, 'USD', 'PI', 'PI-DOC-202607-001', '2026-08-10', 'Balance before shipment', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990973, 'FK20260708-99003', 'PAYABLE', 'out', NULL, 1250.0000, 'USD', 'PO', 'PO-DOC-202607-001', '2026-07-08', 'Supplier payment', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990974, 'FK20260709-99004', 'PAYABLE', 'out', NULL, 1400.0000, 'USD', 'PO', 'PO-DOC-202607-002', '2026-07-09', 'Supplier deposit', @admin_id, NOW(), @admin_id, NOW(), 0);

INSERT INTO stock_movement
  (id, prod_code, product_id, direction, qty, biz_type, related_doc_no, move_date, remark, create_by, create_time, update_by, update_time, deleted)
VALUES
  (990981, 'DOC-LAMP-A', 990201, 'IN', 100.0000, 'PURCHASE_IN', 'PO-DOC-202607-001', '2026-07-20', '文档演示采购入库', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990982, 'DOC-LAMP-A', 990201, 'OUT', 60.0000, 'SALE_OUT', 'SHP20260706-9901', '2026-08-15', '文档演示销售出库', @admin_id, NOW(), @admin_id, NOW(), 0),
  (990983, 'DOC-LAMP-B', 990202, 'IN', 100.0000, 'PURCHASE_IN', 'PO-DOC-202607-002', '2026-07-21', '文档演示采购入库', @admin_id, NOW(), @admin_id, NOW(), 0);
