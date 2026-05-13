package com.microservices.analytics_report.analytics.repository;

import com.microservices.analytics_report.analytics.model.OrderItemAnalytics;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemAnalyticsRepository extends JpaRepository<OrderItemAnalytics, Long> {

    @Modifying
    @Query("UPDATE OrderItemAnalytics o SET o.orderStatus = :status WHERE o.orderId = :orderId")
    void updateStatusByOrderId(@Param("orderId") String orderId, @Param("status") String status);

    /** Per-branch popular items for a single date range (used by manager dashboard). */
    @Query("""
            SELECT o.menuItemId, o.menuItemName, MAX(o.category), SUM(o.quantity), SUM(o.lineTotal)
            FROM OrderItemAnalytics o
            WHERE o.branchId = :branchId
              AND o.orderStatus = 'COMPLETED'
              AND o.orderReceivedAt BETWEEN :start AND :end
            GROUP BY o.menuItemId, o.menuItemName
            ORDER BY SUM(o.quantity) DESC
            """)
    List<Object[]> findPopularItems(@Param("branchId") String branchId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** Org-wide popular items across all branches for a period. Uses Pageable for LIMIT. */
    @Query("""
            SELECT o.menuItemId, o.menuItemName, MAX(o.category), SUM(o.quantity), SUM(o.lineTotal)
            FROM OrderItemAnalytics o
            WHERE o.orderStatus = 'COMPLETED'
              AND o.orderReceivedAt BETWEEN :start AND :end
            GROUP BY o.menuItemId, o.menuItemName
            ORDER BY SUM(o.quantity) DESC
            """)
    List<Object[]> findPopularItemsGlobal(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          Pageable pageable);

    /** Per-branch popular items for a multi-day period. Uses Pageable for LIMIT. */
    @Query("""
            SELECT o.menuItemId, o.menuItemName, MAX(o.category), SUM(o.quantity), SUM(o.lineTotal)
            FROM OrderItemAnalytics o
            WHERE o.branchId = :branchId
              AND o.orderStatus = 'COMPLETED'
              AND o.orderReceivedAt BETWEEN :start AND :end
            GROUP BY o.menuItemId, o.menuItemName
            ORDER BY SUM(o.quantity) DESC
            """)
    List<Object[]> findPopularItemsByBranchPeriod(@Param("branchId") String branchId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  Pageable pageable);
}
