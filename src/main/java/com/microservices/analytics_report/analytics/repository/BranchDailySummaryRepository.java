package com.microservices.analytics_report.analytics.repository;

import com.microservices.analytics_report.analytics.model.BranchDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchDailySummaryRepository extends JpaRepository<BranchDailySummary, Long> {

    Optional<BranchDailySummary> findByBranchIdAndSummaryDate(String branchId, LocalDate summaryDate);

    List<BranchDailySummary> findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(
            String branchId, LocalDate from, LocalDate to);

    List<BranchDailySummary> findBySummaryDateOrderByTotalRevenueDesc(LocalDate summaryDate);
}
