package com.microservices.analytics_report.analytics.service;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    // ── Kafka ingest ──────────────────────────────────────────────────────────

    void recordOrderReceived(AnalyticsDtos.OrderReceivedEvent event);

    void updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent event);

    // ── Core analytics ────────────────────────────────────────────────────────

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

    // ── Admin / Head Office endpoints ─────────────────────────────────────────

    AnalyticsDtos.AdminAnalyticsResponse getAdminAnalytics(LocalDate startDate, LocalDate endDate);

    List<AnalyticsDtos.BranchAnalyticsResponse> getBranchAnalytics(LocalDate startDate, LocalDate endDate);

    /** Unified Head Office overview with growth metrics and rankings. */
    AnalyticsDtos.OverviewResponse getOverview(LocalDate startDate, LocalDate endDate);

    /** Side-by-side branch comparison for the given period. */
    List<AnalyticsDtos.BranchComparisonResponse> getBranchComparison(LocalDate startDate, LocalDate endDate);

    /** Revenue / orders / completion-rate trend points grouped by interval (DAY, WEEK, MONTH). */
    AnalyticsDtos.TrendsResponse getTrends(String branchId, LocalDate startDate, LocalDate endDate, String interval);

    /** Operational breakdown: orders by status, orders by hour, peak hour. */
    AnalyticsDtos.OperationalAnalyticsResponse getOperationalAnalytics(String branchId,
                                                                        LocalDate startDate,
                                                                        LocalDate endDate);

    /** Cross-branch or single-branch popular items for the period (top N). */
    List<AnalyticsDtos.PopularItemResponse> getPopularItemsForPeriod(String branchId,
                                                                      LocalDate startDate,
                                                                      LocalDate endDate,
                                                                      int limit);
}
