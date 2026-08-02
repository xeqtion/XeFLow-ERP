-- 客户联系电话可能包含多个号码、分机及说明，扩容以兼容批量导入。
ALTER TABLE `customer`
  MODIFY COLUMN `phone` VARCHAR(255) DEFAULT NULL COMMENT '联系电话';
