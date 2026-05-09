package com.microservices.analytics_report.analytics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AnalyticsDtos {

    // ── Inbound Kafka events ──────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderReceivedEvent {
        private String orderId;
        private String customerId;
        private String branchId;
        private String status;
        private BigDecimal totalAmount;
        private String orderType;
        private List<OrderItemEvent> items;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderStatusUpdatedEvent {
        private String orderId;
        private String customerId;
        private String branchId;
        private String previousStatus;
        private String newStatus;
        private String updatedBy;
        private String notes;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItemEvent {
        private String menuItemId;
        private String menuItemName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }

    // ── API responses ─────────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchSummaryResponse {
        private String branchId;
        private LocalDate date;
        private Integer totalOrders;
        private Integer completedOrders;
        private Integer cancelledOrders;
        private Integer inProgressOrders;
        private BigDecimal totalRevenue;
        private BigDecimal avgOrderValue;
        private Integer dineInCount;
        private Integer takeawayCount;
        private Integer deliveryCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        private LocalDate date;
        private long totalOrdersToday;
        private BigDecimal totalRevenueToday;
        private long completedOrdersToday;
        private long cancelledOrdersToday;
        private List<BranchSummaryResponse> branchSummaries;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderAnalyticsResponse {
        private Long id;
        private String orderId;
        private String branchId;
        private String customerId;
        private String status;
        private String orderType;
        private BigDecimal totalAmount;
        private Integer itemCount;
        private String orderReceivedAt;
        private String lastUpdatedAt;
    }
}
