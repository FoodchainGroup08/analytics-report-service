package com.microservices.analytics_report.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_analytics", indexes = {
        @Index(name = "idx_oa_branch_id",   columnList = "branch_id"),
        @Index(name = "idx_oa_customer_id", columnList = "customer_id"),
        @Index(name = "idx_oa_received_at", columnList = "order_received_at"),
        @Index(name = "idx_oa_branch_date", columnList = "branch_id, order_received_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String orderId;

    @Column(name = "branch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String branchId;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private String customerId;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Column(name = "order_received_at", nullable = false)
    private LocalDateTime orderReceivedAt;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (this.lastUpdatedAt == null) this.lastUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdatedAt = LocalDateTime.now();
    }
}
