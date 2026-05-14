# FoodChain UML Class Diagrams

This directory contains 11 Draw.io (`.drawio`) class diagram files for the FoodChain microservices application. Each file can be opened and edited in [Draw.io / diagrams.net](https://app.diagrams.net/) — either the web app or the desktop app.

---

## How to Open

1. Go to [https://app.diagrams.net/](https://app.diagrams.net/)
2. Click **Open Existing Diagram** and select any `.drawio` file from this directory.
3. Or install the [Draw.io VS Code extension](https://marketplace.visualstudio.com/items?itemName=hediet.vscode-drawio) and open files directly in VS Code.
4. For the desktop app: [https://github.com/jgraph/drawio-desktop/releases](https://github.com/jgraph/drawio-desktop/releases)

---

## Diagram Files

### 1. `architecture-overview.drawio`
**High-Level System Architecture**

Shows all services, their ports, and the infrastructure they rely on:
- API Gateway (:8080) receiving HTTP/WebSocket traffic from the React frontend
- All 7 microservices with ports (User :8081, Branch :8085, Menu :8082, Order :8083, Kitchen :8084, Notifications :8086, Analytics :8087)
- Eureka Server (:8761) for service discovery
- Config Server (:8888) for centralized configuration
- MySQL databases (one per service)
- Redis cache (Kitchen queue + JWT blacklist)
- Apache Kafka message broker with topic labels
- AWS S3 (menu item images)
- Gemini AI (food suggestions)

---

### 2. `user-service-class-diagram.drawio`
**User Service — Full Class Diagram**

Classes covered:
- `User` entity (implements `UserDetails`) with `Role` enum
- `JwtService` interface + `JwtServiceImpl` (HMAC-SHA JWT generation/validation)
- `AuthService` interface + `AuthServiceImpl` (register, login, Google OAuth2, token refresh, password reset)
- `UserService` interface + `UserServiceImpl` (user CRUD, role management)
- `AuthController` and `UserController` (REST endpoints)
- `UserRepository` (Spring Data JPA)
- `JwtAuthenticationFilter` (intercepts POST /login)
- `JwtAuthorizationFilter` (validates Bearer tokens)
- `KafkaEmailProducer` (publishes to `notification.email.send`)
- `CustomOAuth2UserService` + `OAuth2AuthenticationSuccessHandler` (Google OAuth2 flow)
- `TokenBlacklistService` / `TokenBlacklistServiceImpl` (Redis-backed JWT blacklist)
- DTOs: `AuthResponse`, `RegisterRequest`, `LoginRequest`, `UserResponse`

---

### 3. `order-service-class-diagram.drawio`
**Order Service — Full Class Diagram**

Classes covered:
- `Order` entity with `OrderStatus` enum (RECEIVED → CONFIRMED → PREPARING → READY → PICKED_UP/SERVED → COMPLETED/CANCELLED) and `OrderType` enum (DINE_IN, TAKEAWAY, DELIVERY)
- `OrderItem` entity (many-to-one with Order)
- `OutboxEvent` entity (Transactional Outbox Pattern)
- `OrderStatusUpdate` entity (audit log of status changes)
- `OrderService` interface + `OrderServiceImpl` (create, read, update status, cancel)
- `StatusTransitionValidator` (enforces legal status transitions)
- `OrderController` (REST endpoints with idempotency key support)
- `OrderRepository`, `OutboxEventRepository`, `OrderStatusUpdateRepository`
- `OutboxRelay` (polls DB every 500ms, publishes unpublished OutboxEvents to Kafka)
- DTOs: `CreateOrderRequest`, `OrderResponse`, `FrontendOrderResponse`, `OrderDetailResponse`

---

### 4. `branch-service-class-diagram.drawio`
**Branch Service — Full Class Diagram**

Classes covered:
- `Branch` entity (id, name, address, managerId, location coordinates, rating)
- `BranchHours` entity (many-to-one with Branch, day of week, open/close times)
- `BranchService` interface + `BranchServiceImpl` (CRUD, nearby branch search using Haversine formula)
- `BranchController` (REST endpoints)
- `BranchRepository`, `BranchHoursRepository`
- `GatewayAuthenticationFilter` (reads X-User-Id/X-User-Role headers from gateway)
- `BranchSampleDataLoader` (seed data on startup)
- DTOs: `BranchResponse`, `CreateBranchRequest`, `BranchHoursRequest`, `NearbyBranchResponse`

---

### 5. `menu-service-class-diagram.drawio`
**Menu Service — Full Class Diagram**

Classes covered:
- `MenuCategory` entity (id, name, displayOrder, active)
- `MenuItem` entity (id, name, description, category, basePrice, imageUrl, active)
- `MenuService` interface + `MenuServiceImpl` (CRUD for items and categories, S3 image upload, AI food suggestions)
- `MenuItemController`, `MenuCategoryController`, `MenuBranchController` (REST endpoints)
- `MenuItemRepository`, `MenuCategoryRepository`
- `FoodSuggestionAiClient` interface + `GeminiFoodSuggestionClient` (calls Google Gemini API)
- `S3Service` (AWS S3 image upload/delete)
- `S3Config` (AWS credentials configuration)
- DTOs/Records: `CreateMenuItemRequest`, `MenuItemResponse`, `FoodSuggestionRequest`, `FoodSuggestionResponse`, `FoodSuggestionItem`

---

### 6. `kitchen-service-class-diagram.drawio`
**Kitchen Service — Full Class Diagram**

Classes covered:
- `KitchenQueueService` interface + `KitchenQueueServiceImpl` (Redis-backed kitchen order queue)
- `SLAMonitoringService` (scheduled SLA breach detection: AMBER >15min, RED >25min)
- `KitchenController` (REST endpoints for queue management, accept/ready/status update)
- `KitchenEventConsumer` (Kafka consumer for `order.received`)
- Redis interaction for storing `KitchenOrder` objects per branch
- DTOs (inner classes of `KitchenDtos`): `KitchenOrder`, `KitchenOrderItem`, `KitchenQueueResponse`, `KitchenActionRequest`, `SLAAlert`
- `WebSocketConfig` (STOMP WebSocket configuration for SLA alerts)
- `RedisLettuceConfig` (Redis connection factory)

---

### 7. `notifications-service-class-diagram.drawio`
**Notifications Service — Full Class Diagram**

Classes covered:
- `NotificationLog` entity with `NotificationType` enum (ORDER_RECEIVED, STATUS_UPDATE, ORDER_READY, EMAIL)
- `NotificationEventConsumer` (Kafka consumer for 4 topics: `order.received`, `order.status.updated`, `order.ready`, `notification.email.send`)
- `SmtpMailService` (JavaMailSender SMTP email)
- `BrevoMailService` (Brevo transactional email API)
- `NotificationLogService` (persist notification logs, mark as read)
- `NotificationLogRepository`
- `NotificationController` (REST endpoints for notification history)
- `RawWebSocketHandler` (raw WebSocket for kitchen/order-tracker/manager dashboards)
- `WebSocketHandlerRegistry` (Spring beans: kitchenWebSocketHandler, orderWebSocketHandler, managerWebSocketHandler)
- DTOs: `OrderReceivedEvent`, `OrderStatusUpdatedEvent`, `CustomerNotification`, `EmailSendEvent`, `NotificationHistoryResponse`

---

### 8. `analytics-service-class-diagram.drawio`
**Analytics & Report Service — Full Class Diagram**

Classes covered:
- `OrderAnalytics` entity (denormalized per-order data)
- `OrderItemAnalytics` entity (denormalized per-item data for top items reporting)
- `BranchDailySummary` entity (daily rollup per branch)
- `Report` entity (generated report snapshots)
- `AnalyticsService` interface + `AnalyticsServiceImpl` (17 methods covering dashboard, manager, admin, overview, trends, operational analytics)
- `ReportService` interface + `ReportServiceImpl` (generate, list, get reports)
- `AnalyticsEventConsumer` (Kafka consumer for `order.received`, `order.status.updated`, `analytics.daily.rollup`)
- `AnalyticsController` (analytics queries including branch comparison, trends, operational)
- `ManagerController` (manager dashboard, live orders, daily sales, popular items)
- `AdminAnalyticsController` (admin-level multi-branch analytics)
- `ReportController` (report generation and retrieval)
- Repositories: `OrderAnalyticsRepository`, `OrderItemAnalyticsRepository`, `BranchDailySummaryRepository`, `ReportRepository`
- DTOs: `DashboardResponse`, `ManagerDashboardResponse`, `AdminAnalyticsResponse`, `OverviewResponse`, `BranchComparisonResponse`, `TrendsResponse`

---

### 9. `security-class-diagram.drawio`
**Security Architecture — Cross-Service**

Shows the complete security chain:
- `JwtAuthFilter` in API Gateway (validates JWT, injects headers, blocks unauthorized requests)
- `JwtService` / `JwtServiceImpl` in User Service (generates 15-minute access tokens and 7-day refresh tokens)
- `JwtAuthenticationFilter` in User Service (intercepts POST /login)
- `JwtAuthorizationFilter` in User Service (validates Bearer tokens on all other routes)
- `SecurityConfig` (Spring Security filter chain configuration)
- `TokenBlacklistService` / `TokenBlacklistServiceImpl` (Redis-backed revocation using JWT ID claims)
- `CustomOAuth2UserService` (Google OAuth2 user upsert)
- `OAuth2AuthenticationSuccessHandler` (issues JWT pair on successful Google login)
- `GatewayAuthenticationFilter` in all downstream services (trusts X-User-Id/X-User-Role headers)
- `User` entity implementing `UserDetails`

---

### 10. `event-driven-architecture.drawio`
**Kafka Event-Driven Architecture**

Shows all Kafka producers, topics, consumers, and downstream effects:

**Producers:**
- `OutboxRelay` (order-service) — publishes `order.received`, `order.status.updated`, `order.ready` via Transactional Outbox Pattern
- `KafkaEmailProducer` (user-service) — publishes `notification.email.send`

**Topics:**
- `order.received` — consumed by kitchen-service, notifications-service, analytics-report-service
- `order.status.updated` — consumed by notifications-service, analytics-report-service
- `order.ready` — consumed by notifications-service
- `notification.email.send` — consumed by notifications-service
- `analytics.daily.rollup` — consumed by analytics-report-service

**Consumers and downstream effects:**
- `KitchenEventConsumer` → Redis kitchen queue
- `NotificationEventConsumer` → STOMP push, email (SMTP/Brevo), raw WebSocket, NotificationLog DB
- `AnalyticsEventConsumer` → OrderAnalytics, OrderItemAnalytics, BranchDailySummary DB

---

### 11. `all-services-class-diagram.drawio`
**Master Diagram — All Services**

A wide-canvas diagram with one swimlane per service containing the most important classes, showing cross-service relationships:
- User Service, Branch Service, Menu Service, Order Service, Kitchen Service, Notifications Service, Analytics/Report Service
- Cross-service arrows: Order → Kitchen (Kafka), Order → Notifications (Kafka), Order → Analytics (Kafka), User → Notifications (Kafka email)
- Entity reference arrows: Order.branchId → Branch, Order.menuItemId → MenuItem, User.branchId → Branch

---

## Color Scheme

| Color | Meaning |
|-------|---------|
| Blue (`#dae8fc`) | Entities (`@Entity` JPA classes) |
| Green (`#d5e8d4`) | Controllers (`@RestController`) |
| Yellow (`#fff2cc`) | Services and Interfaces |
| Pink/Red (`#f8cecc`) | Repositories (JpaRepository) |
| Purple (`#e1d5e7`) | DTOs, Records, Events |
| Orange (`#ffe6cc`) | Kafka producers/consumers |
| Gray (`#f5f5f5`) | Security filters, config classes |

---

## Notes

- All diagrams were generated from actual source code exploration of the FoodChain microservices.
- Class fields and methods reflect the real Java code at the time of generation.
- Cross-service communication is either via Apache Kafka (async events) or HTTP REST calls through the API Gateway.
- The API Gateway validates JWTs and injects user identity headers — downstream services trust these headers and never validate JWTs themselves.
- The Order Service uses the Transactional Outbox Pattern: events are saved to the `outbox_events` DB table in the same transaction as the order, then a scheduler (`OutboxRelay`) polls and publishes them to Kafka, guaranteeing at-least-once delivery.
