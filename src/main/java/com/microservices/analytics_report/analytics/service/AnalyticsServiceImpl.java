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
