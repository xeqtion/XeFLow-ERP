-- =====================================================================
-- Finance notification test data
-- Prerequisite: sql/90_test_permission_data.sql has created sales_alice,
-- customer 910101 and product 910201. This script ensures finance_test exists.
-- After loading this file, use sales_alice / DemoOnly-ChangeMe1! to:
--   1. Confirm PI 920501 to generate the finance deposit notification.
--   2. Generate shipment from PI 920502 to generate the PO payment notification.
-- Then sign in as finance_test / DemoOnly-ChangeMe1! and open Message Center -> Notifications.
-- =====================================================================
USE `erp`;
SET NAMES utf8mb4;

SET @pwd_demo = '$2b$12$WlgDY8JCZYpF5kd3opGbmuQoK1fSXjUmn.VWcsCdxiOeylrV/ZB/K';
INSERT INTO `sys_user`
  (`id`,`username`,`password`,`real_name`,`nickname`,`email`,`phone`,`status`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (910004,'finance_test',@pwd_demo,'财务测试','Finance','finance_test@example.com','13800001004',1,
   '财务通知测试账号',1,NOW(),1,NOW(),0)
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`), `status`=1, `deleted`=0,
  `real_name`=VALUES(`real_name`), `nickname`=VALUES(`nickname`);
INSERT IGNORE INTO `sys_user_role` (`user_id`,`role_id`)
SELECT 910004, `id` FROM `sys_role` WHERE `role_key` = 'finance';

DELETE FROM `sys_notification`
WHERE `group_key` IN ('finance-deposit-pi-920501', 'finance-payable-pi-920502');
DELETE sm
FROM `stock_movement` sm
JOIN `shipment` s ON s.`shipment_no` = sm.`related_doc_no`
WHERE s.`pi_id` BETWEEN 920500 AND 920599;
DELETE si
FROM `shipment_item` si
JOIN `shipment` s ON s.`id` = si.`shipment_id`
WHERE s.`pi_id` BETWEEN 920500 AND 920599;
DELETE FROM `shipment` WHERE `pi_id` BETWEEN 920500 AND 920599;
DELETE FROM `stock_movement` WHERE `id` BETWEEN 920700 AND 920799;
DELETE FROM `finance_entry` WHERE `id` BETWEEN 920800 AND 920899;
DELETE FROM `purchase_order_item` WHERE `po_id` BETWEEN 920900 AND 920999;
DELETE FROM `purchase_order` WHERE `id` BETWEEN 920900 AND 920999;
DELETE FROM `sales_pi_item` WHERE `pi_id` BETWEEN 920500 AND 920599;
DELETE FROM `sales_pi` WHERE `id` BETWEEN 920500 AND 920599;

-- Case A: confirming this PI should notify finance to collect the 300 USD deposit.
INSERT INTO `sales_pi`
  (`id`,`owner_id`,`pi_no`,`customer_id`,`currency`,`total_amount`,`deposit_amount`,`status`,`sign_date`,`payment_method`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920501,910001,'PI-NOTIFY-DEP-001',910101,'USD',1000.0000,300.0000,'DRAFT',CURDATE(),'T/T 30% deposit','财务通知测试：确认后提醒收定金',910001,NOW(),910001,NOW(),0);
INSERT INTO `sales_pi_item`
  (`id`,`pi_id`,`product_id`,`prod_code`,`product_name`,`product_model`,`factory_model`,`unit`,`qty`,`unit_price`,`amount`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920601,920501,910201,'TST-LAMP-A','LED Table Lamp A','XF-TL-A','FAC-TL-A','PCS',10.0000,100.0000,1000.0000,910001,NOW(),910001,NOW(),0);

-- Case B: this PI is already paid. Its available stock lets the business user create a full shipment.
-- The related confirmed PO remains unpaid, so full shipment/delivery should notify finance to pay it.
INSERT INTO `sales_pi`
  (`id`,`owner_id`,`pi_no`,`customer_id`,`currency`,`total_amount`,`deposit_amount`,`status`,`sign_date`,`payment_method`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920502,910001,'PI-NOTIFY-PO-001',910101,'USD',1000.0000,0.0000,'ORDERED',CURDATE(),'T/T before shipment','财务通知测试：交货后提醒支付 PO',910001,NOW(),910001,NOW(),0);
INSERT INTO `sales_pi_item`
  (`id`,`pi_id`,`product_id`,`prod_code`,`product_name`,`product_model`,`factory_model`,`unit`,`qty`,`unit_price`,`amount`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920602,920502,910201,'TST-LAMP-A','LED Table Lamp A','XF-TL-A','FAC-TL-A','PCS',10.0000,100.0000,1000.0000,910001,NOW(),910001,NOW(),0);
INSERT INTO `finance_entry`
  (`id`,`entry_no`,`type`,`direction`,`customer_id`,`amount`,`currency`,`related_doc_type`,`related_doc_no`,`entry_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920801,'SK-NOTIFY-001','RECEIVABLE','in',910101,1000.0000,'USD','PI','PI-NOTIFY-PO-001',CURDATE(),'财务通知测试：PI 已收全款',910004,NOW(),910004,NOW(),0);
INSERT INTO `purchase_order`
  (`id`,`po_no`,`pi_id`,`pi_no`,`purchaser_id`,`supplier`,`currency`,`total_amount`,`status`,`order_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920901,'PO-NOTIFY-001',920502,'PI-NOTIFY-PO-001',910001,'Notification Demo Factory','CNY',600.0000,'CONFIRMED',CURDATE(),'财务通知测试：交货后待付款',910001,NOW(),910001,NOW(),0);
INSERT INTO `purchase_order_item`
  (`id`,`po_id`,`product_id`,`prod_code`,`product_name`,`qty`,`received_qty`,`unit_price`,`amount`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920911,920901,910201,'TST-LAMP-A','LED Table Lamp A',10.0000,10.0000,60.0000,600.0000,910001,NOW(),910001,NOW(),0);
INSERT INTO `stock_movement`
  (`id`,`prod_code`,`product_id`,`product_name`,`direction`,`qty`,`biz_type`,`related_doc_no`,`move_date`,`remark`,`create_by`,`create_time`,`update_by`,`update_time`,`deleted`)
VALUES
  (920701,'TST-LAMP-A',910201,'LED Table Lamp A','IN',10.0000,'PURCHASE_IN','PO-NOTIFY-001',CURDATE(),'财务通知测试：可用于生成出货单的库存',910001,NOW(),910001,NOW(),0);
