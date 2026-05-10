# FoodChain — Analytics & Report Service

Spring Boot microservice that consumes Kafka order events, persists per-branch analytics rows, rolls up daily summaries, and exposes REST APIs for manager dashboards, admin metrics, and generated reports.

---

## Overview

| Item | Value |
|---|---|
| Port | **8085** (`SERVER_PORT` overrides) |
| Context path | **`/api`** |
| Direct base URL | `http://localhost:8085/api` |
| Via API Gateway | **`http://localhost:8080`** — REST routes are under **`/api/v1/...`** (see route table in **api-gateway** README) |
| Database | MySQL — `analytics_report_db` |
| Cache | Redis |
| Events | Kafka (`order.received`, `order.status.updated`, optional `analytics.daily.rollup`) |
| Registry | Eureka |

Controllers use versioned paths **`/v1/analytics`**, **`/v1/reports`**, **`/v1/manager`**, **`/v1/admin/analytics`** on this service (after context path: **`/api/v1/...`**).

---

## Kafka topics consumed

| Topic | Purpose |
|---|---|
| **`order.received`** | New order — persists / updates `OrderAnalytics` |
| **`order.status.updated`** | Status change — updates analytics row |
| **`analytics.daily.rollup`** | Optional message (often an ISO date string) — triggers `computeDailySummaries` for that date |

A **`@Scheduled`** job also runs daily to roll `OrderAnalytics` into **`BranchDailySummary`**.

---

## Endpoints

Paths below are the servlet mappings **after** `/api` (add **`http://localhost:8085`** for direct calls, or **`http://localhost:8080`** for gateway — gateway paths match **`/api` + these suffixes**).

### Analytics — `/v1/analytics`

| Method | Path | Description |
|---|---|---|
| GET | `/v1/analytics/dashboard` | All-branch overview for a date (defaults to today) |
| GET | `/v1/analytics/branch/{branchId}/summary` | Daily summary for one branch on a date |
| GET | `/v1/analytics/branch/{branchId}/summaries` | Date range of summaries |
| GET | `/v1/analytics/branch/{branchId}/orders` | Paginated raw analytics rows for a branch |
| POST | `/v1/analytics/rollup` | Manually trigger rollup for a date |

### Reports — `/v1/reports`

| Method | Path | Description |
|---|---|---|
| POST | `/v1/reports/generate` | Generate a report |
| GET | `/v1/reports/{id}` | Fetch report by ID |
| GET | `/v1/reports` | Paginated list |

### Manager — `/v1/manager`

| Method | Path | Description |
|---|---|---|
| GET | `/v1/manager/dashboard` | Today’s metrics for the manager’s branch |
| GET | `/v1/manager/orders/live` | Active orders at the branch |
| GET | `/v1/manager/sales/daily` | Hourly revenue for a date |
| GET | `/v1/manager/items/popular` | Popular items (may be empty — see limitations) |

### Admin analytics — `/v1/admin/analytics`

| Method | Path | Description |
|---|---|---|
| GET | `/v1/admin/analytics` | System-wide metrics for a date range |
| GET | `/v1/admin/analytics/branches` | Per-branch orders/revenue for a range |

**Authorization:** These admin endpoints expect **`X-User-Role`** (forwarded by the API gateway). Accepted roles include **`HEAD_OFFICE_ADMIN`**, **`OFFICE_ADMIN`**, and **`Admin`** (case-insensitive). Missing header → **401**; wrong role → **403**.

---

## Manager endpoints — `branchId`

Each manager endpoint resolves **`branchId`** from:

1. Query **`?branchId=`** (wins if both present), or  
2. Header **`X-User-BranchId`** (from JWT via gateway).

If neither is set, the service responds with **400** (`ResponseStatusException`).

---

## Response shapes (abbrev.)

### Manager dashboard — `GET /v1/manager/dashboard`

```json
{
  "totalOrders": 42,
  "totalRevenue": 1250.50,
  "averageOrderValue": 29.77,
  "ordersChange": 5.2,
  "revenueChange": -1.3
}
```

### Daily sales — `GET /v1/manager/sales/daily?date=YYYY-MM-DD`

Hourly buckets (business hours). Revenue reflects completed orders only.

### Popular items — `GET /v1/manager/items/popular`

Often **`[]`** until item-level aggregation exists (see limitations).

### Live orders — `GET /v1/manager/orders/live`

`tableNumber` / `customerName` may be **`null`** if not stored in analytics rows.

### Admin — `GET /v1/admin/analytics` / `GET /v1/admin/analytics/branches`

See inline Swagger descriptions; `totalCustomers` / branch **names** may be placeholders until enrichment exists.

---

## Data flow

1. Kafka consumers handle **`order.received`** and **`order.status.updated`** (aligned with **order-service** topic names in config).
2. Rows live in **`order_analytics`**; rollups populate **`branch_daily_summary`**.
3. Manager and admin APIs read aggregated and live data accordingly.

---

## Configuration

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_*` | MySQL |
| `SPRING_DATA_REDIS_*` | Redis |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | Eureka |
| `SERVER_PORT` | Default **8085** |

Optional: `spring.config.import` → Config Server `http://localhost:8888`.

---

## Known limitations

- **Popular items** may stay empty without per-line-item analytics.
- **`totalCustomers`** may be **0** at summary level.
- **Branch display names** in admin breakdowns may equal **`branchId`** until branch-service integration exists.
