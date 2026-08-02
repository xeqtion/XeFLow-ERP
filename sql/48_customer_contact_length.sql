-- Keep the legacy contact snapshot aligned with contact_name.
ALTER TABLE `customer`
  MODIFY COLUMN `contact` VARCHAR(128) DEFAULT NULL COMMENT '旧联系人';
