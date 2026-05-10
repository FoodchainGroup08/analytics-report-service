package com.microservices.analytics_report.analytics.controller;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Order analytics and branch performance data. " +
        "Aggregates Kafka order events into daily summaries per branch.")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(
            summary = "Dashboard — all branches for a date",
            description = "Returns a high-level overview of all branches for a given date: total orders, " +
                    "total revenue, completed and cancelled counts, plus a per-branch breakdown. " +
                    "Defaults to today if no date is supplied.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard data returned")
    })
    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsDtos.DashboardResponse> getDashboard(
            @Parameter(description = "Date to query (yyyy-MM-dd). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("GET analytics dashboard date={}", date);
        return ResponseEntity.ok(analyticsService.getDashboard(date));
    }

    @Operation(
            summary = "Branch summary for a specific date",
            description = "Returns the daily summary for one branch on the given date. " +
                    "Returns 404 if no orders were placed at that branch on that day.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch daily summary"),
            @ApiResponse(responseCode = "404", description = "No summary available for this branch on this date")
    })
    @GetMapping("/branch/{branchId}/summary")
    public ResponseEntity<AnalyticsDtos.BranchSummaryResponse> getBranchSummary(
            @Parameter(description = "Branch UUID", required = true) @PathVariable String branchId,
            @Parameter(description = "Date to query (yyyy-MM-dd). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("GET branch summary branchId={} date={}", branchId, date);
        return ResponseEntity.ok(analyticsService.getBranchSummaryForDate(branchId, date));
    }

    @Operation(
            summary = "Branch summaries for a date range",
            description = "Returns a list of daily summaries for one branch between two dates (inclusive). " +
                    "Sorted oldest-first. Use this to build trend charts.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of daily summaries")
    })
    @GetMapping("/branch/{branchId}/summaries")
    public ResponseEntity<List<AnalyticsDtos.BranchSummaryResponse>> getBranchSummaries(
            @Parameter(description = "Branch UUID", required = true) @PathVariable String branchId,
            @Parameter(description = "Start date (yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("GET branch summaries branchId={} from={} to={}", branchId, from, to);
        return ResponseEntity.ok(analyticsService.getBranchSummaries(branchId, from, to));
    }

    @Operation(
            summary = "Individual order records for a branch",
            description = "Returns paginated raw analytics records for every order placed at a branch, " +
                    "newest first. Useful for auditing and drilling into specific orders.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated order analytics records")
    })
    @GetMapping("/branch/{branchId}/orders")
    public ResponseEntity<Page<AnalyticsDtos.OrderAnalyticsResponse>> getBranchOrders(
            @Parameter(description = "Branch UUID", required = true) @PathVariable String branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET branch orders branchId={} page={}", branchId, page);
        return ResponseEntity.ok(analyticsService.getBranchOrders(branchId, page, size));
    }

    @Operation(
            summary = "Trigger daily rollup manually",
            description = "Forces recalculation of BranchDailySummary for a given date. " +
                    "Normally this runs automatically at midnight. Use this to backfill or correct a specific date.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rollup triggered")
    })
    @PostMapping("/rollup")
    public ResponseEntity<String> triggerRollup(
            @Parameter(description = "Date to recompute (yyyy-MM-dd). Defaults to yesterday.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now().minusDays(1);
        log.info("Manual rollup triggered for {}", target);
        analyticsService.computeDailySummaries(target);
        return ResponseEntity.ok("Daily summary computed for " + target);
    }
}
