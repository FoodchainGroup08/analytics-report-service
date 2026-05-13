package com.microservices.analytics_report.analytics.service;

import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.model.BranchDailySummary;
import com.microservices.analytics_report.analytics.model.OrderAnalytics;
import com.microservices.analytics_report.analytics.model.OrderItemAnalytics;
import com.microservices.analytics_report.analytics.repository.BranchDailySummaryRepository;
import com.microservices.analytics_report.analytics.repository.OrderAnalyticsRepository;
import com.microservices.analytics_report.analytics.repository.OrderItemAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderAnalyticsRepository orderAnalyticsRepository;
    private final BranchDailySummaryRepository branchDailySummaryRepository;
    private final OrderItemAnalyticsRepository orderItemAnalyticsRepository;

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

        if (event.getItems() != null && !event.getItems().isEmpty()) {
            List<OrderItemAnalytics> itemRecords = event.getItems().stream()
                    .filter(item -> item.getMenuItemId() != null)
                    .map(item -> {
                        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                        int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                        return OrderItemAnalytics.builder()
                                .orderId(event.getOrderId())
                                .branchId(event.getBranchId())
                                .menuItemId(item.getMenuItemId())
                                .menuItemName(item.getMenuItemName() != null ? item.getMenuItemName() : "Unknown")
                                .category(item.getCategory())
                                .quantity(qty)
                                .unitPrice(unitPrice)
                                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)))
                                .orderStatus(record.getStatus())
                                .orderReceivedAt(record.getOrderReceivedAt())
                                .build();
                    })
                    .collect(Collectors.toList());
            orderItemAnalyticsRepository.saveAll(itemRecords);
        }
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
            orderItemAnalyticsRepository.updateStatusByOrderId(event.getOrderId(), event.getNewStatus());
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

    // ── Daily rollup ──────────────────────────────────────────────────────────

    // Runs every hour — keeps today's BranchDailySummary current for the dashboard
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyDailySummary() {
        log.info("Running hourly summary refresh for {}", LocalDate.now());
        computeDailySummaries(LocalDate.now());
    }

    // Runs at 00:05 each night — finalizes yesterday's complete data
    @Scheduled(cron = "0 5 0 * * *")
    public void scheduledDailySummary() {
        log.info("Running midnight finalization for {}", LocalDate.now().minusDays(1));
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
                ? todaySummary.getTotalRevenue().multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;
        double avgOrderValue = todaySummary != null && todaySummary.getAvgOrderValue() != null
                ? todaySummary.getAvgOrderValue().multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;

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
            double yRevenue = yesterdaySummary.getTotalRevenue().multiply(BigDecimal.valueOf(100)).doubleValue();
            if (yRevenue > 0) {
                revenueChange = ((totalRevenue - yRevenue) / yRevenue) * 100.0;
            }
        }

        // ── Live fields computed from raw OrderAnalytics ──────────────────────
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd   = today.atTime(LocalTime.MAX);
        List<OrderAnalytics> orders =
                orderAnalyticsRepository.findByBranchIdAndOrderReceivedAtBetween(branchId, dayStart, dayEnd);

        // BranchDailySummary is computed at midnight for the previous day, so today's row
        // won't exist until tomorrow. Fall back to live counts so the dashboard is never zero.
        if (todaySummary == null && !orders.isEmpty()) {
            totalOrders = orders.size();
            BigDecimal liveRevenue = orders.stream()
                    .filter(o -> !"CANCELLED".equals(o.getStatus()))
                    .map(OrderAnalytics::getTotalAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalRevenue = liveRevenue.multiply(BigDecimal.valueOf(100)).doubleValue();
            avgOrderValue = totalOrders > 0
                    ? liveRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;
        }

        long dineInCount   = orders.stream().filter(o -> "DINE_IN".equalsIgnoreCase(o.getOrderType())).count();
        long takeawayCount = orders.stream().filter(o -> "TAKEAWAY".equalsIgnoreCase(o.getOrderType())).count();
        long deliveryCount = orders.stream().filter(o -> "DELIVERY".equalsIgnoreCase(o.getOrderType())).count();

        long completedCount = orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
        double completionRate = orders.isEmpty() ? 0.0
                : Math.round((completedCount * 100.0 / orders.size()) * 10.0) / 10.0;

        double averagePrepTime = orders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus())
                        && o.getCompletedAt() != null
                        && o.getOrderReceivedAt() != null)
                .mapToLong(o -> java.time.Duration.between(o.getOrderReceivedAt(), o.getCompletedAt()).toMinutes())
                .filter(m -> m >= 0)
                .average()
                .orElse(0.0);

        Map<Integer, Long> ordersByHour = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getOrderReceivedAt().getHour(),
                        Collectors.counting()
                ));
        int peakHourRaw = ordersByHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);
        String peakHour      = peakHourRaw >= 0 ? formatPeakHour(peakHourRaw) : null;
        long peakHourOrders  = peakHourRaw >= 0 ? ordersByHour.get(peakHourRaw) : 0L;

        log.debug("Manager dashboard branchId={} date={} orders={} revenue={}", branchId, today, totalOrders, totalRevenue);
        return AnalyticsDtos.ManagerDashboardResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .ordersChange(ordersChange)
                .revenueChange(revenueChange)
                .averagePrepTime(Math.round(averagePrepTime * 10.0) / 10.0)
                .peakHour(peakHour)
                .peakHourOrders(peakHourOrders)
                .completionRate(completionRate)
                .dineInCount(dineInCount)
                .takeawayCount(takeawayCount)
                .deliveryCount(deliveryCount)
                .build();
    }

    private String formatPeakHour(int hour) {
        return String.format("%02d:00", hour);
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
                        .tableNumber(null)
                        .customerName(null)
                        .placedAt(o.getOrderReceivedAt() != null ? o.getOrderReceivedAt().toString() : null)
                        .total(o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() * 100.0 : 0.0)
                        .itemCount(o.getItemCount() != null ? o.getItemCount() : 0)
                        .items(List.of())
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
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() * 100.0 : 0.0)
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
        LocalDate target = date != null ? date : LocalDate.now();
        LocalDate yesterday = target.minusDays(1);

        LocalDateTime start  = target.atStartOfDay();
        LocalDateTime end    = target.atTime(LocalTime.MAX);
        LocalDateTime yStart = yesterday.atStartOfDay();
        LocalDateTime yEnd   = yesterday.atTime(LocalTime.MAX);

        List<Object[]> todayRows     = orderItemAnalyticsRepository.findPopularItems(branchId, start, end);
        List<Object[]> yesterdayRows = orderItemAnalyticsRepository.findPopularItems(branchId, yStart, yEnd);

        Map<String, Long> yesterdayQty = yesterdayRows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[3]).longValue()
                ));

        return todayRows.stream().map(row -> {
            String menuItemId = (String) row[0];
            String name       = (String) row[1];
            String category   = (String) row[2];
            long qty          = ((Number) row[3]).longValue();
            double revenue    = ((Number) row[4]).doubleValue() * 100.0;
            long yQty         = yesterdayQty.getOrDefault(menuItemId, 0L);
            double trend      = yQty > 0 ? ((double) (qty - yQty) / yQty) * 100.0 : 0.0;

            return AnalyticsDtos.PopularItemResponse.builder()
                    .id(menuItemId)
                    .name(name)
                    .category(category)
                    .quantitySold(qty)
                    .revenue(revenue)
                    .trend(trend)
                    .build();
        }).collect(Collectors.toList());
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

    // ── Enriched Head Office Admin methods ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.OverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository.findBySummaryDateBetween(start, end);

        long totalOrders     = summaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        long completedOrders = summaries.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
        long cancelledOrders = summaries.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
        BigDecimal totalRevenue = summaries.stream()
                .map(BranchDailySummary::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double completionRate   = totalOrders > 0 ? round((completedOrders * 100.0) / totalOrders) : 0.0;
        double cancellationRate = totalOrders > 0 ? round((cancelledOrders * 100.0) / totalOrders) : 0.0;

        // ── Growth vs equivalent prior period ─────────────────────────────────
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate priorEnd   = start.minusDays(1);
        LocalDate priorStart = priorEnd.minusDays(periodDays - 1);

        List<BranchDailySummary> priorSummaries = branchDailySummaryRepository
                .findBySummaryDateBetween(priorStart, priorEnd);
        long priorOrders  = priorSummaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        double priorRevenue = priorSummaries.stream()
                .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();

        double revenueGrowth = priorRevenue > 0
                ? round(((totalRevenue.doubleValue() - priorRevenue) / priorRevenue) * 100.0) : 0.0;
        double ordersGrowth  = priorOrders > 0
                ? round(((double) (totalOrders - priorOrders) / priorOrders) * 100.0) : 0.0;

        // ── Branch highlights from OrderAnalytics ─────────────────────────────
        Map<String, List<BranchDailySummary>> byBranch = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));
        long totalBranches = byBranch.size();

        String topBranch = byBranch.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum()))
                .map(Map.Entry::getKey).orElse(null);

        // Fastest = shortest avg prep time; Slowest = longest
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd   = end.atTime(LocalTime.MAX);
        List<OrderAnalytics> allOrders = orderAnalyticsRepository.findByOrderReceivedAtBetween(rangeStart, rangeEnd);

        Map<String, Double> prepTimeByBranch = allOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()) && o.getCompletedAt() != null && o.getOrderReceivedAt() != null)
                .collect(Collectors.groupingBy(
                        OrderAnalytics::getBranchId,
                        Collectors.averagingDouble(o ->
                                Math.max(0, java.time.Duration.between(o.getOrderReceivedAt(), o.getCompletedAt()).toMinutes()))
                ));

        String fastestBranch = prepTimeByBranch.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        String slowestBranch = prepTimeByBranch.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        return AnalyticsDtos.OverviewResponse.builder()
                .startDate(start)
                .endDate(end)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .avgOrderValue(avgOrderValue)
                .completionRate(completionRate)
                .cancellationRate(cancellationRate)
                .revenueGrowthPercent(revenueGrowth)
                .ordersGrowthPercent(ordersGrowth)
                .topPerformingBranch(topBranch)
                .fastestBranch(fastestBranch)
                .slowestBranch(slowestBranch)
                .totalBranches(totalBranches)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.BranchComparisonResponse> getBranchComparison(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository.findBySummaryDateBetween(start, end);
        Map<String, List<BranchDailySummary>> byBranch = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd   = end.atTime(LocalTime.MAX);
        List<OrderAnalytics> allOrders = orderAnalyticsRepository.findByOrderReceivedAtBetween(rangeStart, rangeEnd);
        Map<String, List<OrderAnalytics>> ordersByBranch = allOrders.stream()
                .collect(Collectors.groupingBy(OrderAnalytics::getBranchId));

        Pageable top5 = PageRequest.of(0, 5);

        return byBranch.entrySet().stream()
                .map(e -> {
                    String bid = e.getKey();
                    List<BranchDailySummary> bs = e.getValue();
                    long totalOrders = bs.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    long completed   = bs.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
                    long cancelled   = bs.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
                    BigDecimal revenue = bs.stream().map(BranchDailySummary::getTotalRevenue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avgOV = totalOrders > 0
                            ? revenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    double completionRate   = totalOrders > 0 ? round((completed * 100.0) / totalOrders) : 0.0;
                    double cancellationRate = totalOrders > 0 ? round((cancelled * 100.0) / totalOrders) : 0.0;

                    Double avgPrep = ordersByBranch.getOrDefault(bid, List.of()).stream()
                            .filter(o -> "COMPLETED".equals(o.getStatus()) && o.getCompletedAt() != null && o.getOrderReceivedAt() != null)
                            .mapToLong(o -> Math.max(0, java.time.Duration.between(o.getOrderReceivedAt(), o.getCompletedAt()).toMinutes()))
                            .average().isPresent()
                            ? ordersByBranch.getOrDefault(bid, List.of()).stream()
                                    .filter(o -> "COMPLETED".equals(o.getStatus()) && o.getCompletedAt() != null && o.getOrderReceivedAt() != null)
                                    .mapToLong(o -> Math.max(0, java.time.Duration.between(o.getOrderReceivedAt(), o.getCompletedAt()).toMinutes()))
                                    .average().getAsDouble()
                            : null;

                    List<Object[]> topItemRows = orderItemAnalyticsRepository
                            .findPopularItemsByBranchPeriod(bid, rangeStart, rangeEnd, top5);
                    List<AnalyticsDtos.PopularItemResponse> topItems = topItemRows.stream()
                            .map(row -> AnalyticsDtos.PopularItemResponse.builder()
                                    .id((String) row[0])
                                    .name((String) row[1])
                                    .category(row[2] != null ? (String) row[2] : "")
                                    .quantitySold(((Number) row[3]).longValue())
                                    .revenue(((Number) row[4]).doubleValue())
                                    .trend(0.0)
                                    .build())
                            .collect(Collectors.toList());

                    return AnalyticsDtos.BranchComparisonResponse.builder()
                            .branchId(bid)
                            .totalOrders(totalOrders)
                            .totalRevenue(revenue)
                            .avgOrderValue(avgOV)
                            .completionRate(completionRate)
                            .cancellationRate(cancellationRate)
                            .avgPreparationTimeMinutes(avgPrep != null ? round(avgPrep) : null)
                            .topItems(topItems)
                            .build();
                })
                .sorted(Comparator.comparingDouble((AnalyticsDtos.BranchComparisonResponse r) ->
                        r.getTotalRevenue().doubleValue()).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.TrendsResponse getTrends(String branchId, LocalDate startDate, LocalDate endDate, String interval) {
        LocalDate start    = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end      = endDate   != null ? endDate   : LocalDate.now();
        String resolvedInterval = interval != null ? interval.toUpperCase() : "DAY";

        List<BranchDailySummary> summaries = branchId != null && !branchId.isBlank()
                ? branchDailySummaryRepository.findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(branchId, start, end)
                : branchDailySummaryRepository.findBySummaryDateBetween(start, end);

        // Group by period key
        Map<String, List<BranchDailySummary>> grouped = summaries.stream()
                .collect(Collectors.groupingBy(s -> periodKey(s.getSummaryDate(), resolvedInterval),
                        LinkedHashMap::new, Collectors.toList()));

        List<AnalyticsDtos.TrendDataPoint> dataPoints = grouped.entrySet().stream()
                .map(e -> {
                    List<BranchDailySummary> pts = e.getValue();
                    long orders      = pts.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    long completed   = pts.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
                    BigDecimal rev   = pts.stream().map(BranchDailySummary::getTotalRevenue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double compRate  = orders > 0 ? round((completed * 100.0) / orders) : 0.0;
                    return AnalyticsDtos.TrendDataPoint.builder()
                            .period(e.getKey())
                            .revenue(rev)
                            .orders(orders)
                            .avgPreparationTimeMinutes(null)
                            .completionRate(compRate)
                            .build();
                })
                .collect(Collectors.toList());

        return AnalyticsDtos.TrendsResponse.builder()
                .startDate(start)
                .endDate(end)
                .interval(resolvedInterval)
                .branchId(branchId)
                .dataPoints(dataPoints)
                .build();
    }

    private String periodKey(LocalDate date, String interval) {
        return switch (interval) {
            case "WEEK"  -> date.getYear() + "-W" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "MONTH" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            default      -> date.toString(); // DAY
        };
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.OperationalAnalyticsResponse getOperationalAnalytics(String branchId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd   = end.atTime(LocalTime.MAX);

        List<OrderAnalytics> orders = branchId != null && !branchId.isBlank()
                ? orderAnalyticsRepository.findByBranchIdAndOrderReceivedAtBetween(branchId, rangeStart, rangeEnd)
                : orderAnalyticsRepository.findByOrderReceivedAtBetween(rangeStart, rangeEnd);

        long totalOrders = orders.size();

        // ── Orders by status ──────────────────────────────────────────────────
        Map<String, Long> statusCounts = orders.stream()
                .collect(Collectors.groupingBy(OrderAnalytics::getStatus, Collectors.counting()));
        List<AnalyticsDtos.OrdersByStatusEntry> ordersByStatus = statusCounts.entrySet().stream()
                .map(e -> AnalyticsDtos.OrdersByStatusEntry.builder()
                        .status(e.getKey())
                        .count(e.getValue())
                        .percentage(totalOrders > 0 ? round((e.getValue() * 100.0) / totalOrders) : 0.0)
                        .build())
                .sorted(Comparator.comparingLong(AnalyticsDtos.OrdersByStatusEntry::getCount).reversed())
                .collect(Collectors.toList());

        // ── Orders by hour ────────────────────────────────────────────────────
        Map<Integer, List<OrderAnalytics>> byHour = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderReceivedAt().getHour()));
        List<AnalyticsDtos.HourlySalesResponse> ordersByHour = new ArrayList<>();
        for (int h = 0; h <= 23; h++) {
            List<OrderAnalytics> hourOrders = byHour.getOrDefault(h, List.of());
            if (hourOrders.isEmpty()) continue;
            double revenue = hourOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                    .sum();
            ordersByHour.add(AnalyticsDtos.HourlySalesResponse.builder()
                    .hour(String.format("%02d:00", h))
                    .revenue(revenue)
                    .orders(hourOrders.size())
                    .build());
        }

        int peakHourRaw = byHour.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey).orElse(-1);
        String peakHour = peakHourRaw >= 0 ? formatPeakHour(peakHourRaw) : null;

        return AnalyticsDtos.OperationalAnalyticsResponse.builder()
                .ordersByStatus(ordersByStatus)
                .ordersByHour(ordersByHour)
                .peakHour(peakHour)
                .totalOrders(totalOrders)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.PopularItemResponse> getPopularItemsForPeriod(String branchId, LocalDate startDate, LocalDate endDate, int limit) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd   = end.atTime(LocalTime.MAX);

        Pageable pageable = PageRequest.of(0, limit > 0 ? limit : 10);
        List<Object[]> rows = branchId != null && !branchId.isBlank()
                ? orderItemAnalyticsRepository.findPopularItemsByBranchPeriod(branchId, rangeStart, rangeEnd, pageable)
                : orderItemAnalyticsRepository.findPopularItemsGlobal(rangeStart, rangeEnd, pageable);

        return rows.stream()
                .map(row -> AnalyticsDtos.PopularItemResponse.builder()
                        .id((String) row[0])
                        .name((String) row[1])
                        .category(row[2] != null ? (String) row[2] : "")
                        .quantitySold(((Number) row[3]).longValue())
                        .revenue(((Number) row[4]).doubleValue())
                        .trend(0.0)
                        .build())
                .collect(Collectors.toList());
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
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
                .totalRevenue(s.getTotalRevenue() != null
                        ? s.getTotalRevenue().multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO)
                .avgOrderValue(s.getAvgOrderValue() != null
                        ? s.getAvgOrderValue().multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO)
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
