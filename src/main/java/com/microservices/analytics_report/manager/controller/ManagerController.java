package com.microservices.analytics_report.manager.controller;

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

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@Slf4j
@RestController
@RequestMapping("/v1/manager")
@RequiredArgsConstructor
@Tag(name = "Manager Dashboard", description = "Branch manager endpoints for dashboard metrics, live orders, sales analytics, and popular items.")
public class ManagerController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @Operation(
            summary = "Manager dashboard summary",
            description = "Returns key metrics for the manager's branch. Defaults to today; pass ?date= to query a past date.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.ManagerDashboardResponse> getDashboard(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        if (branchId == null) return ResponseEntity.badRequest().build();
        LocalDate target = date != null ? date : LocalDate.now();
        log.info("GET /manager/dashboard branchId={} date={}", branchId, target);
        return ResponseEntity.ok(analyticsService.getManagerDashboard(branchId, target));
    }

    @GetMapping("/orders/live")
    @Operation(
            summary = "Live orders for branch",
            description = "Returns all currently active orders at the manager's branch.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.LiveOrderResponse>> getLiveOrders(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchId) {
        if (branchId == null) return ResponseEntity.badRequest().build();
        log.info("GET /manager/orders/live branchId={}", branchId);
        return ResponseEntity.ok(analyticsService.getManagerLiveOrders(branchId));
    }

    @GetMapping("/sales/daily")
    @Operation(
            summary = "Daily sales breakdown",
            description = "Returns hourly revenue and order counts for the specified date (defaults to today).",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.HourlySalesResponse>> getDailySales(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        if (branchId == null) return ResponseEntity.badRequest().build();
        LocalDate target = date != null ? date : LocalDate.now();
        log.info("GET /manager/sales/daily branchId={} date={}", branchId, target);
        return ResponseEntity.ok(analyticsService.getDailySales(branchId, target));
    }

    @GetMapping("/items/popular")
    @Operation(
            summary = "Popular menu items",
            description = "Returns top-selling menu items for the manager's branch. Defaults to today; pass ?date= for a past date.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.PopularItemResponse>> getPopularItems(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date) {
        if (branchId == null) return ResponseEntity.badRequest().build();
        LocalDate target = date != null ? date : LocalDate.now();
        log.info("GET /manager/items/popular branchId={} date={}", branchId, target);
        return ResponseEntity.ok(analyticsService.getPopularItems(branchId, target));
    }

    @GetMapping("/summary/history")
    @Operation(
            summary = "Historical daily summaries",
            description = "Returns per-day order and revenue summaries for the manager's branch over a date range. Defaults to the last 30 days.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.BranchSummaryResponse>> getHistory(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate to) {
        if (branchId == null) return ResponseEntity.badRequest().build();
        LocalDate toDate   = to   != null ? to   : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);
        log.info("GET /manager/summary/history branchId={} from={} to={}", branchId, fromDate, toDate);
        return ResponseEntity.ok(analyticsService.getBranchSummaries(branchId, fromDate, toDate));
    }
}
