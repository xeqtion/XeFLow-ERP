-- =====================================================================
-- 外贸贸易管理 ERP 系统 - 数据库结构
-- 字符集: utf8mb4   引擎: InnoDB
-- 通用字段约定: id, create_by, create_time, update_by, update_time, deleted
-- 金额统一 DECIMAL(18,4) + 币种字段
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `erp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `erp`;

-- 确保会话以 utf8mb4 解析本文件 (避免 Windows 客户端默认 gbk 导致中文乱码)
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 用户域 (RBAC)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT       NOT NULL COMMENT '主键',
  `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `real_name`   VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
  `nickname`    VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
  `email`       VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
  `phone`       VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像',
  `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
  `create_by`   BIGINT                DEFAULT NULL,
  `create_time` DATETIME              DEFAULT NULL,
  `update_by`   BIGINT                DEFAULT NULL,
  `update_time` DATETIME              DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL,
  `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
  `role_key`    VARCHAR(64)  NOT NULL COMMENT '角色标识',
  `sort`        INT          NOT NULL DEFAULT 0,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `remark`      VARCHAR(255)          DEFAULT NULL,
  `create_by`   BIGINT                DEFAULT NULL,
  `create_time` DATETIME              DEFAULT NULL,
  `update_by`   BIGINT                DEFAULT NULL,
  `update_time` DATETIME              DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`          BIGINT       NOT NULL,
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父级ID 顶级为0',
  `perm_name`   VARCHAR(64)  NOT NULL COMMENT '名称',
  `perm_key`    VARCHAR(128)          DEFAULT NULL COMMENT '权限标识 模块:操作',
  `type`        CHAR(1)      NOT NULL DEFAULT 'C' COMMENT 'M目录 C菜单 B按钮',
  `path`        VARCHAR(128)          DEFAULT NULL COMMENT '前端路由',
  `component`   VARCHAR(128)          DEFAULT NULL COMMENT '前端组件',
  `icon`        VARCHAR(64)           DEFAULT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `create_by`   BIGINT                DEFAULT NULL,
  `create_time` DATETIME              DEFAULT NULL,
  `update_by`   BIGINT                DEFAULT NULL,
  `update_time` DATETIME              DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/菜单';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS `sys_invitation_code` (
  `id`          BIGINT       NOT NULL,
  `invite_code` VARCHAR(32)  NOT NULL COMMENT '系统自动生成的邀请码',
  `expire_time` DATETIME              DEFAULT NULL COMMENT '失效时间，NULL 表示永久有效',
  `max_uses`    INT          NOT NULL DEFAULT 1 COMMENT '最大可用次数',
  `used_count`  INT          NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `create_by`   BIGINT                DEFAULT NULL,
  `create_time` DATETIME              DEFAULT NULL,
  `update_by`   BIGINT                DEFAULT NULL,
  `update_time` DATETIME              DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_invite_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户注册邀请码';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `role_id`       BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`             BIGINT      NOT NULL,
  `user_id`        BIGINT               DEFAULT NULL,
  `username`       VARCHAR(64)          DEFAULT NULL,
  `module`         VARCHAR(64)          DEFAULT NULL,
  `operation`      VARCHAR(128)         DEFAULT NULL,
  `method`         VARCHAR(128)         DEFAULT NULL,
  `request_uri`    VARCHAR(255)         DEFAULT NULL,
  `request_method` VARCHAR(16)          DEFAULT NULL,
  `params`         TEXT,
  `success`        TINYINT              DEFAULT 1,
  `error_msg`      VARCHAR(512)         DEFAULT NULL,
  `ip`             VARCHAR(64)          DEFAULT NULL,
  `cost_ms`        BIGINT               DEFAULT NULL,
  `create_time`    DATETIME             DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ---------------------------------------------------------------------
