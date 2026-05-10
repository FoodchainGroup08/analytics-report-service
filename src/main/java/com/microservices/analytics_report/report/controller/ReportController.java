package com.microservices.analytics_report.report.controller;

import com.microservices.analytics_report.report.dto.ReportDtos;
import com.microservices.analytics_report.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Generate and retrieve business reports built from aggregated analytics data. " +
        "Reports capture a snapshot of branch performance over a chosen date range.")
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "Generate a new report",
            description = "Aggregates BranchDailySummary records for the given date range and persists a report. " +
                    "Leave `branchId` empty to generate a company-wide report across all branches. " +
                    "Report types: BRANCH_PERFORMANCE, SALES_SUMMARY, ORDER_SUMMARY.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report generated and saved"),
            @ApiResponse(responseCode = "400", description = "Missing required fields or invalid date range")
    })
    @PostMapping("/generate")
    public ResponseEntity<ReportDtos.ReportResponse> generateReport(
            @RequestBody ReportDtos.GenerateReportRequest request) {
        log.info("Generate report type={} branch={} period={}/{}",
                request.getReportType(), request.getBranchId(),
                request.getStartDate(), request.getEndDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.generateReport(request));
    }

    @Operation(
            summary = "List all reports",
            description = "Returns a paginated list of all previously generated reports, newest first.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated report list")
    })
    @GetMapping
    public ResponseEntity<Page<ReportDtos.ReportListResponse>> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET reports page={}", page);
        return ResponseEntity.ok(reportService.listReports(page, size));
    }

    @Operation(
            summary = "Get a report by ID",
            description = "Returns the full details of a previously generated report including all metrics and rates.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report details"),
            @ApiResponse(responseCode = "404", description = "Report not found")
    })
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportDtos.ReportResponse> getReport(
            @Parameter(description = "Report ID", required = true) @PathVariable Long reportId) {
        log.info("GET report id={}", reportId);
        return ResponseEntity.ok(reportService.getReport(reportId));
    }

    @Operation(
            summary = "Get all reports for a branch",
            description = "Returns all reports generated for a specific branch, newest first.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of reports for the branch")
    })
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<ReportDtos.ReportResponse>> getReportsByBranch(
            @Parameter(description = "Branch UUID", required = true) @PathVariable String branchId) {
        log.info("GET reports by branch branchId={}", branchId);
        return ResponseEntity.ok(reportService.getReportsByBranch(branchId));
    }
}
