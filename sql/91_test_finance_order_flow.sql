-- Manual local test data for the PI -> deposit -> PO -> SI -> full payment -> shipment flow.
-- Run after 39_finance_order_flow.sql and 90_test_permission_data.sql.
USE `erp`;
SET NAMES utf8mb4;

SET @pwd_demo = '$2b$12$WlgDY8JCZYpF5kd3opGbmuQoK1fSXjUmn.VWcsCdxiOeylrV/ZB/K';

DELETE FROM `sys_user_role` WHERE `user_id` = 910004;
DELETE FROM `sys_user` WHERE `id` = 910004;
INSERT INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `nickname`, `email`, `phone`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
  (910004, 'finance_test', @pwd_demo, '财务测试', 'Finance', 'finance_test@example.com', '13800001004', 1,
   '财务流程测试账号，仅拥有财务维护及 PI/单据追踪只读权限', 1, NOW(), 1, NOW(), 0);
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 910004, `id` FROM `sys_role` WHERE `role_key` = 'finance';

DELETE FROM `shipment` WHERE `id` BETWEEN 911300 AND 911399;
DELETE FROM `shipping_instruction` WHERE `id` BETWEEN 911200 AND 911299;
DELETE FROM `purchase_order_item` WHERE `id` BETWEEN 911100 AND 911199;
DELETE FROM `purchase_order` WHERE `id` BETWEEN 911000 AND 911099;
DELETE FROM `finance_entry` WHERE `id` BETWEEN 911400 AND 911499;
DELETE FROM `stock_movement` WHERE `id` BETWEEN 911500 AND 911599;
DELETE FROM `stock_movement` WHERE `related_doc_no` IN ('PO-FLOW-202607-001', 'PO-FLOW-202607-002');
DELETE FROM `finance_entry`
WHERE `related_doc_type` = 'PI'
  AND `related_doc_no` IN ('PI-TST-A-202607-001', 'PI-TST-A-202607-002', 'PI-TST-B-202607-002');

-- Flow A: fully paid, shipment created and confirmed.
UPDATE `sales_pi` SET `deposit_amount` = 690.0000, `status` = 'ORDERED' WHERE `id` = 910501;
INSERT INTO `finance_entry`
  (`id`,`entry_no`,`type`,`direction`,`customer_id`,`amount`,`currency`,`related_doc_type`,`related_doc_no`,`entry_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911401,'SK20260706-92034','PRE_RECEIVE','in',910101,690.0000,'USD','PI','PI-TST-A-202607-001','2026-07-06','测试：收到 30% 定金',910004,NOW(),910004,NOW(),0),
  (911402,'SK20260710-92035','RECEIVABLE','in',910101,1610.0000,'USD','PI','PI-TST-A-202607-001','2026-07-10','测试：收到剩余 70% 全款',910004,NOW(),910004,NOW(),0);
INSERT INTO `purchase_order`
  (`id`,`po_no`,`pi_id`,`pi_no`,`purchaser_id`,`supplier`,`currency`,`total_amount`,`status`,`order_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911001,'PO-FLOW-202607-001',910501,'PI-TST-A-202607-001',910001,'Demo Lighting Factory','CNY',1200.0000,'IN_PROGRESS','2026-07-07','测试：已部分到货，可先部分出货',910001,NOW(),910001,NOW(),0);
INSERT INTO `purchase_order_item`
  (`id`,`po_id`,`product_id`,`prod_code`,`product_name`,`qty`,`received_qty`,`unit_price`,`amount`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911101,911001,910201,'TST-LAMP-A','LED Table Lamp A',100.0000,30.0000,12.0000,1200.0000,910001,NOW(),910001,NOW(),0);
