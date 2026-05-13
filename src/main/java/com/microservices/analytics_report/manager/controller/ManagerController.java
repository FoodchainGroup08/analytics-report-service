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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/manager")
@RequiredArgsConstructor
@Tag(name = "Manager Dashboard", description = "Branch manager analytics: today's metrics, live orders, sales, and popular items.")
public class ManagerController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Manager dashboard summary",
               description = "Today's key metrics for the manager's branch: total orders, revenue, and trends vs yesterday.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.ManagerDashboardResponse> getDashboard(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        log.info("GET /manager/dashboard branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getManagerDashboard(bid, LocalDate.now()));
    }

    @GetMapping("/orders/live")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Live orders for branch",
               description = "All currently active orders at the manager's branch (status: RECEIVED, PREPARING, READY).",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.LiveOrderResponse>> getLiveOrders(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        log.info("GET /manager/orders/live branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getManagerLiveOrders(bid));
    }

    @GetMapping("/sales/daily")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Daily sales breakdown",
               description = "Hourly revenue and order counts for the specified date (defaults to today).",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.HourlySalesResponse>> getDailySales(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        log.info("GET /manager/sales/daily branchId={} date={}", bid, targetDate);
        return ResponseEntity.ok(analyticsService.getDailySales(bid, targetDate));
    }

    @GetMapping("/items/popular")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Popular menu items",
               description = "Top-selling menu items for the manager's branch today.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.PopularItemResponse>> getPopularItems(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        log.info("GET /manager/items/popular branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getPopularItems(bid, LocalDate.now()));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Branch trend analytics",
               description = "Revenue, orders, completion rate for this branch grouped by interval (DAY, WEEK, MONTH).",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.TrendsResponse> getBranchTrends(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "DAY") String interval) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /manager/trends branchId={} from={} to={} interval={}", bid, start, end, interval);
        return ResponseEntity.ok(analyticsService.getTrends(bid, start, end, interval));
    }

    @GetMapping("/operational")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'HEAD_OFFICE_ADMIN')")
    @Operation(summary = "Branch operational analytics",
               description = "Orders by status, orders by hour of day, and peak hour for this branch.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.OperationalAnalyticsResponse> getBranchOperational(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null || bid.isBlank()) return ResponseEntity.badRequest().build();
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        log.info("GET /manager/operational branchId={} from={} to={}", bid, start, end);
        return ResponseEntity.ok(analyticsService.getOperationalAnalytics(bid, start, end));
    }
}
