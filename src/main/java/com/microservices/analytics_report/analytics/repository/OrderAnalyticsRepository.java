package com.microservices.analytics_report.analytics.repository;

import com.microservices.analytics_report.analytics.model.OrderAnalytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderAnalyticsRepository extends JpaRepository<OrderAnalytics, Long> {

    Optional<OrderAnalytics> findByOrderId(String orderId);

    Page<OrderAnalytics> findByBranchIdOrderByOrderReceivedAtDesc(String branchId, Pageable pageable);

    List<OrderAnalytics> findByOrderReceivedAtBetween(LocalDateTime start, LocalDateTime end);

    List<OrderAnalytics> findByBranchIdAndOrderReceivedAtBetween(String branchId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(o) FROM OrderAnalytics o WHERE o.branchId = :branchId AND o.orderReceivedAt BETWEEN :start AND :end")
    long countByBranchAndPeriod(@Param("branchId") String branchId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderAnalytics o WHERE o.branchId = :branchId AND o.status = 'COMPLETED' AND o.orderReceivedAt BETWEEN :start AND :end")
    java.math.BigDecimal sumRevenueByBranchAndPeriod(@Param("branchId") String branchId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);
}
