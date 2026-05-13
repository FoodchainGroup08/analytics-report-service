package com.microservices.analytics_report.analytics.service;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.model.BranchDailySummary;
import com.microservices.analytics_report.analytics.model.OrderAnalytics;
import com.microservices.analytics_report.analytics.model.OrderItemAnalytics;
import com.microservices.analytics_report.analytics.repository.BranchDailySummaryRepository;
import com.microservices.analytics_report.analytics.repository.OrderAnalyticsRepository;
import com.microservices.analytics_report.analytics.repository.OrderItemAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock private OrderAnalyticsRepository orderAnalyticsRepository;
    @Mock private BranchDailySummaryRepository branchDailySummaryRepository;
    @Mock private OrderItemAnalyticsRepository orderItemAnalyticsRepository;

    @InjectMocks
    private AnalyticsServiceImpl service;

    private static final String BRANCH_ID = "branch-001";
    private static final String ORDER_ID  = "order-001";

    // ── recordOrderReceived ───────────────────────────────────────────────────

    @Test
    void recordOrderReceived_newOrder_persistsOrderAndItems() {
        when(orderAnalyticsRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(orderAnalyticsRepository.save(any())).thenAnswer(inv -> {
            OrderAnalytics o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        AnalyticsDtos.OrderReceivedEvent event = AnalyticsDtos.OrderReceivedEvent.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID).customerId("cust-1")
                .status("RECEIVED").orderType("DINE_IN").totalAmount(new BigDecimal("45.00"))
                .items(List.of(
                        AnalyticsDtos.OrderItemEvent.builder()
                                .menuItemId("item-1").menuItemName("Burger")
                                .quantity(2).unitPrice(new BigDecimal("12.50")).build()))
                .build();

        service.recordOrderReceived(event);

        verify(orderAnalyticsRepository).save(argThat(o ->
                o.getOrderId().equals(ORDER_ID) && o.getItemCount() == 1));
        verify(orderItemAnalyticsRepository).saveAll(argThat((List<OrderItemAnalytics> items) ->
                items.size() == 1 && items.get(0).getMenuItemName().equals("Burger")));
    }

    @Test
    void recordOrderReceived_duplicateOrder_skipsWithoutPersisting() {
        when(orderAnalyticsRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(new OrderAnalytics()));

        service.recordOrderReceived(AnalyticsDtos.OrderReceivedEvent.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID).customerId("cust-1")
                .status("RECEIVED").orderType("DINE_IN").totalAmount(BigDecimal.TEN)
                .build());

        verify(orderAnalyticsRepository, never()).save(any());
        verify(orderItemAnalyticsRepository, never()).saveAll(any());
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    void updateOrderStatus_toCompleted_setsCompletedAtAndMarksItems() {
        OrderAnalytics existing = OrderAnalytics.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID)
                .status("PREPARING").build();

        when(orderAnalyticsRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));
        when(orderAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID)
                .previousStatus("PREPARING").newStatus("COMPLETED").build());

        assertThat(existing.getStatus()).isEqualTo("COMPLETED");
        assertThat(existing.getCompletedAt()).isNotNull();
        verify(orderItemAnalyticsRepository).markOrderCompleted(ORDER_ID);
    }

    @Test
    void updateOrderStatus_toNonCompleted_doesNotMarkItems() {
        OrderAnalytics existing = OrderAnalytics.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID).status("RECEIVED").build();

        when(orderAnalyticsRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));
        when(orderAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent.builder()
                .orderId(ORDER_ID).branchId(BRANCH_ID)
                .previousStatus("RECEIVED").newStatus("PREPARING").build());

        assertThat(existing.getStatus()).isEqualTo("PREPARING");
        verify(orderItemAnalyticsRepository, never()).markOrderCompleted(any());
    }

    // ── computeDailySummaries ─────────────────────────────────────────────────

    @Test
    void computeDailySummaries_calculatesCompletionRateAndAvgPrepTime() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        LocalDateTime received = date.atTime(10, 0);
        LocalDateTime completed = date.atTime(10, 25); // 25 minutes = 1500 seconds

        OrderAnalytics o1 = OrderAnalytics.builder()
                .orderId("o1").branchId(BRANCH_ID).status("COMPLETED")
                .orderType("DINE_IN").totalAmount(new BigDecimal("30.00"))
                .orderReceivedAt(received).completedAt(completed).build();

        OrderAnalytics o2 = OrderAnalytics.builder()
                .orderId("o2").branchId(BRANCH_ID).status("CANCELLED")
                .orderType("TAKEAWAY").totalAmount(new BigDecimal("20.00"))
                .orderReceivedAt(received).build();

        when(orderAnalyticsRepository.findByOrderReceivedAtBetween(any(), any()))
                .thenReturn(List.of(o1, o2));
        when(branchDailySummaryRepository.findByBranchIdAndSummaryDate(eq(BRANCH_ID), eq(date)))
                .thenReturn(Optional.empty());
        when(branchDailySummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.computeDailySummaries(date);

        ArgumentCaptor<BranchDailySummary> captor = ArgumentCaptor.forClass(BranchDailySummary.class);
        verify(branchDailySummaryRepository).save(captor.capture());

        BranchDailySummary saved = captor.getValue();
        assertThat(saved.getTotalOrders()).isEqualTo(2);
        assertThat(saved.getCompletedOrders()).isEqualTo(1);
        assertThat(saved.getCancelledOrders()).isEqualTo(1);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo("30.00");
        assertThat(saved.getAvgPreparationTimeSeconds()).isEqualTo(1500L);
        assertThat(saved.getCompletionRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void computeDailySummaries_noOrders_skipsWithoutPersisting() {
        when(orderAnalyticsRepository.findByOrderReceivedAtBetween(any(), any()))
                .thenReturn(List.of());

        service.computeDailySummaries(LocalDate.now());

        verify(branchDailySummaryRepository, never()).save(any());
    }

    // ── getManagerDashboard ───────────────────────────────────────────────────

    @Test
    void getManagerDashboard_withYesterdayData_calculatesGrowthPercentage() {
        LocalDate today = LocalDate.of(2026, 5, 13);

        BranchDailySummary todaySummary = BranchDailySummary.builder()
                .branchId(BRANCH_ID).summaryDate(today)
                .totalOrders(100).completedOrders(80).cancelledOrders(5)
                .totalRevenue(new BigDecimal("3000.00")).avgOrderValue(new BigDecimal("30.00"))
                .avgPreparationTimeSeconds(900L) // 15 minutes
                .build();

        BranchDailySummary yesterdaySummary = BranchDailySummary.builder()
                .branchId(BRANCH_ID).summaryDate(today.minusDays(1))
                .totalOrders(80).totalRevenue(new BigDecimal("2000.00")).build();

        when(branchDailySummaryRepository.findByBranchIdAndSummaryDate(BRANCH_ID, today))
                .thenReturn(Optional.of(todaySummary));
        when(branchDailySummaryRepository.findByBranchIdAndSummaryDate(BRANCH_ID, today.minusDays(1)))
                .thenReturn(Optional.of(yesterdaySummary));

        AnalyticsDtos.ManagerDashboardResponse result = service.getManagerDashboard(BRANCH_ID, today);

        assertThat(result.getTotalOrders()).isEqualTo(100L);
        assertThat(result.getTotalRevenue()).isEqualTo(3000.0);
        assertThat(result.getAvgPreparationTimeMinutes()).isEqualTo(15.0);
        assertThat(result.getOrdersChange()).isEqualTo(25.0);  // (100-80)/80*100
        assertThat(result.getRevenueChange()).isEqualTo(50.0); // (3000-2000)/2000*100
    }

    @Test
    void getManagerDashboard_noHistory_returnsZeroGrowth() {
        LocalDate today = LocalDate.now();
        when(branchDailySummaryRepository.findByBranchIdAndSummaryDate(BRANCH_ID, today))
                .thenReturn(Optional.empty());
        when(branchDailySummaryRepository.findByBranchIdAndSummaryDate(BRANCH_ID, today.minusDays(1)))
                .thenReturn(Optional.empty());

        AnalyticsDtos.ManagerDashboardResponse result = service.getManagerDashboard(BRANCH_ID, today);

        assertThat(result.getTotalOrders()).isEqualTo(0L);
        assertThat(result.getOrdersChange()).isEqualTo(0.0);
        assertThat(result.getRevenueChange()).isEqualTo(0.0);
    }

    // ── getPopularItems (via getPopularItemsForPeriod) ────────────────────────

    @Test
    void getPopularItems_returnsMappedProjections() {
        OrderItemAnalyticsRepository.ItemPopularityProjection proj =
                new OrderItemAnalyticsRepository.ItemPopularityProjection() {
                    public String getMenuItemId()   { return "item-001"; }
                    public String getMenuItemName() { return "Chicken Burger"; }
                    public Long getTotalQuantity()  { return 42L; }
                    public BigDecimal getTotalRevenue() { return new BigDecimal("504.00"); }
                };

        when(orderItemAnalyticsRepository.findTopItemsByBranch(eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(proj));

        List<AnalyticsDtos.PopularItemResponse> result =
                service.getPopularItemsForPeriod(BRANCH_ID, LocalDate.now(), LocalDate.now(), 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("item-001");
        assertThat(result.get(0).getName()).isEqualTo("Chicken Burger");
        assertThat(result.get(0).getQuantitySold()).isEqualTo(42L);
        assertThat(result.get(0).getRevenue()).isEqualTo(504.0);
    }

    // ── getOverview ───────────────────────────────────────────────────────────

    @Test
    void getOverview_identifiesTopBranchByRevenue() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 5, 7);

        BranchDailySummary b1 = BranchDailySummary.builder()
                .branchId("branch-A").summaryDate(start)
                .totalOrders(50).completedOrders(40).cancelledOrders(2)
                .totalRevenue(new BigDecimal("5000.00")).build();

        BranchDailySummary b2 = BranchDailySummary.builder()
                .branchId("branch-B").summaryDate(start)
                .totalOrders(80).completedOrders(70).cancelledOrders(0)
                .totalRevenue(new BigDecimal("2000.00")).build();

        when(branchDailySummaryRepository.findBySummaryDateBetween(start, end))
                .thenReturn(List.of(b1, b2));
        when(branchDailySummaryRepository.findBySummaryDateBetween(any(), eq(start.minusDays(1))))
                .thenReturn(List.of());

        AnalyticsDtos.OverviewResponse result = service.getOverview(start, end);

        assertThat(result.getTopPerformingBranch()).isEqualTo("branch-A");
        assertThat(result.getTotalOrders()).isEqualTo(130L);
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("7000.00");
    }

    // ── getTrends ─────────────────────────────────────────────────────────────

    @Test
    void getTrends_dayInterval_producesOneBucketPerDay() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 5, 3);

        List<BranchDailySummary> summaries = List.of(
                BranchDailySummary.builder().branchId(BRANCH_ID).summaryDate(LocalDate.of(2026, 5, 1))
                        .totalOrders(10).completedOrders(8).totalRevenue(new BigDecimal("300")).build(),
                BranchDailySummary.builder().branchId(BRANCH_ID).summaryDate(LocalDate.of(2026, 5, 2))
                        .totalOrders(15).completedOrders(12).totalRevenue(new BigDecimal("450")).build(),
                BranchDailySummary.builder().branchId(BRANCH_ID).summaryDate(LocalDate.of(2026, 5, 3))
                        .totalOrders(20).completedOrders(18).totalRevenue(new BigDecimal("600")).build()
        );

        when(branchDailySummaryRepository.findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(
                BRANCH_ID, start, end)).thenReturn(summaries);

        AnalyticsDtos.TrendsResponse result = service.getTrends(BRANCH_ID, start, end, "DAY");

        assertThat(result.getDataPoints()).hasSize(3);
        assertThat(result.getDataPoints().get(0).getPeriod()).isEqualTo("2026-05-01");
        assertThat(result.getDataPoints().get(0).getOrders()).isEqualTo(10L);
    }

    // ── getOperationalAnalytics ───────────────────────────────────────────────

    @Test
    void getOperationalAnalytics_identifiesPeakHour() {
        LocalDate today = LocalDate.of(2026, 5, 13);

        OrderAnalytics o1 = OrderAnalytics.builder().orderId("o1").branchId(BRANCH_ID)
                .status("COMPLETED").orderType("DINE_IN").totalAmount(BigDecimal.TEN)
                .orderReceivedAt(today.atTime(12, 30)).build();
        OrderAnalytics o2 = OrderAnalytics.builder().orderId("o2").branchId(BRANCH_ID)
                .status("COMPLETED").orderType("DINE_IN").totalAmount(BigDecimal.TEN)
                .orderReceivedAt(today.atTime(12, 45)).build();
        OrderAnalytics o3 = OrderAnalytics.builder().orderId("o3").branchId(BRANCH_ID)
                .status("CANCELLED").orderType("TAKEAWAY").totalAmount(BigDecimal.TEN)
                .orderReceivedAt(today.atTime(18, 0)).build();

        when(orderAnalyticsRepository.findByBranchIdAndOrderReceivedAtBetween(
                eq(BRANCH_ID), any(), any())).thenReturn(List.of(o1, o2, o3));

        AnalyticsDtos.OperationalAnalyticsResponse result =
                service.getOperationalAnalytics(BRANCH_ID, today, today);

        assertThat(result.getTotalOrders()).isEqualTo(3L);
        assertThat(result.getPeakHour()).isEqualTo("12:00");
    }
}
