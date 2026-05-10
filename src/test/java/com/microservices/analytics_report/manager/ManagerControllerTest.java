package com.microservices.analytics_report.manager;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import com.microservices.analytics_report.manager.controller.ManagerController;
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

@WebMvcTest(ManagerController.class)
class ManagerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    private static final String BRANCH_ID = "branch-uuid-001";

    // ── /manager/dashboard ────────────────────────────────────────────────────

    @Test
    void getDashboard_withBranchIdParam_returns200() throws Exception {
        AnalyticsDtos.ManagerDashboardResponse response = AnalyticsDtos.ManagerDashboardResponse.builder()
                .totalOrders(42L)
                .totalRevenue(1250.50)
                .averageOrderValue(29.77)
                .ordersChange(5.2)
                .revenueChange(-1.3)
                .build();

        when(analyticsService.getManagerDashboard(eq(BRANCH_ID), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/manager/dashboard").contextPath("/api").param("branchId", BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(42))
                .andExpect(jsonPath("$.totalRevenue").value(1250.50))
                .andExpect(jsonPath("$.averageOrderValue").value(29.77))
                .andExpect(jsonPath("$.ordersChange").value(5.2))
                .andExpect(jsonPath("$.revenueChange").value(-1.3));
    }

    @Test
    void getDashboard_withBranchIdHeader_returns200() throws Exception {
        AnalyticsDtos.ManagerDashboardResponse response = AnalyticsDtos.ManagerDashboardResponse.builder()
                .totalOrders(10L)
                .totalRevenue(300.0)
                .averageOrderValue(30.0)
                .ordersChange(0.0)
                .revenueChange(0.0)
                .build();

        when(analyticsService.getManagerDashboard(eq(BRANCH_ID), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/manager/dashboard").contextPath("/api").header("X-User-BranchId", BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(10));
    }

    @Test
    void getDashboard_missingBranchId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/manager/dashboard").contextPath("/api"))
                .andExpect(status().isBadRequest());
    }

    // ── /manager/orders/live ──────────────────────────────────────────────────

    @Test
    void getLiveOrders_withBranchIdParam_returns200AndList() throws Exception {
        List<AnalyticsDtos.LiveOrderResponse> orders = List.of(
                AnalyticsDtos.LiveOrderResponse.builder()
                        .id("order-001")
                        .status("PREPARING")
                        .orderType("DINE_IN")
                        .tableNumber(null)
                        .customerName(null)
                        .createdAt("2026-05-10T12:30:00")
                        .totalAmount(55.0)
                        .itemCount(3)
                        .build()
        );

        when(analyticsService.getManagerLiveOrders(BRANCH_ID)).thenReturn(orders);

        mockMvc.perform(get("/api/v1/manager/orders/live").contextPath("/api").param("branchId", BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-001"))
                .andExpect(jsonPath("$[0].status").value("PREPARING"))
                .andExpect(jsonPath("$[0].totalAmount").value(55.0))
                .andExpect(jsonPath("$[0].itemCount").value(3));
    }

    @Test
    void getLiveOrders_missingBranchId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/manager/orders/live").contextPath("/api"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLiveOrders_emptyList_returns200() throws Exception {
        when(analyticsService.getManagerLiveOrders(BRANCH_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/manager/orders/live").contextPath("/api").param("branchId", BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── /manager/sales/daily ──────────────────────────────────────────────────

    @Test
    void getDailySales_withDateParam_returns200() throws Exception {
        List<AnalyticsDtos.HourlySalesResponse> slots = List.of(
                AnalyticsDtos.HourlySalesResponse.builder()
                        .hour("09:00").revenue(120.0).orders(4L).build(),
                AnalyticsDtos.HourlySalesResponse.builder()
                        .hour("10:00").revenue(200.0).orders(6L).build()
        );

        when(analyticsService.getDailySales(eq(BRANCH_ID), eq(LocalDate.of(2026, 5, 10))))
                .thenReturn(slots);

        mockMvc.perform(get("/api/v1/manager/sales/daily").contextPath("/api")
                        .param("branchId", BRANCH_ID)
                        .param("date", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hour").value("09:00"))
                .andExpect(jsonPath("$[0].revenue").value(120.0))
                .andExpect(jsonPath("$[1].orders").value(6));
    }

    @Test
    void getDailySales_defaultsToToday_returns200() throws Exception {
        when(analyticsService.getDailySales(eq(BRANCH_ID), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/manager/sales/daily").contextPath("/api").param("branchId", BRANCH_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getDailySales_missingBranchId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/manager/sales/daily").contextPath("/api"))
                .andExpect(status().isBadRequest());
    }

    // ── /manager/items/popular ────────────────────────────────────────────────

    @Test
    void getPopularItems_returns200AndList() throws Exception {
        when(analyticsService.getPopularItems(eq(BRANCH_ID), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/manager/items/popular").contextPath("/api").param("branchId", BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getPopularItems_missingBranchId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/manager/items/popular").contextPath("/api"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPopularItems_withHeaderBranchId_returns200() throws Exception {
        when(analyticsService.getPopularItems(eq(BRANCH_ID), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/manager/items/popular").contextPath("/api").header("X-User-BranchId", BRANCH_ID))
                .andExpect(status().isOk());
    }
}
