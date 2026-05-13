package com.microservices.analytics_report.admin.controller;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "Organization-wide analytics and branch comparison for HEAD_OFFICE_ADMIN.")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    // ── Backward-compatible existing endpoints ────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "System-wide analytics (legacy)",
               description = "Aggregated metrics across all branches. Use /overview for the enriched version.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.AdminAnalyticsResponse> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getAdminAnalytics(start, end));
    }

    @GetMapping("/branches")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Per-branch analytics (legacy)",
               description = "Orders and revenue per branch. Use /compare for the enriched comparison view.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.BranchAnalyticsResponse>> getBranchAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/branches from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getBranchAnalytics(start, end));
    }

    // ── Enriched Head Office endpoints ────────────────────────────────────────

    @GetMapping("/overview")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Head Office overview",
               description = "Unified org-wide metrics: revenue, orders, growth %, completion rate, " +
                             "top/fastest/slowest branch. Defaults to last 30 days.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.OverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/overview from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getOverview(start, end));
    }

    @GetMapping("/compare")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Branch comparison",
               description = "Side-by-side comparison of all branches: revenue, orders, avg order value, " +
                             "completion rate, avg prep time, and top items.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.BranchComparisonResponse>> getBranchComparison(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/compare from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getBranchComparison(start, end));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Org-wide trends",
               description = "Revenue, orders, completion rate over time grouped by interval (DAY, WEEK, MONTH).",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.TrendsResponse> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "DAY") String interval) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/trends from={} to={} interval={}", start, end, interval);
        return ResponseEntity.ok(analyticsService.getTrends(null, start, end, interval));
    }

    @GetMapping("/operational")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Org-wide operational analytics",
               description = "Orders by status, orders by hour of day, peak hour across all branches.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.OperationalAnalyticsResponse> getOperational(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/operational from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getOperationalAnalytics(null, start, end));
    }

    @GetMapping("/popular-items")
    @PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Org-wide popular items",
               description = "Top-selling menu items across all branches for the period.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.PopularItemResponse>> getPopularItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /admin/analytics/popular-items from={} to={} limit={}", start, end, limit);
        return ResponseEntity.ok(analyticsService.getPopularItemsForPeriod(null, start, end, limit));
    }
}
