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
        private String category;
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

    // ── Manager dashboard responses ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ManagerDashboardResponse {
        private long totalOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private double ordersChange;
        private double revenueChange;
        private double averagePrepTime;
        private String peakHour;
        private long peakHourOrders;
        private double completionRate;
        private long dineInCount;
        private long takeawayCount;
        private long deliveryCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HourlySalesResponse {
        private String hour;
        private double revenue;
        private long orders;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PopularItemResponse {
        private String id;
        private String name;
        private String category;
        private long quantitySold;
        private double revenue;
        private double trend;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LiveOrderResponse {
        private String id;
        private String status;
        private String orderType;
        private String tableNumber;
        private String customerName;
        private String placedAt;
        private double total;
        private int itemCount;
        private List<LiveOrderItemDto> items;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LiveOrderItemDto {
        private String id;
        private String name;
        private int quantity;
    }

    // ── Admin analytics responses ──────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AdminAnalyticsResponse {
        private long totalOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private long totalBranches;
        private long totalCustomers;
        private List<HourlySalesResponse> dailyBreakdown;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchAnalyticsResponse {
        private String id;
        private String name;
        private long orders;
        private double revenue;
    }
}
