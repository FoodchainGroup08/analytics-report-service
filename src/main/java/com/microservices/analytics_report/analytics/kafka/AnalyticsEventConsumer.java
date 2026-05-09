package com.microservices.analytics_report.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.analytics_report.analytics.dto.AnalyticsDtos;
import com.microservices.analytics_report.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsEventConsumer {

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.received", groupId = "analytics-report-service-group")
    public void handleOrderReceived(String message) {
        try {
            AnalyticsDtos.OrderReceivedEvent event =
                    objectMapper.readValue(message, AnalyticsDtos.OrderReceivedEvent.class);
            log.info("Analytics: order received orderId={} branchId={}", event.getOrderId(), event.getBranchId());
            analyticsService.recordOrderReceived(event);
        } catch (Exception e) {
            log.error("Failed to process order.received: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.status.updated", groupId = "analytics-report-service-group")
    public void handleOrderStatusUpdated(String message) {
        try {
            AnalyticsDtos.OrderStatusUpdatedEvent event =
                    objectMapper.readValue(message, AnalyticsDtos.OrderStatusUpdatedEvent.class);
            log.info("Analytics: status update orderId={} {} -> {}",
                    event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());
            analyticsService.updateOrderStatus(event);
        } catch (Exception e) {
            log.error("Failed to process order.status.updated: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "analytics.daily.rollup", groupId = "analytics-report-service-group")
    public void handleDailyRollup(String message) {
        try {
            log.info("Analytics: daily rollup triggered — {}", message);
            java.time.LocalDate date = message != null && !message.isBlank()
                    ? java.time.LocalDate.parse(message.trim().replace("\"", ""))
                    : java.time.LocalDate.now().minusDays(1);
            analyticsService.computeDailySummaries(date);
        } catch (Exception e) {
            log.error("Failed to process analytics.daily.rollup: {}", e.getMessage());
        }
    }
}
