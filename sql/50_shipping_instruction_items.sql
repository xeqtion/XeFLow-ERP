CREATE TABLE IF NOT EXISTS `shipping_instruction_item` (
  `id`            BIGINT        NOT NULL,
  `si_id`         BIGINT        NOT NULL COMMENT '所属出货指示',
  `product_model` VARCHAR(512)           DEFAULT NULL COMMENT '产品型号',
  `factory_model` VARCHAR(512)           DEFAULT NULL COMMENT '工厂型号',
  `quantity`      DECIMAL(18,4)          DEFAULT NULL COMMENT '数量',
  `carton_count`  DECIMAL(18,4)          DEFAULT NULL COMMENT '箱数',
  `sort_order`    INT           NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `create_by`     BIGINT                 DEFAULT NULL,
  `create_time`   DATETIME               DEFAULT NULL,
  `update_by`     BIGINT                 DEFAULT NULL,
  `update_time`   DATETIME               DEFAULT NULL,
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_shipping_instruction_item_si` (`si_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货指示产品明细';

-- 历史出货指示不强制回填：详情接口会按原主表字段或关联 PI 明细兼容回显，
-- 用户下一次保存时再固化为独立的出货指示产品快照。
