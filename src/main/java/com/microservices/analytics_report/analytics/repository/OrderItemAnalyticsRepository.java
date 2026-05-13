package com.microservices.analytics_report.analytics.repository;

import com.microservices.analytics_report.analytics.model.OrderItemAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemAnalyticsRepository extends JpaRepository<OrderItemAnalytics, Long> {

    /** Aggregate item popularity for a single branch over a time window. */
    @Query("""
        SELECT i.menuItemId   AS menuItemId,
               i.menuItemName AS menuItemName,
               SUM(i.quantity)  AS totalQuantity,
               SUM(i.lineTotal) AS totalRevenue
        FROM OrderItemAnalytics i
        WHERE i.branchId = :branchId
          AND i.orderReceivedAt BETWEEN :start AND :end
        GROUP BY i.menuItemId, i.menuItemName
        ORDER BY SUM(i.quantity) DESC
        """)
    List<ItemPopularityProjection> findTopItemsByBranch(
            @Param("branchId") String branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate item popularity across all branches over a time window. */
    @Query("""
        SELECT i.menuItemId   AS menuItemId,
               i.menuItemName AS menuItemName,
               SUM(i.quantity)  AS totalQuantity,
               SUM(i.lineTotal) AS totalRevenue
        FROM OrderItemAnalytics i
        WHERE i.orderReceivedAt BETWEEN :start AND :end
        GROUP BY i.menuItemId, i.menuItemName
        ORDER BY SUM(i.quantity) DESC
        """)
    List<ItemPopularityProjection> findTopItemsAllBranches(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Mark all items for a given order as belonging to a completed order. */
    @Modifying
    @Query("UPDATE OrderItemAnalytics i SET i.orderCompleted = true WHERE i.orderId = :orderId")
    void markOrderCompleted(@Param("orderId") String orderId);

    /** Projection returned by popularity queries. */
    interface ItemPopularityProjection {
        String getMenuItemId();
        String getMenuItemName();
        Long getTotalQuantity();
        BigDecimal getTotalRevenue();
    }
}
