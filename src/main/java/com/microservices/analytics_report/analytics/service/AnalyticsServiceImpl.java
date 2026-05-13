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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
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
    @CacheEvict(cacheNames = {"analytics.overview", "analytics.branchComparison",
            "analytics.adminAnalytics", "analytics.branchAnalytics"}, allEntries = true)
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

        OrderAnalytics saved = orderAnalyticsRepository.save(record);

        // Persist item-level rows so popularity queries have data
        if (event.getItems() != null) {
            List<OrderItemAnalytics> itemRows = event.getItems().stream()
                    .filter(i -> i.getMenuItemId() != null && i.getMenuItemName() != null)
                    .map(i -> {
                        int qty = i.getQuantity() != null ? i.getQuantity() : 1;
                        BigDecimal price = i.getUnitPrice() != null ? i.getUnitPrice() : BigDecimal.ZERO;
                        return OrderItemAnalytics.builder()
                                .orderId(event.getOrderId())
                                .branchId(event.getBranchId())
                                .menuItemId(i.getMenuItemId())
                                .menuItemName(i.getMenuItemName())
                                .quantity(qty)
                                .unitPrice(price)
                                .lineTotal(price.multiply(BigDecimal.valueOf(qty)))
                                .orderReceivedAt(saved.getOrderReceivedAt())
                                .orderCompleted(false)
                                .build();
                    })
                    .collect(Collectors.toList());
            orderItemAnalyticsRepository.saveAll(itemRows);
        }

        log.info("Recorded analytics for order={} branch={} items={}", event.getOrderId(), event.getBranchId(), itemCount);
    }

    @Override
    @Transactional
    public void updateOrderStatus(AnalyticsDtos.OrderStatusUpdatedEvent event) {
        orderAnalyticsRepository.findByOrderId(event.getOrderId()).ifPresentOrElse(record -> {
            record.setStatus(event.getNewStatus());
            if ("COMPLETED".equals(event.getNewStatus())) {
                record.setCompletedAt(LocalDateTime.now());
                orderItemAnalyticsRepository.markOrderCompleted(event.getOrderId());
            }
            orderAnalyticsRepository.save(record);
            log.info("Updated analytics for order={} status={}", event.getOrderId(), event.getNewStatus());
        }, () -> log.warn("Order {} not found in analytics — status update skipped", event.getOrderId()));
    }

    // ── Core queries ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardResponse getDashboard(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<BranchDailySummary> summaries = branchDailySummaryRepository
                .findBySummaryDateOrderByTotalRevenueDesc(target);

        long totalOrders   = summaries.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        long completed     = summaries.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
        long cancelled     = summaries.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
        BigDecimal revenue = summaries.stream().map(BranchDailySummary::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDtos.DashboardResponse.builder()
                .date(target)
                .totalOrdersToday(totalOrders)
                .totalRevenueToday(revenue)
                .completedOrdersToday(completed)
                .cancelledOrdersToday(cancelled)
                .branchSummaries(summaries.stream().map(this::toSummaryResponse).collect(Collectors.toList()))
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

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledDailySummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Running scheduled daily summary for {}", yesterday);
        computeDailySummaries(yesterday);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"analytics.overview", "analytics.branchComparison",
            "analytics.adminAnalytics", "analytics.branchAnalytics"}, allEntries = true)
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

            // Average preparation time (received → completedAt) for completed orders
            OptionalDouble avgPrepSeconds = branchOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus())
                            && o.getCompletedAt() != null
                            && o.getOrderReceivedAt() != null)
                    .mapToLong(o -> java.time.Duration.between(o.getOrderReceivedAt(), o.getCompletedAt()).getSeconds())
                    .average();

            BigDecimal completionRate = total > 0
                    ? BigDecimal.valueOf((double) completed / total * 100).setScale(2, RoundingMode.HALF_UP)
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
            summary.setAvgPreparationTimeSeconds(avgPrepSeconds.isPresent() ? (long) avgPrepSeconds.getAsDouble() : null);
            summary.setCompletionRate(completionRate);

            branchDailySummaryRepository.save(summary);
            log.info("Computed summary: branch={} date={} orders={} completed={} revenue={} avgPrepSecs={}",
                    branchId, date, total, completed, revenue,
                    avgPrepSeconds.isPresent() ? (long) avgPrepSeconds.getAsDouble() : "n/a");
        });
    }

    // ── Manager endpoints ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.ManagerDashboardResponse getManagerDashboard(String branchId, LocalDate date) {
        LocalDate today     = date != null ? date : LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        BranchDailySummary todaySummary = branchDailySummaryRepository
                .findByBranchIdAndSummaryDate(branchId, today).orElse(null);

        long totalOrders      = todaySummary != null ? todaySummary.getTotalOrders() : 0L;
        double totalRevenue   = todaySummary != null ? todaySummary.getTotalRevenue().doubleValue() : 0.0;
        double avgOrderValue  = todaySummary != null && todaySummary.getAvgOrderValue() != null
                ? todaySummary.getAvgOrderValue().doubleValue() : 0.0;
        double avgPrepMinutes = 0.0;
        if (todaySummary != null && todaySummary.getAvgPreparationTimeSeconds() != null) {
            avgPrepMinutes = todaySummary.getAvgPreparationTimeSeconds() / 60.0;
        }

        double ordersChange  = 0.0;
        double revenueChange = 0.0;

        BranchDailySummary yesterdaySummary = branchDailySummaryRepository
                .findByBranchIdAndSummaryDate(branchId, yesterday).orElse(null);

        if (yesterdaySummary != null) {
            long yOrders = yesterdaySummary.getTotalOrders();
            if (yOrders > 0) ordersChange = ((double) (totalOrders - yOrders) / yOrders) * 100.0;
            double yRevenue = yesterdaySummary.getTotalRevenue().doubleValue();
            if (yRevenue > 0) revenueChange = ((totalRevenue - yRevenue) / yRevenue) * 100.0;
        }

        return AnalyticsDtos.ManagerDashboardResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .ordersChange(ordersChange)
                .revenueChange(revenueChange)
                .avgPreparationTimeMinutes(avgPrepMinutes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.LiveOrderResponse> getManagerLiveOrders(String branchId) {
        return orderAnalyticsRepository.findActivOrdersByBranch(branchId).stream()
                .map(o -> AnalyticsDtos.LiveOrderResponse.builder()
                        .id(o.getOrderId())
                        .status(o.getStatus())
                        .orderType(o.getOrderType())
                        .tableNumber(null)
                        .customerName(null)
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

        Map<Integer, List<OrderAnalytics>> byHour = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderReceivedAt().getHour()));

        List<AnalyticsDtos.HourlySalesResponse> result = new ArrayList<>();
        for (int hour = 8; hour <= 22; hour++) {
            List<OrderAnalytics> hourOrders = byHour.getOrDefault(hour, List.of());
            double revenue = hourOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                    .sum();
            result.add(AnalyticsDtos.HourlySalesResponse.builder()
                    .hour(String.format("%02d:00", hour))
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
        return getPopularItemsForPeriod(branchId, target, target, 10);
    }

    // ── Admin / Head Office endpoints ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analytics.adminAnalytics", key = "#startDate + '-' + #endDate")
    public AnalyticsDtos.AdminAnalyticsResponse getAdminAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> current = branchDailySummaryRepository.findBySummaryDateBetween(start, end);

        long totalOrders    = current.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        long completedTotal = current.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
        long cancelledTotal = current.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
        double totalRevenue = current.stream().mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
        long branchCount    = current.stream().map(BranchDailySummary::getBranchId).distinct().count();
        double avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;
        double completionRate = totalOrders > 0 ? (double) completedTotal / totalOrders * 100.0 : 0.0;
        double cancellationRate = totalOrders > 0 ? (double) cancelledTotal / totalOrders * 100.0 : 0.0;

        // Prior period for growth calculation
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate priorStart = start.minusDays(periodDays);
        LocalDate priorEnd   = start.minusDays(1);
        List<BranchDailySummary> prior = branchDailySummaryRepository.findBySummaryDateBetween(priorStart, priorEnd);
        long priorOrders  = prior.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        double priorRevenue = prior.stream().mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
        double revenueGrowth = priorRevenue > 0 ? ((totalRevenue - priorRevenue) / priorRevenue) * 100.0 : 0.0;
        double ordersGrowth  = priorOrders  > 0 ? ((double) (totalOrders - priorOrders) / priorOrders) * 100.0 : 0.0;

        // Rankings by branch
        Map<String, List<BranchDailySummary>> byBranch = current.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        String topBranch = byBranch.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum()))
                .map(Map.Entry::getKey).orElse(null);

        String fastestBranch = byBranch.entrySet().stream()
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> s.getAvgPreparationTimeSeconds() != null))
                .min(Comparator.comparingDouble(e -> e.getValue().stream()
                        .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                        .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds)
                        .average().orElse(Double.MAX_VALUE)))
                .map(Map.Entry::getKey).orElse(null);

        String slowestBranch = byBranch.entrySet().stream()
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> s.getAvgPreparationTimeSeconds() != null))
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                        .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds)
                        .average().orElse(0.0)))
                .map(Map.Entry::getKey).orElse(null);

        // Daily breakdown: one entry per day in the range
        List<AnalyticsDtos.HourlySalesResponse> dailyBreakdown = buildDailyBreakdown(current, start, end);

        return AnalyticsDtos.AdminAnalyticsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .totalBranches(branchCount)
                .totalCustomers(0L)
                .completionRate(completionRate)
                .cancellationRate(cancellationRate)
                .revenueGrowthPercent(revenueGrowth)
                .ordersGrowthPercent(ordersGrowth)
                .topPerformingBranch(topBranch)
                .fastestBranch(fastestBranch)
                .slowestBranch(slowestBranch)
                .dailyBreakdown(dailyBreakdown)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analytics.branchAnalytics", key = "#startDate + '-' + #endDate")
    public List<AnalyticsDtos.BranchAnalyticsResponse> getBranchAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository.findBySummaryDateBetween(start, end);
        Map<String, List<BranchDailySummary>> byBranch = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.atTime(LocalTime.MAX);

        return byBranch.entrySet().stream()
                .map(entry -> {
                    String bid = entry.getKey();
                    List<BranchDailySummary> bs = entry.getValue();

                    long orders    = bs.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    long completed = bs.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
                    long cancelled = bs.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
                    double revenue = bs.stream().mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
                    double avgOV   = orders > 0 ? revenue / orders : 0.0;
                    double compRate = orders > 0 ? (double) completed / orders * 100.0 : 0.0;
                    double canRate  = orders > 0 ? (double) cancelled / orders * 100.0 : 0.0;

                    OptionalDouble avgPrepOpt = bs.stream()
                            .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                            .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds)
                            .average();
                    Double avgPrepMin = avgPrepOpt.isPresent() ? avgPrepOpt.getAsDouble() / 60.0 : null;

                    List<AnalyticsDtos.PopularItemResponse> topItems =
                            orderItemAnalyticsRepository.findTopItemsByBranch(bid, startDt, endDt)
                                    .stream().limit(3)
                                    .map(p -> AnalyticsDtos.PopularItemResponse.builder()
                                            .id(p.getMenuItemId())
                                            .name(p.getMenuItemName())
                                            .quantitySold(p.getTotalQuantity())
                                            .revenue(p.getTotalRevenue().doubleValue())
                                            .build())
                                    .collect(Collectors.toList());

                    return AnalyticsDtos.BranchAnalyticsResponse.builder()
                            .id(bid)
                            .name(bid)
                            .orders(orders)
                            .revenue(revenue)
                            .avgOrderValue(avgOV)
                            .completionRate(compRate)
                            .cancellationRate(canRate)
                            .avgPreparationTimeMinutes(avgPrepMin)
                            .topItems(topItems)
                            .build();
                })
                .sorted(Comparator.comparingDouble(AnalyticsDtos.BranchAnalyticsResponse::getRevenue).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analytics.overview", key = "#startDate + '-' + #endDate")
    public AnalyticsDtos.OverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> current = branchDailySummaryRepository.findBySummaryDateBetween(start, end);

        long totalOrders    = current.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        long completedTotal = current.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
        long cancelledTotal = current.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
        BigDecimal totalRevenue = current.stream().map(BranchDailySummary::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long branchCount = current.stream().map(BranchDailySummary::getBranchId).distinct().count();
        BigDecimal avgOV = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double completionRate   = totalOrders > 0 ? (double) completedTotal / totalOrders * 100.0 : 0.0;
        double cancellationRate = totalOrders > 0 ? (double) cancelledTotal / totalOrders * 100.0 : 0.0;

        long periodDays  = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate priorStart = start.minusDays(periodDays);
        List<BranchDailySummary> prior = branchDailySummaryRepository
                .findBySummaryDateBetween(priorStart, start.minusDays(1));
        long priorOrders   = prior.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
        double priorRevDbl = prior.stream().mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
        double revDbl      = totalRevenue.doubleValue();
        double revenueGrowth = priorRevDbl > 0 ? ((revDbl - priorRevDbl) / priorRevDbl) * 100.0 : 0.0;
        double ordersGrowth  = priorOrders  > 0 ? ((double) (totalOrders - priorOrders) / priorOrders) * 100.0 : 0.0;

        Map<String, List<BranchDailySummary>> byBranch = current.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        String topBranch = byBranch.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum()))
                .map(Map.Entry::getKey).orElse(null);

        String fastestBranch = byBranch.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(s -> s.getAvgPreparationTimeSeconds() != null))
                .min(Comparator.comparingDouble(e -> e.getValue().stream()
                        .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                        .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds).average()
                        .orElse(Double.MAX_VALUE)))
                .map(Map.Entry::getKey).orElse(null);

        String slowestBranch = byBranch.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(s -> s.getAvgPreparationTimeSeconds() != null))
                .max(Comparator.comparingDouble(e -> e.getValue().stream()
                        .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                        .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds).average()
                        .orElse(0.0)))
                .map(Map.Entry::getKey).orElse(null);

        return AnalyticsDtos.OverviewResponse.builder()
                .startDate(start).endDate(end)
                .totalOrders(totalOrders).totalRevenue(totalRevenue).avgOrderValue(avgOV)
                .completionRate(completionRate).cancellationRate(cancellationRate)
                .revenueGrowthPercent(revenueGrowth).ordersGrowthPercent(ordersGrowth)
                .topPerformingBranch(topBranch).fastestBranch(fastestBranch).slowestBranch(slowestBranch)
                .totalBranches(branchCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analytics.branchComparison", key = "#startDate + '-' + #endDate")
    public List<AnalyticsDtos.BranchComparisonResponse> getBranchComparison(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();

        List<BranchDailySummary> summaries = branchDailySummaryRepository.findBySummaryDateBetween(start, end);
        Map<String, List<BranchDailySummary>> byBranch = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getBranchId));

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.atTime(LocalTime.MAX);

        return byBranch.entrySet().stream()
                .map(entry -> {
                    String bid = entry.getKey();
                    List<BranchDailySummary> bs = entry.getValue();

                    long orders    = bs.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    long completed = bs.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
                    long cancelled = bs.stream().mapToLong(BranchDailySummary::getCancelledOrders).sum();
                    BigDecimal revenue = bs.stream().map(BranchDailySummary::getTotalRevenue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avgOV = orders > 0
                            ? revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    double compRate = orders > 0 ? (double) completed / orders * 100.0 : 0.0;
                    double canRate  = orders > 0 ? (double) cancelled / orders * 100.0 : 0.0;

                    OptionalDouble avgPrepOpt = bs.stream()
                            .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                            .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds).average();
                    Double avgPrepMin = avgPrepOpt.isPresent() ? avgPrepOpt.getAsDouble() / 60.0 : null;

                    List<AnalyticsDtos.PopularItemResponse> topItems =
                            orderItemAnalyticsRepository.findTopItemsByBranch(bid, startDt, endDt)
                                    .stream().limit(5)
                                    .map(p -> AnalyticsDtos.PopularItemResponse.builder()
                                            .id(p.getMenuItemId())
                                            .name(p.getMenuItemName())
                                            .quantitySold(p.getTotalQuantity())
                                            .revenue(p.getTotalRevenue().doubleValue())
                                            .build())
                                    .collect(Collectors.toList());

                    return AnalyticsDtos.BranchComparisonResponse.builder()
                            .branchId(bid)
                            .totalOrders(orders)
                            .totalRevenue(revenue)
                            .avgOrderValue(avgOV)
                            .completionRate(compRate)
                            .cancellationRate(canRate)
                            .avgPreparationTimeMinutes(avgPrepMin)
                            .topItems(topItems)
                            .build();
                })
                .sorted(Comparator.comparingDouble(r -> -r.getTotalRevenue().doubleValue()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.TrendsResponse getTrends(String branchId, LocalDate startDate,
                                                    LocalDate endDate, String interval) {
        LocalDate start    = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end      = endDate   != null ? endDate   : LocalDate.now();
        String resolvedInterval = interval != null ? interval.toUpperCase() : "DAY";

        List<BranchDailySummary> summaries = (branchId != null && !branchId.isBlank())
                ? branchDailySummaryRepository.findByBranchIdAndSummaryDateBetweenOrderBySummaryDateAsc(branchId, start, end)
                : branchDailySummaryRepository.findBySummaryDateBetween(start, end);

        // Group daily summaries into the requested interval bucket
        Map<String, List<BranchDailySummary>> grouped = summaries.stream()
                .collect(Collectors.groupingBy(s -> toBucket(s.getSummaryDate(), resolvedInterval)));

        List<AnalyticsDtos.TrendDataPoint> dataPoints = new TreeMap<>(grouped).entrySet().stream()
                .map(entry -> {
                    List<BranchDailySummary> bucket = entry.getValue();
                    long orders    = bucket.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
                    long completed = bucket.stream().mapToLong(BranchDailySummary::getCompletedOrders).sum();
                    BigDecimal revenue = bucket.stream().map(BranchDailySummary::getTotalRevenue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double compRate = orders > 0 ? (double) completed / orders * 100.0 : 0.0;
                    OptionalDouble avgPrepOpt = bucket.stream()
                            .filter(s -> s.getAvgPreparationTimeSeconds() != null)
                            .mapToLong(BranchDailySummary::getAvgPreparationTimeSeconds).average();
                    Double avgPrepMin = avgPrepOpt.isPresent() ? avgPrepOpt.getAsDouble() / 60.0 : null;
                    return AnalyticsDtos.TrendDataPoint.builder()
                            .period(entry.getKey())
                            .revenue(revenue)
                            .orders(orders)
                            .completionRate(compRate)
                            .avgPreparationTimeMinutes(avgPrepMin)
                            .build();
                })
                .collect(Collectors.toList());

        return AnalyticsDtos.TrendsResponse.builder()
                .startDate(start).endDate(end)
                .interval(resolvedInterval)
                .branchId(branchId)
                .dataPoints(dataPoints)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.OperationalAnalyticsResponse getOperationalAnalytics(String branchId,
                                                                               LocalDate startDate,
                                                                               LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.atTime(LocalTime.MAX);

        List<OrderAnalytics> orders = (branchId != null && !branchId.isBlank())
                ? orderAnalyticsRepository.findByBranchIdAndOrderReceivedAtBetween(branchId, startDt, endDt)
                : orderAnalyticsRepository.findByOrderReceivedAtBetween(startDt, endDt);

        long total = orders.size();

        // Orders by status
        Map<String, Long> byStatus = orders.stream()
                .collect(Collectors.groupingBy(OrderAnalytics::getStatus, Collectors.counting()));

        List<AnalyticsDtos.OrdersByStatusEntry> statusEntries = byStatus.entrySet().stream()
                .map(e -> AnalyticsDtos.OrdersByStatusEntry.builder()
                        .status(e.getKey())
                        .count(e.getValue())
                        .percentage(total > 0 ? (double) e.getValue() / total * 100.0 : 0.0)
                        .build())
                .sorted(Comparator.comparingLong(AnalyticsDtos.OrdersByStatusEntry::getCount).reversed())
                .collect(Collectors.toList());

        // Orders by hour across the full day range
        Map<Integer, List<OrderAnalytics>> byHour = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderReceivedAt().getHour()));

        List<AnalyticsDtos.HourlySalesResponse> hourlyBreakdown = new ArrayList<>();
        for (int hour = 0; hour <= 23; hour++) {
            List<OrderAnalytics> hourOrders = byHour.getOrDefault(hour, List.of());
            double revenue = hourOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                    .sum();
            hourlyBreakdown.add(AnalyticsDtos.HourlySalesResponse.builder()
                    .hour(String.format("%02d:00", hour))
                    .revenue(revenue)
                    .orders(hourOrders.size())
                    .build());
        }

        String peakHour = hourlyBreakdown.stream()
                .max(Comparator.comparingLong(AnalyticsDtos.HourlySalesResponse::getOrders))
                .map(AnalyticsDtos.HourlySalesResponse::getHour)
                .orElse(null);

        return AnalyticsDtos.OperationalAnalyticsResponse.builder()
                .ordersByStatus(statusEntries)
                .ordersByHour(hourlyBreakdown)
                .peakHour(peakHour)
                .totalOrders(total)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analytics.popularItems",
               key = "(#branchId ?: 'all') + '-' + #startDate + '-' + #endDate + '-' + #limit")
    public List<AnalyticsDtos.PopularItemResponse> getPopularItemsForPeriod(String branchId,
                                                                             LocalDate startDate,
                                                                             LocalDate endDate,
                                                                             int limit) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.atTime(LocalTime.MAX);

        List<OrderItemAnalyticsRepository.ItemPopularityProjection> projections =
                (branchId != null && !branchId.isBlank())
                        ? orderItemAnalyticsRepository.findTopItemsByBranch(branchId, startDt, endDt)
                        : orderItemAnalyticsRepository.findTopItemsAllBranches(startDt, endDt);

        return projections.stream()
                .limit(limit)
                .map(p -> AnalyticsDtos.PopularItemResponse.builder()
                        .id(p.getMenuItemId())
                        .name(p.getMenuItemName())
                        .quantitySold(p.getTotalQuantity())
                        .revenue(p.getTotalRevenue().doubleValue())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AnalyticsDtos.BranchSummaryResponse toSummaryResponse(BranchDailySummary s) {
        int inProgress = Math.max(s.getTotalOrders() - s.getCompletedOrders() - s.getCancelledOrders(), 0);
        return AnalyticsDtos.BranchSummaryResponse.builder()
                .branchId(s.getBranchId())
                .date(s.getSummaryDate())
                .totalOrders(s.getTotalOrders())
                .completedOrders(s.getCompletedOrders())
                .cancelledOrders(s.getCancelledOrders())
                .inProgressOrders(inProgress)
                .totalRevenue(s.getTotalRevenue())
                .avgOrderValue(s.getAvgOrderValue())
                .dineInCount(s.getDineInCount())
                .takeawayCount(s.getTakeawayCount())
                .deliveryCount(s.getDeliveryCount())
                .avgPreparationTimeSeconds(s.getAvgPreparationTimeSeconds())
                .completionRate(s.getCompletionRate())
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

    /** Groups a date into the label for the requested interval bucket. */
    private String toBucket(LocalDate date, String interval) {
        return switch (interval) {
            case "WEEK"  -> date.getYear() + "-W" +
                    String.format("%02d", date.get(WeekFields.of(DayOfWeek.MONDAY, 4).weekOfYear()));
            case "MONTH" -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            default      -> date.format(DateTimeFormatter.ISO_LOCAL_DATE); // DAY
        };
    }

    /** Builds a daily revenue/orders breakdown for the admin analytics response. */
    private List<AnalyticsDtos.HourlySalesResponse> buildDailyBreakdown(
            List<BranchDailySummary> summaries, LocalDate start, LocalDate end) {

        Map<LocalDate, List<BranchDailySummary>> byDate = summaries.stream()
                .collect(Collectors.groupingBy(BranchDailySummary::getSummaryDate));

        List<AnalyticsDtos.HourlySalesResponse> result = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<BranchDailySummary> day = byDate.getOrDefault(cursor, List.of());
            double rev   = day.stream().mapToDouble(s -> s.getTotalRevenue().doubleValue()).sum();
            long   cnt   = day.stream().mapToLong(BranchDailySummary::getTotalOrders).sum();
            result.add(AnalyticsDtos.HourlySalesResponse.builder()
                    .hour(cursor.toString())
                    .revenue(rev)
                    .orders(cnt)
                    .build());
            cursor = cursor.plusDays(1);
        }
        return result;
    }
}
