package com.microservices.analytics_report.report.service;

import com.microservices.analytics_report.analytics.model.BranchDailySummary;
import com.microservices.analytics_report.analytics.repository.BranchDailySummaryRepository;
import com.microservices.analytics_report.report.dto.ReportDtos;
import com.microservices.analytics_report.report.model.Report;
import com.microservices.analytics_report.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final BranchDailySummaryRepository branchDailySummaryRepository;

    @Override
    @Transactional
    public ReportDtos.ReportResponse generateReport(ReportDtos.GenerateReportRequest request) {
        List<BranchDailySummary> summaries;

        if (request.getBranchId() != null && !request.getBranchId().isBlank()) {
            summaries = branchDailySummaryRepository
                    .findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(
                            request.getBranchId(), request.getStartDate(), request.getEndDate());
        } else {
            // All branches across the period
            summaries = request.getStartDate().datesUntil(request.getEndDate().plusDays(1))
                    .flatMap(date -> branchDailySummaryRepository.findBySummaryDateOrderByTotalRevenueDesc(date).stream())
                    .collect(Collectors.toList());
        }

        int totalOrders    = summaries.stream().mapToInt(BranchDailySummary::getTotalOrders).sum();
        int completed      = summaries.stream().mapToInt(BranchDailySummary::getCompletedOrders).sum();
        int cancelled      = summaries.stream().mapToInt(BranchDailySummary::getCancelledOrders).sum();
        int dineIn         = summaries.stream().mapToInt(BranchDailySummary::getDineInCount).sum();
        int takeaway       = summaries.stream().mapToInt(BranchDailySummary::getTakeawayCount).sum();
        int delivery       = summaries.stream().mapToInt(BranchDailySummary::getDeliveryCount).sum();
        BigDecimal revenue = summaries.stream().map(BranchDailySummary::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg     = completed > 0
                ? revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Report report = Report.builder()
                .reportType(request.getReportType())
                .branchId(request.getBranchId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .generatedBy(request.getRequestedBy())
                .totalOrders(totalOrders)
                .completedOrders(completed)
                .cancelledOrders(cancelled)
                .totalRevenue(revenue)
                .avgOrderValue(avg)
                .dineInCount(dineIn)
                .takeawayCount(takeaway)
                .deliveryCount(delivery)
                .build();

        report = reportRepository.save(report);
        log.info("Generated report id={} type={} branch={} period={}/{}",
                report.getId(), report.getReportType(), report.getBranchId(),
                report.getStartDate(), report.getEndDate());

        return toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportDtos.ReportListResponse> listReports(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt"));
        return reportRepository.findAllByOrderByGeneratedAtDesc(pageable).map(this::toListResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found: " + reportId));
        return toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportDtos.ReportResponse> getReportsByBranch(String branchId) {
        return reportRepository.findByBranchIdOrderByGeneratedAtDesc(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ReportDtos.ReportResponse toResponse(Report r) {
        int inProgress = Math.max(r.getTotalOrders() - r.getCompletedOrders() - r.getCancelledOrders(), 0);
        double completionRate  = r.getTotalOrders() > 0
                ? (r.getCompletedOrders() * 100.0) / r.getTotalOrders() : 0.0;
        double cancellationRate = r.getTotalOrders() > 0
                ? (r.getCancelledOrders() * 100.0) / r.getTotalOrders() : 0.0;

        return ReportDtos.ReportResponse.builder()
                .id(r.getId())
                .reportType(r.getReportType())
                .branchId(r.getBranchId())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .generatedAt(r.getGeneratedAt())
                .generatedBy(r.getGeneratedBy())
                .totalOrders(r.getTotalOrders())
                .completedOrders(r.getCompletedOrders())
                .cancelledOrders(r.getCancelledOrders())
                .inProgressOrders(inProgress)
                .totalRevenue(r.getTotalRevenue())
                .avgOrderValue(r.getAvgOrderValue())
                .dineInCount(r.getDineInCount())
                .takeawayCount(r.getTakeawayCount())
                .deliveryCount(r.getDeliveryCount())
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .cancellationRate(Math.round(cancellationRate * 100.0) / 100.0)
                .build();
    }

    private ReportDtos.ReportListResponse toListResponse(Report r) {
        return ReportDtos.ReportListResponse.builder()
                .id(r.getId())
                .reportType(r.getReportType())
                .branchId(r.getBranchId())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .generatedAt(r.getGeneratedAt())
                .generatedBy(r.getGeneratedBy())
                .totalRevenue(r.getTotalRevenue())
                .totalOrders(r.getTotalOrders())
                .build();
    }
}
