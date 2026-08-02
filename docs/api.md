# API Guide

## Base URL

Local development:

```text
http://localhost:8080/api
```

Frontend development proxy:

```text
http://localhost:5173/api -> http://localhost:8080/api
```

## Authentication

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "loginType": "password",
  "username": "admin",
  "password": "<your-bootstrap-admin-password>"
}
```

Authenticated requests:

```http
Authorization: Bearer <token>
```

## Unified Response

Most JSON endpoints return:

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

Paged endpoints return a page object in `data`, usually including records, total count, page number and page size.

## OpenAPI

When enabled for the active profile:

- Knife4j UI: http://localhost:8080/api/doc.html
- OpenAPI JSON: http://localhost:8080/api/v3/api-docs

## Main API Areas

| Area | Prefix |
| --- | --- |
| Authentication | `/auth` |
| Customer data | `/basedata/customer` |
| Product data | `/basedata/product` |
| BOM/accessories | `/basedata/bom` |
| Currency | `/basedata/currency` |
| Standard price | `/sales/standard-price` |
| Sales quote | `/sales/contract-price` |
| Sales PI | `/sales/pi` |
| Trace | `/trace` |
| OEM | `/order/oem` |
| PO | `/order/po` |
| Shipping instruction | `/order/shipping-instruction` |
| Shipment | `/shipment` |
| Finance entries | `/finance/entry` |
| Receivable | `/finance/receivable` |
| Payable | `/finance/payable` |
| Reports | `/report` |
| Inventory | `/inventory` |
| Messages | `/message` |
| Notifications | `/notification` |
| File sharing | `/fileshare` |
| System users | `/system/user` |
| Roles | `/system/role` |
| Permissions | `/system/permission` |
| Audit logs | `/system/audit` |

## Error Handling

- `401`: login expired or token missing.
- `403`: role permission or data-scope check failed.
- `400`: invalid business request or validation error.
- `500`: unexpected server error; check backend logs and audit logs.