INSERT INTO `stock_movement`
  (`id`,`prod_code`,`product_id`,`product_name`,`direction`,`qty`,`biz_type`,`related_doc_no`,`move_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911501,'TST-LAMP-A',910201,'LED Table Lamp A','IN',30.0000,'PURCHASE_IN','PO-FLOW-202607-001','2026-07-08','测试：PO首批到货入库 30 PCS',910001,NOW(),910001,NOW(),0);
INSERT INTO `shipping_instruction`
  (`id`,`si_no`,`customer_id`,`pi_id`,`ship_date`,`shipping_address`,`delivery_address`,`contact_name`,`phone`,`order_no`,`order_country`,`product_model`,`factory_model`,`quantity`,`carton_count`,`order_requirement`,`owner_name`,`order_date`,`port`,`status`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911201,'SI-FLOW-202607-001',910101,910501,'2026-07-12','Ningbo warehouse','100 Market Street, Los Angeles, CA','Emma Stone','+1-213-555-0101','PI-TST-A-202607-001','US','XF-TL-A','FAC-TL-A',100,100,'按 PI 要求出货','Alice','2026-07-02','Los Angeles','CONFIRMED','测试：已收全款待出货',910001,NOW(),910001,NOW(),0);
INSERT INTO `shipment`
  (`id`,`shipment_no`,`si_id`,`pi_id`,`ship_date`,`qty`,`package_count`,`status`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911301,'SHP-FLOW-202607-001',911201,910501,'2026-07-12',100.0000,100,'CONFIRMED','测试：已确认，下一步可改为已交货',910001,NOW(),910001,NOW(),0);
DELETE FROM `shipment` WHERE `id` = 911301;

-- Flow B: deposit received, PI auto-confirmed, remaining balance blocks shipment creation.
UPDATE `sales_pi` SET `deposit_amount` = 1500.0000, `status` = 'ORDERED' WHERE `id` = 910502;
INSERT INTO `finance_entry`
  (`id`,`entry_no`,`type`,`direction`,`customer_id`,`amount`,`currency`,`related_doc_type`,`related_doc_no`,`entry_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911403,'SK20260708-92036','PRE_RECEIVE','in',910102,1500.0000,'USD','PI','PI-TST-A-202607-002','2026-07-08','测试：定金已收，尚欠 3500 USD',910004,NOW(),910004,NOW(),0);
INSERT INTO `purchase_order`
  (`id`,`po_no`,`pi_id`,`pi_no`,`purchaser_id`,`supplier`,`currency`,`total_amount`,`status`,`order_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911002,'PO-FLOW-202607-002',910502,'PI-TST-A-202607-002',910004,'Demo Metal Factory','CNY',2800.0000,'DRAFT','2026-07-09','测试：仅收定金的采购单',910001,NOW(),910001,NOW(),0);
INSERT INTO `purchase_order_item`
  (`id`,`po_id`,`product_id`,`prod_code`,`product_name`,`qty`,`received_qty`,`unit_price`,`amount`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911102,911002,910202,'TST-LAMP-B','LED Pendant Lamp B',100.0000,0.0000,28.0000,2800.0000,910001,NOW(),910001,NOW(),0);
INSERT INTO `shipping_instruction`
  (`id`,`si_no`,`customer_id`,`pi_id`,`ship_date`,`shipping_address`,`delivery_address`,`contact_name`,`phone`,`order_no`,`order_country`,`product_model`,`factory_model`,`quantity`,`carton_count`,`order_requirement`,`owner_name`,`order_date`,`port`,`status`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (911202,'SI-FLOW-202607-002',910102,910502,'2026-07-18','Ningbo warehouse','Alexanderplatz 12, Berlin','Lukas Meyer','+49-30-555-0102','PI-TST-A-202607-002','DE','XF-PL-B','FAC-PL-B',100,100,'收全款后方可生成出货单','Alice','2026-07-03','Hamburg','DRAFT','测试：出货单应被全款校验拦截',910001,NOW(),910001,NOW(),0);

-- Flow C: no receipt yet; use the receivable dialog to register a 1230 USD deposit and verify auto-confirm.
UPDATE `sales_pi` SET `deposit_amount` = 1230.0000, `status` = 'DRAFT' WHERE `id` = 910504;
