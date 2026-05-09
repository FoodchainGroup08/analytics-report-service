package com.microservices.analytics_report.report.service;

import com.microservices.analytics_report.report.dto.ReportDtos;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ReportService {

    ReportDtos.ReportResponse generateReport(ReportDtos.GenerateReportRequest request);

    Page<ReportDtos.ReportListResponse> listReports(int page, int size);

    ReportDtos.ReportResponse getReport(Long reportId);

    List<ReportDtos.ReportResponse> getReportsByBranch(String branchId);
}
