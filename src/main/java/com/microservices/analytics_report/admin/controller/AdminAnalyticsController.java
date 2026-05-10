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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "System-wide analytics and reporting for platform administrators.")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    @Operation(
            summary = "System-wide analytics",
            description = "Returns aggregated metrics across all branches for the specified date range.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.AdminAnalyticsResponse> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        log.info("GET /admin/analytics from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getAdminAnalytics(start, end));
    }

    @GetMapping("/branches")
    @Operation(
            summary = "Per-branch analytics",
            description = "Returns orders and revenue broken down by branch for the specified date range.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.BranchAnalyticsResponse>> getBranchAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        log.info("GET /admin/analytics/branches from={} to={}", start, end);
        return ResponseEntity.ok(analyticsService.getBranchAnalytics(start, end));
    }
}
