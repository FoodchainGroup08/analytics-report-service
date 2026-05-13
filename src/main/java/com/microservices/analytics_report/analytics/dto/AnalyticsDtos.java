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

    // ── Core API responses (backward-compatible) ──────────────────────────────

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
        /** Seconds; null when no completed orders on this day. */
        private Long avgPreparationTimeSeconds;
        private BigDecimal completionRate;
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

    // ── Manager dashboard responses ───────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ManagerDashboardResponse {
        private long totalOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private double ordersChange;
        private double revenueChange;
        /** Average preparation time in minutes for today. */
        private double avgPreparationTimeMinutes;
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
        private long quantitySold;
        private double revenue;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LiveOrderResponse {
        private String id;
        private String status;
        private String orderType;
        private String tableNumber;
        private String customerName;
        private String createdAt;
        private double totalAmount;
        private int itemCount;
    }

    // ── Admin analytics responses (enriched) ─────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AdminAnalyticsResponse {
        private long totalOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private long totalBranches;
        private long totalCustomers;
        private double completionRate;
        private double cancellationRate;
        /** Revenue growth % vs the equivalent prior period. */
        private double revenueGrowthPercent;
        /** Orders growth % vs the equivalent prior period. */
        private double ordersGrowthPercent;
        private String topPerformingBranch;
        private String fastestBranch;
        private String slowestBranch;
        private List<HourlySalesResponse> dailyBreakdown;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchAnalyticsResponse {
        private String id;
        private String name;
        private long orders;
        private double revenue;
        private double avgOrderValue;
        private double completionRate;
        private double cancellationRate;
        /** Average preparation time in minutes; null when no completed orders. */
        private Double avgPreparationTimeMinutes;
        private List<PopularItemResponse> topItems;
    }

    // ── Overview response ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OverviewResponse {
        private LocalDate startDate;
        private LocalDate endDate;
        private long totalOrders;
        private BigDecimal totalRevenue;
        private BigDecimal avgOrderValue;
        private double completionRate;
        private double cancellationRate;
        private double revenueGrowthPercent;
        private double ordersGrowthPercent;
        private String topPerformingBranch;
        private String fastestBranch;
        private String slowestBranch;
        private long totalBranches;
    }

    // ── Branch comparison response ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchComparisonResponse {
        private String branchId;
        private long totalOrders;
        private BigDecimal totalRevenue;
        private BigDecimal avgOrderValue;
        private double completionRate;
        private double cancellationRate;
        private Double avgPreparationTimeMinutes;
        private List<PopularItemResponse> topItems;
    }

    // ── Trends response ───────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TrendDataPoint {
        /** e.g. "2024-01-15" (DAY), "2024-W03" (WEEK), "2024-01" (MONTH). */
        private String period;
        private BigDecimal revenue;
        private long orders;
        private Double avgPreparationTimeMinutes;
        private Double completionRate;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TrendsResponse {
        private LocalDate startDate;
        private LocalDate endDate;
        private String interval;
        private String branchId;
        private List<TrendDataPoint> dataPoints;
    }

    // ── Operational analytics response ────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrdersByStatusEntry {
        private String status;
        private long count;
        private double percentage;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OperationalAnalyticsResponse {
        private List<OrdersByStatusEntry> ordersByStatus;
        private List<HourlySalesResponse> ordersByHour;
        private String peakHour;
        private long totalOrders;
    }
}
