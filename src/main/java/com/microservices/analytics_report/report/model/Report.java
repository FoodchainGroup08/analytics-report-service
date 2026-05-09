package com.microservices.analytics_report.report.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_report_branch_id",    columnList = "branch_id"),
        @Index(name = "idx_report_generated_at", columnList = "generated_at"),
        @Index(name = "idx_report_type",         columnList = "report_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private String branchId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "completed_orders")
    @Builder.Default
    private Integer completedOrders = 0;

    @Column(name = "cancelled_orders")
    @Builder.Default
    private Integer cancelledOrders = 0;

    @Column(name = "total_revenue", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "avg_order_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal avgOrderValue = BigDecimal.ZERO;

    @Column(name = "dine_in_count")
    @Builder.Default
    private Integer dineInCount = 0;

    @Column(name = "takeaway_count")
    @Builder.Default
    private Integer takeawayCount = 0;

    @Column(name = "delivery_count")
    @Builder.Default
    private Integer deliveryCount = 0;

    @PrePersist
    protected void onCreate() {
        if (this.generatedAt == null) this.generatedAt = LocalDateTime.now();
    }
}
