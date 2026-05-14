package com.microservices.analytics_report.analytics.service;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    void recordOrderReceived(AnalyticsDtos.OrderReceivedEvent event);

    void updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent event);

    AnalyticsDtos.DashboardResponse getDashboard(LocalDate date);

    AnalyticsDtos.BranchSummaryResponse getBranchSummaryForDate(String branchId, LocalDate date);

    List<AnalyticsDtos.BranchSummaryResponse> getBranchSummaries(String branchId, LocalDate from, LocalDate to);

    Page<AnalyticsDtos.OrderAnalyticsResponse> getBranchOrders(String branchId, int page, int size);

    void computeDailySummaries(LocalDate date);

    // ── Manager endpoints ─────────────────────────────────────────────────────

    AnalyticsDtos.ManagerDashboardResponse getManagerDashboard(String branchId, LocalDate date);

    List<AnalyticsDtos.LiveOrderResponse> getManagerLiveOrders(String branchId);

    List<AnalyticsDtos.HourlySalesResponse> getDailySales(String branchId, LocalDate date);

    List<AnalyticsDtos.PopularItemResponse> getPopularItems(String branchId, LocalDate date);

    // ── Admin endpoints ───────────────────────────────────────────────────────

    AnalyticsDtos.AdminAnalyticsResponse getAdminAnalytics(LocalDate startDate, LocalDate endDate);

    List<AnalyticsDtos.BranchAnalyticsResponse> getBranchAnalytics(LocalDate startDate, LocalDate endDate);

    // ── Enriched Head Office Admin endpoints ──────────────────────────────────

    default AnalyticsDtos.OverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        return getOverview(null, startDate, endDate);
    }

    AnalyticsDtos.OverviewResponse getOverview(String branchId, LocalDate startDate, LocalDate endDate);

    List<AnalyticsDtos.BranchComparisonResponse> getBranchComparison(LocalDate startDate, LocalDate endDate);

    AnalyticsDtos.TrendsResponse getTrends(String branchId, LocalDate startDate, LocalDate endDate, String interval);

    AnalyticsDtos.OperationalAnalyticsResponse getOperationalAnalytics(String branchId, LocalDate startDate, LocalDate endDate);

    List<AnalyticsDtos.PopularItemResponse> getPopularItemsForPeriod(String branchId, LocalDate startDate, LocalDate endDate, int limit);
}