-- 基础资料域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customer` (
  `id`               BIGINT       NOT NULL,
  `owner_id`         BIGINT                DEFAULT NULL COMMENT '当前跟单业务员/担当',
  `pool_type`        VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'PERSONAL个人发展客户池/PUBLIC公共发展客户池',
  `owner_assigned_at` DATETIME              DEFAULT NULL COMMENT '当前担当确认时间',
  `cust_code`        VARCHAR(64)  NOT NULL COMMENT '客户ID(业务主键, 按国家ISO alpha-2前缀自动生成)',
  `contact_name`     VARCHAR(128)          DEFAULT NULL COMMENT '联系人姓名',
  `contact_position` VARCHAR(128)          DEFAULT NULL COMMENT '职位',
  `country`          VARCHAR(64)           DEFAULT NULL COMMENT '国家',
  `company_name`     VARCHAR(255)          DEFAULT NULL COMMENT '公司名称',
  `address`          VARCHAR(512)          DEFAULT NULL COMMENT '地址',
  `currency`         VARCHAR(8)            DEFAULT 'USD',
  `website`          VARCHAR(255)          DEFAULT NULL COMMENT '网站',
  `phone`            VARCHAR(255)          DEFAULT NULL COMMENT '联系电话',
  `email`            VARCHAR(128)          DEFAULT NULL COMMENT '电子邮箱',
  `social_contact`   VARCHAR(255)          DEFAULT NULL COMMENT '社媒联系',
  `remark`           VARCHAR(512)          DEFAULT NULL COMMENT '备注',
  `cust_name_cn`     VARCHAR(255)          DEFAULT NULL COMMENT '旧客户中文名',
  `cust_name_en`     VARCHAR(255)          DEFAULT NULL COMMENT '旧客户英文名',
  `business_license` VARCHAR(128)          DEFAULT NULL COMMENT '营业执照号',
  `tax_no`           VARCHAR(128)          DEFAULT NULL COMMENT '税号',
  `bank_name`        VARCHAR(255)          DEFAULT NULL COMMENT '开户行',
  `bank_account`     VARCHAR(128)          DEFAULT NULL COMMENT '银行账户',
  `contact`          VARCHAR(128)          DEFAULT NULL COMMENT '旧联系人',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_code` (`cust_code`, `deleted`),
  KEY `idx_customer_pool_owner` (`pool_type`, `owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户资料';

CREATE TABLE IF NOT EXISTS `customer_extra_field` (
  `id`          BIGINT       NOT NULL,
  `customer_id` BIGINT       NOT NULL COMMENT '客户内部ID',
  `field_key`   VARCHAR(128) NOT NULL COMMENT '字段名',
  `field_value` VARCHAR(1024)         DEFAULT NULL COMMENT '字段值',
  `sort`        INT                   DEFAULT 0 COMMENT '排序',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_customer_extra_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户扩展键值资料';

CREATE TABLE IF NOT EXISTS `product` (
  `id`           BIGINT       NOT NULL COMMENT '产品ID(6位起自动生成)',
  `prod_code`    VARCHAR(64)  NOT NULL COMMENT '旧产品代码/编号',
  `product_name` VARCHAR(255)          DEFAULT NULL COMMENT '产品名称',
  `company_model` VARCHAR(255)         DEFAULT NULL COMMENT '公司产品型号',
  `factory_model` VARCHAR(255)         DEFAULT NULL COMMENT '工厂产品型号',
  `factory_price` DECIMAL(18,4)        DEFAULT NULL COMMENT '出厂价',
  `prod_name_cn` VARCHAR(255)          DEFAULT NULL COMMENT '旧中文名',
  `prod_name_en` VARCHAR(255)          DEFAULT NULL COMMENT '旧英文名',
  `spec`         VARCHAR(255)          DEFAULT NULL COMMENT '旧规格/型号',
  `unit`         VARCHAR(32)           DEFAULT NULL COMMENT '单位',
  `cost_price`   DECIMAL(18,4)         DEFAULT NULL COMMENT '旧生产成本价',
  `currency`     VARCHAR(8)            DEFAULT 'CNY',
  `remark`       VARCHAR(512)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prod_code` (`prod_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品资料';

CREATE TABLE IF NOT EXISTS `bom` (
  `id`               BIGINT       NOT NULL,
  `bom_code`         VARCHAR(64)  NOT NULL COMMENT '物料编码(MT0001)',
  `product_id`       BIGINT                DEFAULT NULL COMMENT '兼容旧数据: 关联产品id',
  `prod_code`        VARCHAR(64)           DEFAULT NULL COMMENT '兼容旧数据: 产品代码',
  `name_cn`          VARCHAR(255)          DEFAULT NULL COMMENT '名称',
  `name_en`          VARCHAR(255)          DEFAULT NULL COMMENT '兼容旧数据: 英文名',
  `version`          VARCHAR(32)           DEFAULT NULL COMMENT '兼容旧数据: 版本',
  `support_models`   VARCHAR(1024)         DEFAULT NULL COMMENT '支持产品型号(公司产品型号), 多个逗号分隔, 所有产品通用填所有',
  `universal`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否通用: 1是0否',
  `spec`             VARCHAR(255)          DEFAULT NULL COMMENT '规格',
  `qty`              DECIMAL(18,4)         DEFAULT NULL COMMENT '用量',
  `supplier`         VARCHAR(255)          DEFAULT NULL COMMENT '供应商',
  `supplier_phone`   VARCHAR(64)           DEFAULT NULL COMMENT '联系电话',
  `supplier_contact` VARCHAR(128)          DEFAULT NULL COMMENT '联系人',
  `unit_price`       DECIMAL(18,4)         DEFAULT NULL COMMENT '单价',
  `currency`         VARCHAR(8)            DEFAULT 'CNY' COMMENT '币种',
  `remark`           VARCHAR(512)          DEFAULT NULL,
  `create_by`        BIGINT      DEFAULT NULL,
  `create_time`      DATETIME    DEFAULT NULL,
  `update_by`        BIGINT      DEFAULT NULL,
  `update_time`      DATETIME    DEFAULT NULL,
  `deleted`          TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bom_code` (`bom_code`, `deleted`),
  KEY `idx_bom_support_models` (`support_models`(191)),
  KEY `idx_bom_universal` (`universal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品配件资料';

CREATE TABLE IF NOT EXISTS `bom_item` (
  `id`             BIGINT       NOT NULL,
  `bom_id`         BIGINT       NOT NULL,
  `material_model` VARCHAR(128)          DEFAULT NULL COMMENT '物料型号',
  `name_cn`        VARCHAR(255)          DEFAULT NULL,
  `name_en`        VARCHAR(255)          DEFAULT NULL,
  `qty`            DECIMAL(18,4)         DEFAULT NULL,
  `unit_price`     DECIMAL(18,4)         DEFAULT NULL,
  `currency`       VARCHAR(8)            DEFAULT 'CNY',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_bom` (`bom_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM 明细';

-- ---------------------------------------------------------------------
-- 销售域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `standard_price` (
  `id`                  BIGINT       NOT NULL,
  `product_id`          BIGINT       NOT NULL COMMENT '产品ID',
  `prod_code`           VARCHAR(64)           DEFAULT NULL COMMENT '兼容旧数据: 产品代码',
  `product_name`        VARCHAR(255)          DEFAULT NULL COMMENT '产品名称',
  `company_model`       VARCHAR(255)          DEFAULT NULL COMMENT '公司产品型号',
  `customer_id`         BIGINT                DEFAULT NULL COMMENT '兼容旧数据: 可空, 空为通用报价',
  `price`               DECIMAL(18,4) NOT NULL COMMENT '兼容旧数据: 系统单价/标准价',
  `standard_unit_price` DECIMAL(18,4) NOT NULL COMMENT '标准单价',
  `discount_unit_price` DECIMAL(18,4) NOT NULL COMMENT '折扣单价',
  `floor_price`         DECIMAL(18,4)          DEFAULT NULL COMMENT '底价, 默认折扣单价',
  `currency`            VARCHAR(8)            DEFAULT 'USD' COMMENT '币种',
  `effective_date`      DATE                  DEFAULT NULL COMMENT '兼容旧数据: 生效日期',
  `remark`              VARCHAR(512)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_standard_price_company_model` (`company_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品报价';

CREATE TABLE IF NOT EXISTS `contract_price` (
  `id`             BIGINT        NOT NULL,
  `customer_id`    BIGINT        NOT NULL,
  `customer_name`  VARCHAR(255)           DEFAULT NULL COMMENT '客户名称',
  `product_id`     BIGINT        NOT NULL,
  `product_name`   VARCHAR(255)           DEFAULT NULL COMMENT '产品名称',
  `product_model`  VARCHAR(255)           DEFAULT NULL COMMENT '产品型号',
  `prod_code`      VARCHAR(64)            DEFAULT NULL,
  `standard_price` DECIMAL(18,4)          DEFAULT NULL COMMENT '产品报价标准单价快照',
  `contract_price` DECIMAL(18,4) NOT NULL COMMENT '成交单价',
  `deviation`      DECIMAL(18,6)          DEFAULT NULL COMMENT '偏差率(基于标准价)',
  `currency`       VARCHAR(8)             DEFAULT 'USD',
  `audit_status`   VARCHAR(16)   NOT NULL DEFAULT 'PASS' COMMENT 'PASS通过 PENDING待审核 REJECTED驳回',
  `auditor_id`     BIGINT                 DEFAULT NULL,
  `audit_time`     DATETIME               DEFAULT NULL,
  `remark`         VARCHAR(512)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cust_prod` (`customer_id`, `product_id`),
  KEY `idx_contract_price_product_model` (`product_model`),
  KEY `idx_contract_price_customer_name` (`customer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售报价(含审核)';

CREATE TABLE IF NOT EXISTS `sales_pi` (
  `id`           BIGINT        NOT NULL,
  `pi_no`        VARCHAR(64)   NOT NULL COMMENT 'PI 单号',
  `customer_id`  BIGINT        NOT NULL,
  `currency`     VARCHAR(8)             DEFAULT 'USD',
  `total_amount` DECIMAL(18,4)          DEFAULT 0,
  `deposit_amount` DECIMAL(18,4)        NOT NULL DEFAULT 0 COMMENT 'PI contract deposit amount',
  `status`       VARCHAR(16)            DEFAULT 'DRAFT',
  `sign_date`    DATE                   DEFAULT NULL,
  `payment_method` VARCHAR(128)         DEFAULT NULL COMMENT '付款方式',
  `trade_term`     VARCHAR(128)         DEFAULT NULL COMMENT '贸易条款',
  `delivery_date`  DATE                 DEFAULT NULL COMMENT '交货日期',
  `dest_port`      VARCHAR(128)         DEFAULT NULL COMMENT '目的港',
  `shipping_marks` VARCHAR(512)         DEFAULT NULL COMMENT '唛头',
  `remark`       VARCHAR(512)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_no` (`pi_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售合同 PI 主表';

CREATE TABLE IF NOT EXISTS `sales_pi_item` (
  `id`         BIGINT        NOT NULL,
  `pi_id`      BIGINT        NOT NULL,
  `product_id` BIGINT                 DEFAULT NULL,
  `prod_code`  VARCHAR(64)            DEFAULT NULL,
  `product_name` VARCHAR(255)         DEFAULT NULL COMMENT '产品名称快照',
  `product_model` VARCHAR(255)        DEFAULT NULL COMMENT '产品型号快照',
  `factory_model` VARCHAR(255)        DEFAULT NULL COMMENT '工厂产品型号快照',
  `unit`       VARCHAR(32)            DEFAULT NULL COMMENT '单位',
  `quote_id`   BIGINT                 DEFAULT NULL COMMENT '关联销售报价',
  `quote_source` VARCHAR(32)          DEFAULT NULL COMMENT '报价来源: SALES_QUOTE/STANDARD_PRICE/DISCOUNT_PRICE/FLOOR_PRICE, 空为手动',
  `standard_price` DECIMAL(18,4)      DEFAULT NULL COMMENT '产品报价标准单价快照',
  `discount_unit_price` DECIMAL(18,4) DEFAULT NULL COMMENT '产品报价折扣单价快照',
  `floor_price` DECIMAL(18,4)         DEFAULT NULL COMMENT '产品报价底价快照',
  `qty`        DECIMAL(18,4)          DEFAULT NULL,
  `unit_price` DECIMAL(18,4)          DEFAULT NULL,
  `amount`     DECIMAL(18,4)          DEFAULT NULL,
  `remark`     VARCHAR(512)           DEFAULT NULL COMMENT '明细备注',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_pi` (`pi_id`),
  KEY `idx_sales_pi_item_quote` (`quote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售合同 PI 明细';

-- ---------------------------------------------------------------------
-- 订单域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_oem` (
  `id`          BIGINT       NOT NULL,
  `oem_no`      VARCHAR(64)  NOT NULL,
  `pi_id`       BIGINT                DEFAULT NULL COMMENT '关联 PI ID',
  `pi_no`       VARCHAR(64)           DEFAULT NULL COMMENT '关联 PI 单号快照',
  `customer_id` BIGINT                DEFAULT NULL,
  `customer_code` VARCHAR(64)         DEFAULT NULL COMMENT '客户业务ID快照',
  `customer_name` VARCHAR(255)        DEFAULT NULL COMMENT '客户名称快照',
  `product_id`  BIGINT                DEFAULT NULL,
  `product_model` VARCHAR(255)        DEFAULT NULL COMMENT '产品型号快照',
  `factory_model` VARCHAR(255)        DEFAULT NULL COMMENT '工厂型号快照',
  `requirement` TEXT,
  `status`      VARCHAR(16)           DEFAULT 'DRAFT',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oem_no` (`oem_no`, `deleted`),
  KEY `idx_order_oem_pi` (`pi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户 OEM 要求';

CREATE TABLE IF NOT EXISTS `order_oem_file` (
  `id`            BIGINT       NOT NULL,
  `oem_id`        BIGINT       NOT NULL COMMENT '所属 OEM 要求',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `stored_name`   VARCHAR(255) NOT NULL COMMENT '磁盘存储名',
  `content_type`  VARCHAR(128)          DEFAULT NULL,
  `size`          BIGINT                DEFAULT NULL COMMENT '字节大小',
  `uploader_name` VARCHAR(64)           DEFAULT NULL,
  `remark`        VARCHAR(255)          DEFAULT NULL,
  `create_by`     BIGINT      DEFAULT NULL,
  `create_time`   DATETIME    DEFAULT NULL,
  `update_by`     BIGINT      DEFAULT NULL,
  `update_time`   DATETIME    DEFAULT NULL,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order_oem_file_oem` (`oem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OEM 要求附件';

CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id`           BIGINT        NOT NULL,
  `po_no`        VARCHAR(64)   NOT NULL,
  `pi_id`        BIGINT                 DEFAULT NULL COMMENT '关联 PI ID',
  `pi_no`        VARCHAR(64)            DEFAULT NULL COMMENT '关联 PI 单号快照',
  `purchaser_id` BIGINT                 DEFAULT NULL COMMENT '采购人用户 ID',
  `supplier`     VARCHAR(255)           DEFAULT NULL,
  `currency`     VARCHAR(8)             DEFAULT 'CNY',
  `total_amount` DECIMAL(18,4)          DEFAULT 0,
  `status`       VARCHAR(16)            DEFAULT 'DRAFT',
  `order_date`   DATE                   DEFAULT NULL,
  `remark`       VARCHAR(512)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_no` (`po_no`, `deleted`),
  KEY `idx_purchase_order_pi` (`pi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PO 采购订单 主表';

CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `id`         BIGINT        NOT NULL,
  `po_id`      BIGINT        NOT NULL,
  `product_id` BIGINT                 DEFAULT NULL,
  `prod_code`  VARCHAR(64)            DEFAULT NULL,
  `product_name` VARCHAR(255)         DEFAULT NULL,
  `qty`        DECIMAL(18,4)          DEFAULT NULL,
  `received_qty` DECIMAL(18,4)        NOT NULL DEFAULT 0 COMMENT '累计到货入库数量',
  `unit_price` DECIMAL(18,4)          DEFAULT NULL,
  `amount`     DECIMAL(18,4)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_po` (`po_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PO 采购订单 明细';

CREATE TABLE IF NOT EXISTS `shipping_instruction` (
  `id`          BIGINT       NOT NULL,
  `si_no`       VARCHAR(64)  NOT NULL,
  `customer_id` BIGINT                DEFAULT NULL,
  `pi_id`       BIGINT                DEFAULT NULL,
  `ship_date`   DATE                  DEFAULT NULL,
  `shipping_address` VARCHAR(512)     DEFAULT NULL COMMENT '发货物流地址',
  `delivery_address` VARCHAR(512)     DEFAULT NULL COMMENT '送货地址',
  `contact_name` VARCHAR(128)         DEFAULT NULL COMMENT '联系人',
  `phone`       VARCHAR(64)           DEFAULT NULL COMMENT '电话',
  `order_no`    VARCHAR(128)          DEFAULT NULL COMMENT '订单编号',
  `order_country` VARCHAR(128)        DEFAULT NULL COMMENT '订单国家',
  `product_model` VARCHAR(512)        DEFAULT NULL COMMENT '产品型号',
  `factory_model` VARCHAR(512)        DEFAULT NULL COMMENT '工厂型号',
  `quantity`    DECIMAL(18,4)         DEFAULT NULL COMMENT '数量',
  `carton_count` DECIMAL(18,4)        DEFAULT NULL COMMENT '箱数',
  `order_requirement` TEXT            DEFAULT NULL COMMENT '订单要求',
  `owner_name`  VARCHAR(128)          DEFAULT NULL COMMENT '负责人/业务员',
  `order_date`  DATE                  DEFAULT NULL COMMENT '下单日期',
  `port`        VARCHAR(128)          DEFAULT NULL,
  `status`      VARCHAR(16)           DEFAULT 'DRAFT',
  `remark`      VARCHAR(512)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_si_no` (`si_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货指示';

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

-- ---------------------------------------------------------------------
-- 出货域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shipment` (
  `id`           BIGINT        NOT NULL,
  `shipment_no`  VARCHAR(64)   NOT NULL,
  `si_id`        BIGINT                 DEFAULT NULL COMMENT '关联出货指示',
  `pi_id`        BIGINT                 DEFAULT NULL COMMENT '关联 PI',
  `ship_date`    DATE                   DEFAULT NULL,
  `qty`          DECIMAL(18,4)          DEFAULT NULL,
  `status`       VARCHAR(16)            DEFAULT 'DRAFT',
  `remark`       VARCHAR(512)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_no` (`shipment_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货单';

CREATE TABLE IF NOT EXISTS `shipment_item` (
  `id`          BIGINT        NOT NULL,
  `shipment_id` BIGINT        NOT NULL,
  `pi_item_id`  BIGINT                 DEFAULT NULL,
  `product_id`  BIGINT                 DEFAULT NULL,
  `prod_code`   VARCHAR(64)            DEFAULT NULL,
  `qty`         DECIMAL(18,4) NOT NULL DEFAULT 0,
  `create_by`   BIGINT                 DEFAULT NULL,
  `create_time` DATETIME               DEFAULT NULL,
  `update_by`   BIGINT                 DEFAULT NULL,
  `update_time` DATETIME               DEFAULT NULL,
  `deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_shipment_item_shipment` (`shipment_id`),
  KEY `idx_shipment_item_pi` (`pi_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货单产品明细';

-- ---------------------------------------------------------------------
-- 财务域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `finance_entry` (
  `id`               BIGINT        NOT NULL,
  `entry_no`         VARCHAR(64)            DEFAULT NULL COMMENT '流水号',
  `type`             VARCHAR(32)   NOT NULL COMMENT 'RECEIVABLE应收/PAYABLE应付/PRE_RECEIVE预收/PRE_PAY预付/UNRECEIVED应收未收/UNPAID应付未付',
  `direction`        VARCHAR(8)    NOT NULL COMMENT 'debit借/credit贷',
  `customer_id`      BIGINT                 DEFAULT NULL,
  `amount`           DECIMAL(18,4) NOT NULL COMMENT '金额',
  `currency`         VARCHAR(8)             DEFAULT 'USD',
  `related_doc_type` VARCHAR(32)            DEFAULT NULL COMMENT '关联单据类型',
  `related_doc_no`   VARCHAR(64)            DEFAULT NULL COMMENT '关联单据号',
  `entry_date`       DATE                   DEFAULT NULL,
  `remark`           VARCHAR(512)           DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收付款流水';

-- ---------------------------------------------------------------------
-- 系统支撑
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `import_template` (
  `id`        BIGINT       NOT NULL,
  `module`    VARCHAR(64)  NOT NULL COMMENT '所属模块',
  `name`      VARCHAR(128) NOT NULL,
  `file_path` VARCHAR(255)          DEFAULT NULL,
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入模板';

CREATE TABLE IF NOT EXISTS `doc_template` (
  `id`        BIGINT       NOT NULL,
  `code`      VARCHAR(64)  NOT NULL COMMENT '单据编码 如 PI',
  `name`      VARCHAR(128) NOT NULL,
  `lang`      VARCHAR(8)            DEFAULT 'zh' COMMENT 'zh/en 双语',
  `file_path` VARCHAR(255)          DEFAULT NULL COMMENT 'Word 模板路径',
  `create_by`   BIGINT      DEFAULT NULL,
  `create_time` DATETIME    DEFAULT NULL,
  `update_by`   BIGINT      DEFAULT NULL,
  `update_time` DATETIME    DEFAULT NULL,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据模板';
