# Analytics & Report Service

Consumes Kafka order events, persists per-branch daily summaries, and exposes REST endpoints for manager dashboards, admin analytics, and business reports.

**Base URL (via gateway):** `http://<gateway-host>/api`  
**Direct port:** `8085`

---

## Authentication

All endpoints require a valid JWT:

```
Authorization: Bearer <token>
```

Manager endpoints additionally require the JWT to carry a `branchId` claim. The gateway extracts this and sets the `X-User-BranchId` header automatically.

---

## How Data Gets In

```
order-service publishes Kafka events
        ↓
analytics-service consumes:
  • order.received   → records the order
  • order.updated    → updates status, timestamps
        ↓
Nightly job (midnight) → computes BranchDailySummary
        ↓
REST endpoints serve the aggregated data
```

---

## All Endpoints at a Glance

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/v1/manager/dashboard` | Manager | Key metrics for manager's branch |
| GET | `/v1/manager/orders/live` | Manager | Currently active orders |
| GET | `/v1/manager/sales/daily` | Manager | Hourly revenue/orders for a day |
| GET | `/v1/manager/items/popular` | Manager | Top-selling items |
| GET | `/v1/manager/summary/history` | Manager | Daily summaries over date range |
| GET | `/v1/analytics/dashboard` | Any auth | All-branch overview |
| GET | `/v1/analytics/branch/{branchId}/summary` | Any auth | Single branch, single day |
| GET | `/v1/analytics/branch/{branchId}/summaries` | Any auth | Single branch, date range |
| GET | `/v1/analytics/branch/{branchId}/orders` | Any auth | Paginated raw order records |
| POST | `/v1/analytics/rollup` | Any auth | Manually trigger daily rollup |
| GET | `/v1/admin/analytics` | Admin | System-wide metrics |
| GET | `/v1/admin/analytics/branches` | Admin | Per-branch breakdown |
| POST | `/v1/reports/generate` | Any auth | Generate a new report |
| GET | `/v1/reports` | Any auth | List all reports (paginated) |
| GET | `/v1/reports/{reportId}` | Any auth | Get one report by ID |
| GET | `/v1/reports/branch/{branchId}` | Any auth | All reports for a branch |

---

## Manager Dashboard

These endpoints read `branchId` from the `X-User-BranchId` header (set by gateway from JWT). A manager only sees data for their own branch.

---

### GET `/v1/manager/dashboard`

Key metrics for the manager's branch. Defaults to today.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `date` | string (yyyy-MM-dd) | today | Date to query |

**Response `200`:**
```json
{
  "totalOrders": 48,
  "totalRevenue": 3240.50,
  "averageOrderValue": 67.51,
  "ordersChange": 12.5,
  "revenueChange": 8.3,
  "averagePrepTime": 14.2,
  "peakHour": "13:00",
  "peakHourOrders": 11,
  "completionRate": 91.7,
  "dineInCount": 28,
  "takeawayCount": 14,
  "deliveryCount": 6
}
```

| Field | Type | Description |
|-------|------|-------------|
| `totalOrders` | long | All orders for the day |
| `totalRevenue` | double | Sum of all order amounts |
| `averageOrderValue` | double | Revenue ÷ orders |
| `ordersChange` | double | % change vs previous day (positive = up) |
| `revenueChange` | double | % change vs previous day |
| `averagePrepTime` | double | Average minutes from order placed to ready |
| `peakHour` | string | Hour with most orders (24h format, e.g. `"13:00"`) |
| `peakHourOrders` | long | Order count in peak hour |
| `completionRate` | double | Percentage of orders completed (0–100) |
| `dineInCount` | long | Orders with type DINE_IN |
| `takeawayCount` | long | Orders with type TAKEAWAY |
| `deliveryCount` | long | Orders with type DELIVERY |

---

### GET `/v1/manager/orders/live`

Returns all currently **active** orders at the manager's branch (statuses: RECEIVED, CONFIRMED, PREPARING, READY).

**Response `200`:**
```json
[
  {
    "id": "order-uuid",
    "status": "PREPARING",
    "orderType": "DINE_IN",
    "tableNumber": "T5",
    "customerName": "Omar Al-Hassan",
    "createdAt": "2026-05-13T12:34:00",
    "totalAmount": 68.0,
    "itemCount": 3
  }
]
```

| Field | Description |
|-------|-------------|
| `id` | Order UUID |
| `status` | `RECEIVED` / `CONFIRMED` / `PREPARING` / `READY` |
| `orderType` | `DINE_IN` / `TAKEAWAY` / `DELIVERY` |
| `tableNumber` | Table number for dine-in; `null` for others |
| `customerName` | Customer's display name |
| `createdAt` | ISO datetime string |
| `totalAmount` | Order total |
| `itemCount` | Number of line items |

---

### GET `/v1/manager/sales/daily`

Hourly revenue and order count breakdown for a day. Useful for a bar chart.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `date` | string (yyyy-MM-dd) | today | Day to query |

**Response `200`:**
```json
[
  { "hour": "09:00", "revenue": 210.50, "orders": 4 },
  { "hour": "10:00", "revenue": 340.00, "orders": 6 },
  { "hour": "11:00", "revenue": 180.00, "orders": 3 },
  { "hour": "12:00", "revenue": 890.50, "orders": 15 },
  { "hour": "13:00", "revenue": 1040.00, "orders": 17 }
]
```

Returns one entry per hour that had orders. Hours with no orders are omitted.

---

### GET `/v1/manager/items/popular`

Top-selling menu items for the manager's branch. Sorted by quantity sold.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `date` | string (yyyy-MM-dd) | today | Day to query |

**Response `200`:**
```json
[
  {
    "id": "item-uuid",
    "name": "Kabsa Chicken",
    "category": "Mains",
    "quantitySold": 24,
    "revenue": 1632.00,
    "trend": 15.0
  }
]
```

| Field | Description |
|-------|-------------|
| `id` | Menu item UUID |
| `name` | Item name |
| `category` | Category name |
| `quantitySold` | Total quantity sold today |
| `revenue` | Total revenue from this item |
| `trend` | % change in quantity vs previous period (positive = up) |

---

### GET `/v1/manager/summary/history`

Per-day summaries for the manager's branch over a date range. Good for trend charts.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `from` | string (yyyy-MM-dd) | 30 days ago | Start date (inclusive) |
| `to` | string (yyyy-MM-dd) | today | End date (inclusive) |

**Response `200`:** array of `BranchSummaryResponse` objects (see Analytics section below), sorted oldest-first.

---

## Analytics

General analytics endpoints accessible to any authenticated user.

---

### GET `/v1/analytics/dashboard`

High-level overview across all branches for a given day.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `date` | string (yyyy-MM-dd) | today | Date to query |

**Response `200`:**
```json
{
  "date": "2026-05-13",
  "totalOrdersToday": 312,
  "totalRevenueToday": 21450.75,
  "completedOrdersToday": 287,
  "cancelledOrdersToday": 14,
  "branchSummaries": [
    {
      "branchId": "00e03993-...",
      "date": "2026-05-13",
      "totalOrders": 48,
      "completedOrders": 44,
      "cancelledOrders": 2,
      "inProgressOrders": 2,
      "totalRevenue": 3240.50,
      "avgOrderValue": 67.51,
      "dineInCount": 28,
      "takeawayCount": 14,
      "deliveryCount": 6
    }
  ]
}
```

---

### GET `/v1/analytics/branch/{branchId}/summary`

Daily summary for one branch on a specific date.

**Path param:** `branchId` — branch UUID  
**Query param:** `date` (yyyy-MM-dd, defaults to today)

**Response `200`:**
```json
{
  "branchId": "00e03993-6425-4703-a38f-cc661ceedf44",
  "date": "2026-05-13",
  "totalOrders": 48,
  "completedOrders": 44,
  "cancelledOrders": 2,
  "inProgressOrders": 2,
  "totalRevenue": 3240.50,
  "avgOrderValue": 67.51,
  "dineInCount": 28,
  "takeawayCount": 14,
  "deliveryCount": 6
}
```

**Response `404`:** no data exists for that branch on that date (no orders were placed).

---

### GET `/v1/analytics/branch/{branchId}/summaries`

Array of daily summaries for one branch over a date range. Sorted oldest-first.

**Path param:** `branchId`  
**Query params:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | string (yyyy-MM-dd) | Yes | Start date inclusive |
| `to` | string (yyyy-MM-dd) | Yes | End date inclusive |

**Response `200`:** array of `BranchSummaryResponse` objects (same structure as single summary above).

---

### GET `/v1/analytics/branch/{branchId}/orders`

Paginated raw analytics records — one entry per order placed at the branch.

**Path param:** `branchId`  
**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `20` | Records per page |

**Response `200`:**
```json
{
  "content": [
    {
      "id": 1,
      "orderId": "order-uuid",
      "branchId": "branch-uuid",
      "customerId": "customer-uuid",
      "status": "COMPLETED",
      "orderType": "DINE_IN",
      "totalAmount": 88.00,
      "itemCount": 4,
      "orderReceivedAt": "2026-05-13T12:00:00",
      "lastUpdatedAt": "2026-05-13T12:32:00"
    }
  ],
  "totalElements": 124,
  "totalPages": 7,
  "number": 0,
  "size": 20
}
```

---

### POST `/v1/analytics/rollup`

Manually triggers recalculation of the daily summary for a date. Normally runs automatically at midnight. Use this to backfill data or correct a specific date.

**Query param:** `date` (yyyy-MM-dd, defaults to yesterday)

**Response `200`:**
```json
"Daily summary computed for 2026-05-12"
```

---

## Admin Analytics

System-wide endpoints for platform administrators.

---

### GET `/v1/admin/analytics`

Aggregated metrics across **all branches** for a date range.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `startDate` | string (yyyy-MM-dd) | 30 days ago | Start date inclusive |
| `endDate` | string (yyyy-MM-dd) | today | End date inclusive |

**Response `200`:**
```json
{
  "totalOrders": 4820,
  "totalRevenue": 318540.75,
  "averageOrderValue": 66.09,
  "totalBranches": 12,
  "totalCustomers": 1340,
  "dailyBreakdown": [
    { "hour": "2026-05-01", "revenue": 10240.00, "orders": 156 },
    { "hour": "2026-05-02", "revenue": 11320.50, "orders": 172 }
  ]
}
```

> In `dailyBreakdown`, the `hour` field contains the **date** string when used in admin context.

---

### GET `/v1/admin/analytics/branches`

Orders and revenue broken down per branch for a date range.

**Query params:** same as above (`startDate`, `endDate`)

**Response `200`:**
```json
[
  {
    "id": "branch-uuid",
    "name": "Downtown Branch",
    "orders": 520,
    "revenue": 34820.00
  },
  {
    "id": "branch-uuid-2",
    "name": "Airport Branch",
    "orders": 390,
    "revenue": 28100.50
  }
]
```

---

## Reports

Generate and retrieve snapshot reports built from aggregated analytics data.

---

### POST `/v1/reports/generate`

Aggregates daily summaries over a date range and saves a report. Leave `branchId` null for a company-wide report.

**Request body:**
```json
{
  "reportType": "BRANCH_PERFORMANCE",
  "branchId": "00e03993-6425-4703-a38f-cc661ceedf44",
  "startDate": "2026-05-01",
  "endDate": "2026-05-13",
  "requestedBy": "manager-user-id"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `reportType` | string | Yes | `BRANCH_PERFORMANCE` / `SALES_SUMMARY` / `ORDER_SUMMARY` |
| `branchId` | string (UUID) | No | Omit or `null` for all branches |
| `startDate` | string (yyyy-MM-dd) | Yes | |
| `endDate` | string (yyyy-MM-dd) | Yes | Must be ≥ startDate |
| `requestedBy` | string | No | User ID or name for audit trail |

**Response `201`:**
```json
{
  "id": 7,
  "reportType": "BRANCH_PERFORMANCE",
  "branchId": "00e03993-...",
  "startDate": "2026-05-01",
  "endDate": "2026-05-13",
  "generatedAt": "2026-05-13T14:00:00",
  "generatedBy": "manager-user-id",
  "totalOrders": 580,
  "completedOrders": 532,
  "cancelledOrders": 23,
  "inProgressOrders": 25,
  "totalRevenue": 38940.75,
  "avgOrderValue": 67.14,
  "dineInCount": 320,
  "takeawayCount": 175,
  "deliveryCount": 85,
  "completionRate": 91.72,
  "cancellationRate": 3.97
}
```

---

### GET `/v1/reports`

Paginated list of all previously generated reports, newest first.

**Query params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | integer | `0` | Page index |
| `size` | integer | `20` | Reports per page |

**Response `200`:**
```json
{
  "content": [
    {
      "id": 7,
      "reportType": "BRANCH_PERFORMANCE",
      "branchId": "00e03993-...",
      "startDate": "2026-05-01",
      "endDate": "2026-05-13",
      "generatedAt": "2026-05-13T14:00:00",
      "generatedBy": "manager-user-id",
      "totalRevenue": 38940.75,
      "totalOrders": 580
    }
  ],
  "totalElements": 14,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

### GET `/v1/reports/{reportId}`

Full report details by numeric ID.

**Path param:** `reportId` — numeric report ID (from the `id` field in generate response)

**Response `200`:** full `ReportResponse` object (same as generate response above).  
**Response `404`:** report not found.

---

### GET `/v1/reports/branch/{branchId}`

All reports generated for a specific branch, newest first.

**Path param:** `branchId` — branch UUID

**Response `200`:** array of full `ReportResponse` objects.

---

## Error Responses

All errors use this structure:

```json
{
  "success": false,
  "status": 400,
  "message": "startDate must not be null",
  "error": "Bad Request",
  "path": "/api/v1/reports/generate",
  "timestamp": "2026-05-13T10:00:00Z"
}
```

| Status | When |
|--------|------|
| `400` | Missing required fields or invalid date range |
| `401` | Missing or expired JWT |
| `404` | Resource not found (report, branch summary) |
| `500` | Unexpected server error |
