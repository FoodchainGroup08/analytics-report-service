package com.microservices.analytics_report.admin;

import com.microservices.analytics_report.admin.controller.AdminAnalyticsController;
import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import com.microservices.analytics_report.filter.GatewayAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyticsController.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    // GatewayAuthenticationFilter is a @Component — mock it so @WebMvcTest doesn't
    // try to wire its dependencies in the slice context.
    @MockBean
    private GatewayAuthenticationFilter gatewayAuthenticationFilter;

    // ── GET /admin/analytics ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getAnalytics_withDateRange_returns200() throws Exception {
        AnalyticsDtos.AdminAnalyticsResponse response = AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(500L).totalRevenue(15000.0).averageOrderValue(30.0)
                .totalBranches(5L).totalCustomers(0L).completionRate(85.0).cancellationRate(5.0)
                .revenueGrowthPercent(12.5).ordersGrowthPercent(8.0)
                .topPerformingBranch("branch-001").dailyBreakdown(List.of())
                .build();

        when(analyticsService.getAdminAnalytics(
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(500))
                .andExpect(jsonPath("$.totalRevenue").value(15000.0))
                .andExpect(jsonPath("$.completionRate").value(85.0))
                .andExpect(jsonPath("$.revenueGrowthPercent").value(12.5))
                .andExpect(jsonPath("$.topPerformingBranch").value("branch-001"))
                .andExpect(jsonPath("$.dailyBreakdown").isArray());
    }

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getAnalytics_noParams_defaultsToLast30Days_returns200() throws Exception {
        when(analyticsService.getAdminAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(AnalyticsDtos.AdminAnalyticsResponse.builder()
                        .totalOrders(0L).totalRevenue(0.0).dailyBreakdown(List.of()).build());

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void getAnalytics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BRANCH_MANAGER")
    void getAnalytics_branchManagerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api"))
                .andExpect(status().isForbidden());
    }

    // ── GET /admin/analytics/branches ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getBranchAnalytics_withDateRange_returns200() throws Exception {
        List<AnalyticsDtos.BranchAnalyticsResponse> branchList = List.of(
                AnalyticsDtos.BranchAnalyticsResponse.builder()
                        .id("branch-001").name("branch-001")
                        .orders(200L).revenue(6000.0)
                        .avgOrderValue(30.0).completionRate(88.0)
                        .topItems(List.of()).build(),
                AnalyticsDtos.BranchAnalyticsResponse.builder()
                        .id("branch-002").name("branch-002")
                        .orders(300L).revenue(9000.0).build());

        when(analyticsService.getBranchAnalytics(
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(branchList);

        mockMvc.perform(get("/api/v1/admin/analytics/branches").contextPath("/api")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("branch-001"))
                .andExpect(jsonPath("$[0].completionRate").value(88.0))
                .andExpect(jsonPath("$[1].revenue").value(9000.0));
    }

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getBranchAnalytics_noParams_returnsEmptyList() throws Exception {
        when(analyticsService.getBranchAnalytics(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/analytics/branches").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /admin/analytics/overview ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getOverview_returns200WithGrowthMetrics() throws Exception {
        when(analyticsService.getOverview(any(), any())).thenReturn(
                AnalyticsDtos.OverviewResponse.builder()
                        .startDate(LocalDate.now().minusDays(30)).endDate(LocalDate.now())
                        .totalOrders(1200L).totalRevenue(new BigDecimal("36000"))
                        .avgOrderValue(new BigDecimal("30")).completionRate(87.5)
                        .revenueGrowthPercent(15.0).ordersGrowthPercent(10.0)
                        .topPerformingBranch("branch-A").totalBranches(3L)
                        .build());

        mockMvc.perform(get("/api/v1/admin/analytics/overview").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1200))
                .andExpect(jsonPath("$.revenueGrowthPercent").value(15.0))
                .andExpect(jsonPath("$.topPerformingBranch").value("branch-A"));
    }

    // ── GET /admin/analytics/compare ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getBranchComparison_returns200WithAllBranches() throws Exception {
        when(analyticsService.getBranchComparison(any(), any())).thenReturn(List.of(
                AnalyticsDtos.BranchComparisonResponse.builder()
                        .branchId("branch-A").totalOrders(500L)
                        .totalRevenue(new BigDecimal("15000")).avgOrderValue(new BigDecimal("30"))
                        .completionRate(90.0).avgPreparationTimeMinutes(12.5)
                        .topItems(List.of()).build()));

        mockMvc.perform(get("/api/v1/admin/analytics/compare").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value("branch-A"))
                .andExpect(jsonPath("$[0].completionRate").value(90.0))
                .andExpect(jsonPath("$[0].avgPreparationTimeMinutes").value(12.5));
    }

    // ── GET /admin/analytics/trends ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getTrends_returns200WithDataPoints() throws Exception {
        when(analyticsService.getTrends(any(), any(), any(), any())).thenReturn(
                AnalyticsDtos.TrendsResponse.builder()
                        .startDate(LocalDate.now().minusDays(7)).endDate(LocalDate.now())
                        .interval("DAY")
                        .dataPoints(List.of(
                                AnalyticsDtos.TrendDataPoint.builder()
                                        .period("2026-05-01").revenue(new BigDecimal("1000"))
                                        .orders(30L).completionRate(85.0).build()))
                        .build());

        mockMvc.perform(get("/api/v1/admin/analytics/trends").contextPath("/api")
                        .param("interval", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("DAY"))
                .andExpect(jsonPath("$.dataPoints[0].period").value("2026-05-01"))
                .andExpect(jsonPath("$.dataPoints[0].orders").value(30));
    }

    // ── GET /admin/analytics/operational ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "HEAD_OFFICE_ADMIN")
    void getOperational_returns200WithPeakHour() throws Exception {
        when(analyticsService.getOperationalAnalytics(any(), any(), any())).thenReturn(
                AnalyticsDtos.OperationalAnalyticsResponse.builder()
                        .totalOrders(250L).peakHour("12:00")
                        .ordersByStatus(List.of(
                                AnalyticsDtos.OrdersByStatusEntry.builder()
                                        .status("COMPLETED").count(200L).percentage(80.0).build()))
                        .ordersByHour(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/admin/analytics/operational").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakHour").value("12:00"))
                .andExpect(jsonPath("$.ordersByStatus[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.ordersByStatus[0].percentage").value(80.0));
    }
}
