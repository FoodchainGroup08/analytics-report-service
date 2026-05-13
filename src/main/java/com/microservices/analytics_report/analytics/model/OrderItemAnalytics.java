package com.microservices.analytics_report.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_item_analytics", indexes = {
        @Index(name = "idx_oia_branch_date",  columnList = "branch_id, order_received_at"),
        @Index(name = "idx_oia_order_id",     columnList = "order_id"),
        @Index(name = "idx_oia_menu_item_id", columnList = "menu_item_id")
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

    @Column(name = "menu_item_name", nullable = false)
    private String menuItemName;

    @Column(name = "category")
    private String category;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "order_status", nullable = false, length = 15)
    private String orderStatus;

    @Column(name = "order_received_at", nullable = false)
    private LocalDateTime orderReceivedAt;
}
