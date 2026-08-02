-- 产品ID改为6位起的短数字: 100000, 100001, ...
-- 仅迁移不在短ID范围内的产品记录, 并同步所有 product_id 引用。

SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

SET @next_product_id := (
  SELECT GREATEST(COALESCE(MAX(`id`), 99999), 99999)
    FROM `product`
   WHERE `id` >= 100000
     AND `id` < 1000000000
);

CREATE TEMPORARY TABLE `tmp_product_id_map` AS
SELECT `old_id`, (@next_product_id := @next_product_id + 1) AS `new_id`
  FROM (
        SELECT `id` AS `old_id`
          FROM `product`
         WHERE `id` < 100000 OR `id` >= 1000000000
         ORDER BY `id`
       ) p;

UPDATE `bom` b
  JOIN `tmp_product_id_map` m ON b.`product_id` = m.`old_id`
   SET b.`product_id` = m.`new_id`;

UPDATE `contract_price` cp
  JOIN `tmp_product_id_map` m ON cp.`product_id` = m.`old_id`
   SET cp.`product_id` = m.`new_id`;

UPDATE `order_oem` o
  JOIN `tmp_product_id_map` m ON o.`product_id` = m.`old_id`
   SET o.`product_id` = m.`new_id`;

UPDATE `purchase_order_item` poi
  JOIN `tmp_product_id_map` m ON poi.`product_id` = m.`old_id`
   SET poi.`product_id` = m.`new_id`;

UPDATE `sales_pi_item` spi
  JOIN `tmp_product_id_map` m ON spi.`product_id` = m.`old_id`
   SET spi.`product_id` = m.`new_id`;

UPDATE `standard_price` sp
  JOIN `tmp_product_id_map` m ON sp.`product_id` = m.`old_id`
   SET sp.`product_id` = m.`new_id`;

UPDATE `stock_movement` sm
  JOIN `tmp_product_id_map` m ON sm.`product_id` = m.`old_id`
   SET sm.`product_id` = m.`new_id`;

UPDATE `product` p
  JOIN `tmp_product_id_map` m ON p.`id` = m.`old_id`
   SET p.`id` = m.`new_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_product_id_map`;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
