package com.microservices.analytics_report.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores one row per item per order so we can aggregate item-level popularity.
 * Populated during order.received Kafka ingest alongside OrderAnalytics.
 * orderCompleted is flipped to true on order.status.updated = COMPLETED so
 * revenue queries can filter to only completed orders.
 */
@Entity
@Table(name = "order_item_analytics", indexes = {
        @Index(name = "idx_oia_order_id",       columnList = "order_id"),
        @Index(name = "idx_oia_branch_item",     columnList = "branch_id, menu_item_id"),
        @Index(name = "idx_oia_branch_received", columnList = "branch_id, order_received_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private String orderId;

    @Column(name = "branch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String branchId;

    @Column(name = "menu_item_id", nullable = false, columnDefinition = "CHAR(36)")
    private String menuItemId;

    @Column(name = "menu_item_name", nullable = false, length = 255)
    private String menuItemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "order_received_at", nullable = false)
    private LocalDateTime orderReceivedAt;

    @Column(name = "order_completed", nullable = false)
    @Builder.Default
    private Boolean orderCompleted = false;
}
