package com.foodloop.service;

import com.foodloop.dto.AdminStatsResponse;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.repository.StatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final StatsRepository stats;

    public StatsService(StatsRepository stats) {
        this.stats = stats;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse adminStats() {
        return new AdminStatsResponse(donations(), deliveries(), volunteers(), categories());
    }

    private AdminStatsResponse.Donations donations() {
        Map<String, Long> byStatus = zeroed(DonationStatus.values());
        long total = 0;
        BigDecimal totalKg = BigDecimal.ZERO;
        BigDecimal rescuedKg = BigDecimal.ZERO;
        for (Object[] row : stats.donationsByStatus()) {
            DonationStatus status = (DonationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal kg = decimal(row[2]);
            byStatus.put(status.name(), count);
            total += count;
            totalKg = totalKg.add(kg);
            if (status == DonationStatus.COMPLETED) {
                rescuedKg = kg;
            }
        }
        long last7Days = stats.countDonationsSince(LocalDateTime.now().minusDays(7));
        return new AdminStatsResponse.Donations(total, last7Days, totalKg, rescuedKg, byStatus);
    }

    private AdminStatsResponse.Deliveries deliveries() {
        Map<String, Long> byStatus = zeroed(DeliveryStatus.values());
        long total = 0;
        for (Object[] row : stats.deliveriesByStatus()) {
            long count = ((Number) row[1]).longValue();
            byStatus.put(((DeliveryStatus) row[0]).name(), count);
            total += count;
        }
        long inProgress = byStatus.get(DeliveryStatus.NOT_STARTED.name())
                + byStatus.get(DeliveryStatus.PICKED_UP.name())
                + byStatus.get(DeliveryStatus.IN_TRANSIT.name());
        BigDecimal receivedKg = decimal(stats.sumReceivedKg(DeliveryStatus.RECEIPT_CONFIRMED));
        return new AdminStatsResponse.Deliveries(total, inProgress,
                byStatus.get(DeliveryStatus.DELIVERED.name()),
                byStatus.get(DeliveryStatus.RECEIPT_CONFIRMED.name()),
                byStatus.get(DeliveryStatus.FAILED.name()),
                receivedKg, byStatus);
    }

    private AdminStatsResponse.Volunteers volunteers() {
        Map<String, Long> byAvailability = zeroed(Availability.values());
        long total = 0;
        for (Object[] row : stats.volunteersByAvailability()) {
            long count = ((Number) row[1]).longValue();
            byAvailability.put(((Availability) row[0]).name(), count);
            total += count;
        }
        return new AdminStatsResponse.Volunteers(total, byAvailability);
    }

    private List<AdminStatsResponse.CategoryStat> categories() {
        List<AdminStatsResponse.CategoryStat> result = new ArrayList<>();
        for (Object[] row : stats.donationsByCategory()) {
            result.add(new AdminStatsResponse.CategoryStat(
                    String.valueOf(row[0]), ((Number) row[1]).longValue(), decimal(row[2])));
        }
        return result;
    }

    private static Map<String, Long> zeroed(Enum<?>[] values) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Enum<?> value : values) {
            map.put(value.name(), 0L);
        }
        return map;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }
}