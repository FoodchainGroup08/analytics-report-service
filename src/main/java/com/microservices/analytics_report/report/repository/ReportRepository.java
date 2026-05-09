package com.microservices.analytics_report.report.repository;

import com.microservices.analytics_report.report.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    List<Report> findByBranchIdOrderByGeneratedAtDesc(String branchId);

    List<Report> findByReportTypeAndStartDateGreaterThanEqualAndEndDateLessThanEqualOrderByGeneratedAtDesc(
            String reportType, LocalDate from, LocalDate to);
}
