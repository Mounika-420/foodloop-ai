package com.foodloop.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AdminStatsResponse(Donations donations,
                                 Deliveries deliveries,
                                 Volunteers volunteers,
                                 List<CategoryStat> byCategory) {

    public record Donations(long total, long last7Days, BigDecimal totalKg, BigDecimal rescuedKg,
                            Map<String, Long> byStatus) {
    }

    public record Deliveries(long total, long inProgress, long delivered, long confirmed, long failed,
                             BigDecimal receivedKg, Map<String, Long> byStatus) {
    }

    public record Volunteers(long total, Map<String, Long> byAvailability) {
    }

    public record CategoryStat(String category, long donations, BigDecimal kg) {
    }
}