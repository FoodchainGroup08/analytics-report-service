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
            description = "Returns today's key metrics for the manager's branch: total orders, revenue, and trends vs yesterday.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<AnalyticsDtos.ManagerDashboardResponse> getDashboard(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null) return ResponseEntity.badRequest().build();
        log.info("GET /manager/dashboard branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getManagerDashboard(bid, LocalDate.now()));
    }

    @GetMapping("/orders/live")
    @Operation(
            summary = "Live orders for branch",
            description = "Returns all currently active orders at the manager's branch.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.LiveOrderResponse>> getLiveOrders(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null) return ResponseEntity.badRequest().build();
        log.info("GET /manager/orders/live branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getManagerLiveOrders(bid));
    }

    @GetMapping("/sales/daily")
    @Operation(
            summary = "Daily sales breakdown",
            description = "Returns hourly revenue and order counts for the specified date (defaults to today).",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.HourlySalesResponse>> getDailySales(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null) return ResponseEntity.badRequest().build();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        log.info("GET /manager/sales/daily branchId={} date={}", bid, targetDate);
        return ResponseEntity.ok(analyticsService.getDailySales(bid, targetDate));
    }

    @GetMapping("/items/popular")
    @Operation(
            summary = "Popular menu items",
            description = "Returns the top-selling menu items for the manager's branch today.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<List<AnalyticsDtos.PopularItemResponse>> getPopularItems(
            @RequestHeader(value = "X-User-BranchId", required = false) String branchIdHeader,
            @RequestParam(required = false) String branchId) {
        String bid = branchId != null ? branchId : branchIdHeader;
        if (bid == null) return ResponseEntity.badRequest().build();
        log.info("GET /manager/items/popular branchId={}", bid);
        return ResponseEntity.ok(analyticsService.getPopularItems(bid, LocalDate.now()));
    }
}
