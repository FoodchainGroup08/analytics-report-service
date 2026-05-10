package com.microservices.analytics_report.admin;

import com.microservices.analytics_report.admin.controller.AdminAnalyticsController;
import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

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

    // ── GET /admin/analytics ──────────────────────────────────────────────────

    @Test
    void getAnalytics_withDateRange_returns200() throws Exception {
        AnalyticsDtos.AdminAnalyticsResponse response = AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(500L)
                .totalRevenue(15000.0)
                .averageOrderValue(30.0)
                .totalBranches(5L)
                .totalCustomers(0L)
                .dailyBreakdown(List.of())
                .build();

        when(analyticsService.getAdminAnalytics(
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(500))
                .andExpect(jsonPath("$.totalRevenue").value(15000.0))
                .andExpect(jsonPath("$.averageOrderValue").value(30.0))
                .andExpect(jsonPath("$.totalBranches").value(5))
                .andExpect(jsonPath("$.totalCustomers").value(0))
                .andExpect(jsonPath("$.dailyBreakdown").isArray());
    }

    @Test
    void getAnalytics_noParams_defaultsToLast30Days_returns200() throws Exception {
        AnalyticsDtos.AdminAnalyticsResponse response = AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(0L)
                .totalRevenue(0.0)
                .averageOrderValue(0.0)
                .totalBranches(0L)
                .totalCustomers(0L)
                .dailyBreakdown(List.of())
                .build();

        when(analyticsService.getAdminAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void getAnalytics_withDailyBreakdown_returns200() throws Exception {
        List<AnalyticsDtos.HourlySalesResponse> breakdown = List.of(
                AnalyticsDtos.HourlySalesResponse.builder()
                        .hour("09:00").revenue(500.0).orders(15L).build()
        );

        AnalyticsDtos.AdminAnalyticsResponse response = AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(15L)
                .totalRevenue(500.0)
                .averageOrderValue(33.33)
                .totalBranches(2L)
                .totalCustomers(0L)
                .dailyBreakdown(breakdown)
                .build();

        when(analyticsService.getAdminAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyBreakdown[0].hour").value("09:00"))
                .andExpect(jsonPath("$.dailyBreakdown[0].revenue").value(500.0))
                .andExpect(jsonPath("$.dailyBreakdown[0].orders").value(15));
    }

    // ── GET /admin/analytics/branches ─────────────────────────────────────────

    @Test
    void getBranchAnalytics_withDateRange_returns200() throws Exception {
        List<AnalyticsDtos.BranchAnalyticsResponse> branchList = List.of(
                AnalyticsDtos.BranchAnalyticsResponse.builder()
                        .id("branch-001")
                        .name("branch-001")
                        .orders(200L)
                        .revenue(6000.0)
                        .build(),
                AnalyticsDtos.BranchAnalyticsResponse.builder()
                        .id("branch-002")
                        .name("branch-002")
                        .orders(300L)
                        .revenue(9000.0)
                        .build()
        );

        when(analyticsService.getBranchAnalytics(
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(branchList);

        mockMvc.perform(get("/api/v1/admin/analytics/branches").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("branch-001"))
                .andExpect(jsonPath("$[0].name").value("branch-001"))
                .andExpect(jsonPath("$[0].orders").value(200))
                .andExpect(jsonPath("$[0].revenue").value(6000.0))
                .andExpect(jsonPath("$[1].id").value("branch-002"))
                .andExpect(jsonPath("$[1].revenue").value(9000.0));
    }

    @Test
    void getBranchAnalytics_noParams_defaultsToLast30Days_returns200() throws Exception {
        when(analyticsService.getBranchAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/analytics/branches").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getBranchAnalytics_singleBranch_returns200() throws Exception {
        List<AnalyticsDtos.BranchAnalyticsResponse> branchList = List.of(
                AnalyticsDtos.BranchAnalyticsResponse.builder()
                        .id("branch-xyz")
                        .name("branch-xyz")
                        .orders(50L)
                        .revenue(1500.0)
                        .build()
        );

        when(analyticsService.getBranchAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(branchList);

        mockMvc.perform(get("/api/v1/admin/analytics/branches").contextPath("/api")
                        .header("X-User-Role", "HEAD_OFFICE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("branch-xyz"));
    }

    @Test
    void getAnalytics_withoutRoleHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getAnalytics_withCustomerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAnalytics_displayNameAdmin_isAccepted() throws Exception {
        AnalyticsDtos.AdminAnalyticsResponse response = AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(1L)
                .totalRevenue(10.0)
                .averageOrderValue(10.0)
                .totalBranches(1L)
                .totalCustomers(0L)
                .dailyBreakdown(List.of())
                .build();

        when(analyticsService.getAdminAnalytics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics").contextPath("/api")
                        .header("X-User-Role", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1));
    }
}
