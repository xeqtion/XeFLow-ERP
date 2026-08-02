-- Shipment is a fulfillment result: only partial-shipped or fully-shipped status is displayed.
USE `erp`;
SET NAMES utf8mb4;

UPDATE `shipment`
SET `status` = CASE
  WHEN `status` IN ('FULL_SHIPPED', 'COMPLETED') THEN 'FULL_SHIPPED'
  ELSE 'PARTIAL_SHIPPED'
END
WHERE `status` NOT IN ('PARTIAL_SHIPPED', 'FULL_SHIPPED');
