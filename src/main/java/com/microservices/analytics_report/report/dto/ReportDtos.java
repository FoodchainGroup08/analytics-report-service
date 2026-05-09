package com.microservices.analytics_report.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReportDtos {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GenerateReportRequest {
        private String reportType;  // BRANCH_PERFORMANCE, SALES_SUMMARY, ORDER_SUMMARY
        private String branchId;    // null = all branches
        private LocalDate startDate;
        private LocalDate endDate;
        private String requestedBy;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReportResponse {
        private Long id;
        private String reportType;
        private String branchId;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private Integer totalOrders;
        private Integer completedOrders;
        private Integer cancelledOrders;
        private Integer inProgressOrders;
        private BigDecimal totalRevenue;
        private BigDecimal avgOrderValue;
        private Integer dineInCount;
        private Integer takeawayCount;
        private Integer deliveryCount;
        private double completionRate;
        private double cancellationRate;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReportListResponse {
        private Long id;
        private String reportType;
        private String branchId;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private BigDecimal totalRevenue;
        private Integer totalOrders;
    }
}
