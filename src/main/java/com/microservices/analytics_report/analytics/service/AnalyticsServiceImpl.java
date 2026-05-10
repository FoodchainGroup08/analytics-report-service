package com.microservices.analytics_report.analytics.service;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.model.BranchDailySummary;
import com.microservices.analytics_report.analytics.model.OrderAnalytics;
import com.microservices.analytics_report.analytics.repository.BranchDailySummaryRepository;
import com.microservices.analytics_report.analytics.repository.OrderAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderAnalyticsRepository orderAnalyticsRepository;
    private final BranchDailySummaryRepository branchDailySummaryRepository;

    // ── Kafka ingest ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void recordOrderReceived(AnalyticsDtos.OrderReceivedEvent event) {
        if (orderAnalyticsRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.debug("Order {} already recorded — skipping duplicate", event.getOrderId());
            return;
        }

        int itemCount = event.getItems() != null ? event.getItems().size() : 0;

        OrderAnalytics record = OrderAnalytics.builder()
                .orderId(event.getOrderId())
                .branchId(event.getBranchId())
                .customerId(event.getCustomerId())
                .status(event.getStatus() != null ? event.getStatus() : "RECEIVED")
                .orderType(event.getOrderType())
                .totalAmount(event.getTotalAmount())
                .itemCount(itemCount)
                .orderReceivedAt(LocalDateTime.now())
                .build();

        orderAnalyticsRepository.save(record);
        log.info("Recorded analytics for order={} branch={}", event.getOrderId(), event.getBranchId());
    }

    @Override
    @Transactional
    public void updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent event) {
        orderAnalyticsRepository.findByOrderId(event.getOrderId()).ifPresentOrElse(record -> {
            record.setStatus(event.getNewStatus());
            if ("COMPLETED".equals(event.getNewStatus())) {
                record.setCompletedAt(LocalDateTime.now());
            }
            orderAnalyticsRepository.save(record);
            log.info("Updated analytics for order={} status={}", event.getOrderId(), event.getNewStatus());
        }, () -> log.warn("Order {} not found in analytics — status update skipped", event.getOrderId()));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardResponse getDashboard(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<BranchDailySummary> summaries = branchDailySummaryRepository
                .findBySummaryDateOrderByTotalRevenueDesc(target);

        long totalOrders    = summaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        long completed      = summaries.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
        long cancelled      = summaries.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
        BigDecimal revenue  = summaries.stream().map(BranchDailySummary::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnalyticsDtos.BranchSummaryResponse> branchResponses = summaries.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return AnalyticsDtos.DashboardResponse.builder()
                .date(target)
                .totalOrdersToday(totalOrders)
                .totalRevenueToday(revenue)
                .completedOrdersToday(completed)
                .cancelledOrdersToday(cancelled)
                .branchSummaries(branchResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.BranchSummaryResponse getBranchSummaryForDate(String branchId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        BranchDailySummary summary = branchDailySummaryRepository
                .findByBranchIdAndSummaryDate(branchId, target)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No summary found for branch " + branchId + " on " + target));
        return toSummaryResponse(summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.BranchSummaryResponse> getBranchSummaries(String branchId,
                                                                         LocalDate from, LocalDate to) {
        return branchDailySummaryRepository
                .findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(branchId, from, to)
                .stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnalyticsDtos.OrderAnalyticsResponse> getBranchOrders(String branchId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderReceivedAt"));
        return orderAnalyticsRepository
                .findByBranchIdOrderByOrderReceivedAtDesc(branchId, pageable)
                .map(this::toOrderResponse);
    }

    // ── Daily rollup — runs at midnight every day ─────────────────────────────

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledDailySummary() {
        log.info("Running scheduled daily summary for {}", LocalDate.now().minusDays(1));
        computeDailySummaries(LocalDate.now().minusDays(1));
    }

    @Override
    @Transactional
    public void computeDailySummaries(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.atTime(LocalTime.MAX);

        List<OrderAnalytics> orders = orderAnalyticsRepository.findByOrderReceivedAtBetween(start, end);
        if (orders.isEmpty()) {
            log.info("No orders found for {} — skipping summary", date);
            return;
        }

        Map<String, List<OrderAnalytics>> byBranch = orders.stream()
                .collect(Collectors.groupingBy(OrderAnalytics::getBranchId));

        byBranch.forEach((branchId, branchOrders) -> {
            int total     = branchOrders.size();
            int completed = (int) branchOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
            int cancelled = (int) branchOrders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();
            int dineIn    = (int) branchOrders.stream().filter(o -> "DINE_IN".equals(o.getOrderType())).count();
            int takeaway  = (int) branchOrders.stream().filter(o -> "TAKEAWAY".equals(o.getOrderType())).count();
            int delivery  = (int) branchOrders.stream().filter(o -> "DELIVERY".equals(o.getOrderType())).count();

            BigDecimal revenue = branchOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .map(OrderAnalytics::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avg = completed > 0
                    ? revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BranchDailySummary summary = branchDailySummaryRepository
                    .findByBranchIdAndSummaryDate(branchId, date)
                    .orElse(BranchDailySummary.builder().branchId(branchId).summaryDate(date).build());

            summary.setTotalOrders(total);
            summary.setCompletedOrders(completed);
            summary.setCancelledOrders(cancelled);
            summary.setTotalRevenue(revenue);
            summary.setAvgOrderValue(avg);
            summary.setDineInCount(dineIn);
            summary.setTakeawayCount(takeaway);
            summary.setDeliveryCount(delivery);

            branchDailySummaryRepository.save(summary);
            log.info("Computed daily summary: branch={} date={} orders={} revenue={}",
                    branchId, date, total, revenue);
        });
    }

    // ── Manager endpoints ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.ManagerDashboardResponse getManagerDashboard(String branchId, LocalDate date) {
        LocalDate today = date != null ? date : LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        BranchDailySummary todaySummary = branchDailySummaryRepository
                .findByBranchIdAndSummaryDate(branchId, today)
                .orElse(null);

        long totalOrders = todaySummary != null ? todaySummary.getTotalOrders() : 0L;
        double totalRevenue = todaySummary != null
                ? todaySummary.getTotalRevenue().doubleValue() : 0.0;
        double avgOrderValue = todaySummary != null && todaySummary.getAvgOrderValue() != null
                ? todaySummary.getAvgOrderValue().doubleValue() : 0.0;

        double ordersChange = 0.0;
        double revenueChange = 0.0;

        BranchDailySummary yesterdaySummary = branchDailySummaryRepository
                .findByBranchIdAndSummaryDate(branchId, yesterday)
                .orElse(null);

        if (yesterdaySummary != null) {
            long yOrders = yesterdaySummary.getTotalOrders();
            if (yOrders > 0) {
                ordersChange = ((double) (totalOrders - yOrders) / yOrders) * 100.0;
            }
            double yRevenue = yesterdaySummary.getTotalRevenue().doubleValue();
            if (yRevenue > 0) {
                revenueChange = ((totalRevenue - yRevenue) / yRevenue) * 100.0;
            }
        }

        log.debug("Manager dashboard branchId={} date={} orders={} revenue={}", branchId, today, totalOrders, totalRevenue);
        return AnalyticsDtos.ManagerDashboardResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .ordersChange(ordersChange)
                .revenueChange(revenueChange)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.LiveOrderResponse> getManagerLiveOrders(String branchId) {
        // OrderAnalytics has status field; query for active statuses: RECEIVED, PREPARING, READY
        List<OrderAnalytics> activeOrders = orderAnalyticsRepository.findActivOrdersByBranch(branchId);
        return activeOrders.stream()
                .map(o -> AnalyticsDtos.LiveOrderResponse.builder()
                        .id(o.getOrderId())
                        .status(o.getStatus())
                        .orderType(o.getOrderType())
                        .tableNumber(null)    // not stored in OrderAnalytics
                        .customerName(null)   // not stored in OrderAnalytics (only customerId)
                        .createdAt(o.getOrderReceivedAt() != null ? o.getOrderReceivedAt().toString() : null)
                        .totalAmount(o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                        .itemCount(o.getItemCount() != null ? o.getItemCount() : 0)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.HourlySalesResponse> getDailySales(String branchId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        LocalDateTime start = target.atStartOfDay();
        LocalDateTime end   = target.atTime(LocalTime.MAX);

        List<OrderAnalytics> orders = orderAnalyticsRepository
                .findByBranchIdAndOrderReceivedAtBetween(branchId, start, end);

        // Group orders by hour (business hours 0-23); produce a slot for each hour that has data
        // and fill business hours (8-22) even if empty to give the frontend a full chart
        Map<Integer, List<OrderAnalytics>> byHour = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderReceivedAt().getHour()));

        List<AnalyticsDtos.HourlySalesResponse> result = new ArrayList<>();
        for (int hour = 8; hour <= 22; hour++) {
            List<OrderAnalytics> hourOrders = byHour.getOrDefault(hour, List.of());
            double revenue = hourOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                    .sum();
            String label = String.format("%02d:00", hour);
            result.add(AnalyticsDtos.HourlySalesResponse.builder()
                    .hour(label)
                    .revenue(revenue)
                    .orders(hourOrders.size())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.PopularItemResponse> getPopularItems(String branchId, LocalDate date) {
        // OrderAnalytics does not store item-level data (only itemCount aggregate).
        // TODO: implement once an OrderItem analytics table or item-level Kafka event is available.
        log.debug("getPopularItems called for branchId={} date={} — no item-level data available", branchId, date);
        return List.of();
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.AdminAnalyticsResponse getAdminAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository
                .findBySummaryDateBetween(start, end);

        long totalOrders   = summaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        double totalRevenue = summaries.stream()
                .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
        long branchCount   = summaries.stream()
                .map(BranchDailySummary::getBranchId).distinct().count();
        double avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;

        log.debug("Admin analytics start={} end={} totalOrders={} revenue={}", start, end, totalOrders, totalRevenue);
        return AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .totalBranches(branchCount)
                .totalCustomers(0L) // not tracked at summary level
                .dailyBreakdown(List.of())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.BranchAnalyticsResponse> getBranchAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository
                .findBySummaryDateBetween(start, end);

        // Group by branchId and aggregate
        Map<String, List<BranchDailySummary>> byBranch = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        return byBranch.entrySet().stream()
                .map(entry -> {
                    String bid = entry.getKey();
                    List<BranchDailySummary> branchSummaries = entry.getValue();
                    long orders = branchSummaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    double revenue = branchSummaries.stream()
                            .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
                    return AnalyticsDtos.BranchAnalyticsResponse.builder()
                            .id(bid)
                            .name(bid) // branchName not available; using branchId as name
                            .orders(orders)
                            .revenue(revenue)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AnalyticsDtos.BranchSummaryResponse toSummaryResponse(BranchDailySummary s) {
        int inProgress = s.getTotalOrders() - s.getCompletedOrders() - s.getCancelledOrders();
        return AnalyticsDtos.BranchSummaryResponse.builder()
                .branchId(s.getBranchId())
                .date(s.getSummaryDate())
                .totalOrders(s.getTotalOrders())
                .completedOrders(s.getCompletedOrders())
                .cancelledOrders(s.getCancelledOrders())
                .inProgressOrders(Math.max(inProgress, 0))
                .totalRevenue(s.getTotalRevenue())
                .avgOrderValue(s.getAvgOrderValue())
                .dineInCount(s.getDineInCount())
                .takeawayCount(s.getTakeawayCount())
                .deliveryCount(s.getDeliveryCount())
                .build();
    }

    private AnalyticsDtos.OrderAnalyticsResponse toOrderResponse(OrderAnalytics o) {
        return AnalyticsDtos.OrderAnalyticsResponse.builder()
                .id(o.getId())
                .orderId(o.getOrderId())
                .branchId(o.getBranchId())
                .customerId(o.getCustomerId())
                .status(o.getStatus())
                .orderType(o.getOrderType())
                .totalAmount(o.getTotalAmount())
                .itemCount(o.getItemCount())
                .orderReceivedAt(o.getOrderReceivedAt() != null ? o.getOrderReceivedAt().toString() : null)
                .lastUpdatedAt(o.getLastUpdatedAt() != null ? o.getLastUpdatedAt().toString() : null)
                .build();
    }
}
