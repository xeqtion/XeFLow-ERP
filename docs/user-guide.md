# User Guide

This guide follows the normal work of a small foreign-trade sales company: develop customer, quote, sign PI, prepare OEM/PO, ship goods, register finance entries and review reports.

## 1. Login and Home

![登录](assets/screenshots/00-login.png)

首次空库启动仅创建 `admin`，密码取自部署者配置的 `ERP_BOOTSTRAP_ADMIN_PASSWORD`。系统没有公开默认密码。首页展示待办、通知、日程和经营入口。

![首页](assets/screenshots/01-dashboard.png)

## 2. Customer and Product Master Data

Customer profiles store the visible business customer ID, contact information, country, currency, credit limit, extra fields and attachments.

![客户资料](assets/screenshots/10-customer.png)

Product data stores product code, company model, factory model, factory price, currency and safety stock.

![产品资料](assets/screenshots/11-product.png)

BOM/accessory data records supplier, material and accessory information.

![产品配件资料](assets/screenshots/12-bom.png)

Currency rates support CNY conversion and financial reports.

![汇率管理](assets/screenshots/13-currency.png)

## 3. Sales Workflow

Standard prices define product pricing baselines.

![产品报价](assets/screenshots/14-standard-price.png)

Sales quotes are customer-specific prices. Quotes outside configured thresholds can require sales supervisor approval.

![销售报价](assets/screenshots/15-contract-price.png)

Sales PI records the confirmed contract and its item details. PI documents and commercial invoices can be exported from this module.

![销售合同 PI](assets/screenshots/16-sales-pi.png)

The document trace page connects quote, PI, OEM, PO, shipment and finance events.

![单据追踪](assets/screenshots/17-trace.png)

## 4. Order and Fulfillment

OEM requirements are linked to PI/customer/product data and can include attachments.

![OEM 要求](assets/screenshots/18-oem.png)

PO purchase orders link the sales PI to suppliers, purchase details and receiving status.

![PO 采购订单](assets/screenshots/19-po.png)

Shipping instructions define ship date, delivery address, logistics address, product model, quantity, carton count and owner.

![出货指示](assets/screenshots/20-shipping-instruction.png)

Shipment records represent final shipment confirmations and exportable shipment documents.

![出货单](assets/screenshots/21-shipment.png)

Inventory query summarizes stock by product and highlights low-stock items.

![库存查询](assets/screenshots/22-inventory.png)

## 5. Finance

Finance entries store receipt and payment flows with generated serial numbers.

![收付款](assets/screenshots/23-finance-entry.png)

Receivable and payable modules support payment registration and aging summaries.

![应收账款](assets/screenshots/24-receivable.png)

![应付账款](assets/screenshots/25-payable.png)

## 6. Reports

Purchase, sales and profit reports support date filtering and export.

![采购报表](assets/screenshots/26-report-purchase.png)

![销售报表](assets/screenshots/27-report-sales.png)

![财务利润报表](assets/screenshots/28-report-profit.png)

Salesman performance helps sales supervisors compare salespeople.

![业务员业绩](assets/screenshots/29-report-salesman.png)

## 7. Collaboration

Notifications and announcements support internal broadcasting and unread tracking.

![消息通知](assets/screenshots/02-notification.png)

Messages support user-to-user conversations and attachments.

![消息中心](assets/screenshots/03-message.png)

File sharing supports category, file format, description, expiration time and unified search.

![资料共享](assets/screenshots/04-fileshare.png)

## 8. Administration

Administrators maintain users, roles, permission menus and audit logs.

![用户管理](assets/screenshots/30-system-user.png)

![角色管理](assets/screenshots/31-system-role.png)

![权限菜单](assets/screenshots/32-system-permission.png)

![审计日志](assets/screenshots/33-system-audit.png)

## Core Storyboard

```mermaid
flowchart LR
  A["客户建档"] --> B["产品报价"]
  B --> C["销售报价审批"]
  C --> D["销售合同 PI"]
  D --> E["OEM 要求"]
  D --> F["PO 采购订单"]
  D --> G["出货指示"]
  G --> H["出货单"]
  H --> I["收付款 / 应收应付"]
  I --> J["销售、采购、利润、业绩报表"]
```
