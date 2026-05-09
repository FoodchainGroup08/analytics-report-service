package com.microservices.analytics_report.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "branch_daily_summary", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_date", columnNames = {"branch_id", "summary_date"})
}, indexes = {
        @Index(name = "idx_bds_branch_id",    columnList = "branch_id"),
        @Index(name = "idx_bds_summary_date", columnList = "summary_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchDailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String branchId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "completed_orders", nullable = false)
    @Builder.Default
    private Integer completedOrders = 0;

    @Column(name = "cancelled_orders", nullable = false)
    @Builder.Default
    private Integer cancelledOrders = 0;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "avg_order_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal avgOrderValue = BigDecimal.ZERO;

    @Column(name = "dine_in_count", nullable = false)
    @Builder.Default
    private Integer dineInCount = 0;

    @Column(name = "takeaway_count", nullable = false)
    @Builder.Default
    private Integer takeawayCount = 0;

    @Column(name = "delivery_count", nullable = false)
    @Builder.Default
    private Integer deliveryCount = 0;
}
