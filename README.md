# FoodChain — Analytics & Report Service

Spring Boot microservice that consumes Kafka order events, persists per-branch daily summaries, and exposes REST endpoints for manager dashboards, admin analytics, and business reports.

---

## Overview

| Item | Value |
|---|---|
| Port | **8085** |
| Context path | `/api` |
| Base URL (local) | `http://localhost:8085/api` |
| Via API Gateway | `http://localhost:8080/api` |
| Database | MySQL — `analytics_report_db` |
| Cache | Redis (localhost:6379) |
| Events | Kafka (localhost:9092) |
| Service registry | Eureka (localhost:8761) |

---

## Kafka Topics Consumed

| Topic | Event | Action |
|---|---|---|
| `order-received` | `OrderReceivedEvent` | Persist `OrderAnalytics` row |
| `order-status-updated` | `OrderStatusUpdatedEvent` | Update order status in `OrderAnalytics` |

A scheduled job runs every midnight and rolls up `OrderAnalytics` rows into `BranchDailySummary` entries.

---

## All Endpoints

### Analytics — `/analytics`

| Method | Path | Description |
|---|---|---|
| GET | `/analytics/dashboard` | All-branch overview for a date (defaults to today) |
| GET | `/analytics/branch/{branchId}/summary` | Daily summary for one branch on a specific date |
| GET | `/analytics/branch/{branchId}/summaries` | Range of daily summaries for one branch |
| GET | `/analytics/branch/{branchId}/orders` | Paginated raw order records for a branch |
| POST | `/analytics/rollup` | Manually trigger daily rollup for a date |

### Reports — `/reports`

| Method | Path | Description |
|---|---|---|
| POST | `/reports/generate` | Generate a report for a branch and date range |
| GET | `/reports/{id}` | Fetch a previously generated report by ID |
| GET | `/reports` | List all reports (paginated) |

### Manager Dashboard — `/manager`

| Method | Path | Description |
|---|---|---|
| GET | `/manager/dashboard` | Today's key metrics for the manager's branch |
| GET | `/manager/orders/live` | Currently active orders at the manager's branch |
| GET | `/manager/sales/daily` | Hourly revenue/orders for a date (defaults to today) |
| GET | `/manager/items/popular` | Top-selling items for the manager's branch today |

### Admin Analytics — `/admin`

| Method | Path | Description |
|---|---|---|
| GET | `/admin/analytics` | System-wide aggregated metrics for a date range |
| GET | `/admin/analytics/branches` | Per-branch orders and revenue for a date range |

---

## How branchId is passed for Manager Endpoints

Each manager endpoint accepts `branchId` via two mechanisms (param takes precedence):

1. **Query parameter** — `?branchId=<uuid>`
2. **Request header** — `X-User-BranchId: <uuid>`

The API Gateway is expected to inject `X-User-BranchId` from the authenticated JWT. If neither is provided the endpoint returns `400 Bad Request`.

---

## Response Shapes

### Manager Dashboard — `GET /manager/dashboard`

```json
{
  "totalOrders": 42,
  "totalRevenue": 1250.50,
  "averageOrderValue": 29.77,
  "ordersChange": 5.2,
  "revenueChange": -1.3
}
```

`ordersChange` and `revenueChange` are percentage changes vs the previous day. Returns `0.0` when no prior-day data exists.

### Daily Sales — `GET /manager/sales/daily?date=YYYY-MM-DD`

Array of hourly slots (business hours 08:00–22:00). Revenue reflects completed orders only.

```json
[
  { "hour": "08:00", "revenue": 0.0, "orders": 0 },
  { "hour": "09:00", "revenue": 320.50, "orders": 11 },
  { "hour": "10:00", "revenue": 415.00, "orders": 14 }
]
```

### Popular Items — `GET /manager/items/popular`

Returns an empty array until item-level analytics are available (see TODO note below).

```json
[]
```

### Live Orders — `GET /manager/orders/live`

```json
[
  {
    "id": "order-uuid",
    "status": "PREPARING",
    "orderType": "DINE_IN",
    "tableNumber": null,
    "customerName": null,
    "createdAt": "2026-05-10T12:30:00",
    "totalAmount": 55.00,
    "itemCount": 3
  }
]
```

`tableNumber` and `customerName` are `null` — not stored in `OrderAnalytics`. These would require enrichment from order-service.

### Admin Analytics — `GET /admin/analytics`

```json
{
  "totalOrders": 5000,
  "totalRevenue": 148250.00,
  "averageOrderValue": 29.65,
  "totalBranches": 8,
  "totalCustomers": 0,
  "dailyBreakdown": []
}
```

`totalCustomers` is `0` — not tracked at summary level. `dailyBreakdown` is an empty array (hourly admin-level aggregation not yet implemented).

### Branch Analytics — `GET /admin/analytics/branches`

```json
[
  { "id": "branch-uuid-001", "name": "branch-uuid-001", "orders": 1200, "revenue": 36000.00 },
  { "id": "branch-uuid-002", "name": "branch-uuid-002", "orders": 900,  "revenue": 27500.00 }
]
```

`name` is the `branchId` string — branch names require a call to branch-service which is not currently wired.

---

## Data Aggregation

1. **Kafka consumer** listens to `order-received` and `order-status-updated` topics.
2. Each event upserts/updates a row in the `order_analytics` table.
3. A scheduled job (`@Scheduled(cron = "0 0 0 * * *")`) rolls up all orders from the previous day into `branch_daily_summary` rows grouped by `branchId`.
4. Manager and admin endpoints read from `branch_daily_summary` (aggregated) and `order_analytics` (live orders).

---

## Environment Variables / Configuration

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/analytics_report_db` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | MySQL password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | `http://localhost:8761/eureka/` | Eureka URL |
| `SERVER_PORT` | `8085` | Service port |

When running with the Config Server, set `SPRING_CONFIG_IMPORT=configserver:http://localhost:8888`.

---

## Known Limitations / TODOs

- **Popular items** (`GET /manager/items/popular`) always returns an empty list. `OrderAnalytics` only stores `itemCount` (integer), not per-item breakdown. Item-level data would require a separate `order_item_analytics` table populated from Kafka events that include line items.
- **`totalCustomers`** in admin analytics is always `0` — unique customer counting requires a dedicated aggregation not available from `BranchDailySummary`.
- **`tableNumber` / `customerName`** in live orders are always `null` — these fields are not forwarded in Kafka events or stored in `OrderAnalytics`.
- **Branch names** in `/admin/analytics/branches` use `branchId` as the name — no branch-service client is wired.
