package com.foodloop.service;

import com.foodloop.dto.AdminStatsResponse;
import com.foodloop.entity.enums.Availability;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.repository.StatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceTest {

    @Mock StatsRepository stats;
    @InjectMocks StatsService service;

    private static Object[] row(Object... cells) {
        return cells;
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    private static BigDecimal kg(String value) {
        return new BigDecimal(value);
    }

    @Test
    void donationTotalsAndZeroFilledStatusMap() {
        when(stats.donationsByStatus()).thenReturn(rows(
                row(DonationStatus.AVAILABLE, 3L, kg("30.5")),
                row(DonationStatus.COMPLETED, 2L, kg("50.00"))));
        when(stats.countDonationsSince(any())).thenReturn(4L);

        AdminStatsResponse.Donations d = service.adminStats().donations();

        assertEquals(5, d.total());
        assertEquals(4, d.last7Days());
        assertEquals(0, kg("80.5").compareTo(d.totalKg()));
        assertEquals(3L, d.byStatus().get("AVAILABLE"));
        assertEquals(2L, d.byStatus().get("COMPLETED"));
        assertEquals(0L, d.byStatus().get("EXPIRED"));
        assertEquals(DonationStatus.values().length, d.byStatus().size());
    }

    @Test
    void rescuedKgIsTheCompletedDonationsKg() {
        when(stats.donationsByStatus()).thenReturn(rows(
                row(DonationStatus.COMPLETED, 2L, kg("50.00")),
                row(DonationStatus.CANCELLED, 1L, kg("9.00"))));

        assertEquals(0, kg("50").compareTo(service.adminStats().donations().rescuedKg()));
    }

    @Test
    void nullKgIsTreatedAsZero() {
        when(stats.donationsByStatus()).thenReturn(rows(row(DonationStatus.MATCHED, 1L, null)));

        assertEquals(0, BigDecimal.ZERO.compareTo(service.adminStats().donations().totalKg()));
    }

    @Test
    void deliveryBreakdown() {
        when(stats.deliveriesByStatus()).thenReturn(rows(
                row(DeliveryStatus.NOT_STARTED, 1L),
                row(DeliveryStatus.IN_TRANSIT, 2L),
                row(DeliveryStatus.DELIVERED, 1L),
                row(DeliveryStatus.RECEIPT_CONFIRMED, 6L),
                row(DeliveryStatus.FAILED, 2L)));
        when(stats.sumReceivedKg(DeliveryStatus.RECEIPT_CONFIRMED)).thenReturn(kg("120.75"));

        AdminStatsResponse.Deliveries d = service.adminStats().deliveries();

        assertEquals(12, d.total());
        assertEquals(3, d.inProgress());
        assertEquals(1, d.delivered());
        assertEquals(6, d.confirmed());
        assertEquals(2, d.failed());
        assertEquals(0, kg("120.75").compareTo(d.receivedKg()));
    }

    @Test
    void volunteerAvailabilityIsZeroFilled() {
        when(stats.volunteersByAvailability()).thenReturn(rows(
                row(Availability.AVAILABLE, 2L),
                row(Availability.BUSY, 1L)));

        AdminStatsResponse.Volunteers v = service.adminStats().volunteers();

        assertEquals(3, v.total());
        assertEquals(2L, v.byAvailability().get("AVAILABLE"));
        assertEquals(0L, v.byAvailability().get("OFFLINE"));
    }

    @Test
    void categoriesAreMappedInOrder() {
        when(stats.donationsByCategory()).thenReturn(rows(
                row("Cooked Food", 7L, kg("70")),
                row("Dairy", 2L, null)));

        List<AdminStatsResponse.CategoryStat> c = service.adminStats().byCategory();

        assertEquals(2, c.size());
        assertEquals("Cooked Food", c.get(0).category());
        assertEquals(7, c.get(0).donations());
        assertEquals("Dairy", c.get(1).category());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.get(1).kg()));
    }

    @Test
    void emptyDatabaseGivesZerosNotErrors() {
        AdminStatsResponse s = service.adminStats();

        assertEquals(0, s.donations().total());
        assertEquals(0, s.deliveries().total());
        assertEquals(0, s.volunteers().total());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.deliveries().receivedKg()));
        assertTrue(s.byCategory().isEmpty());
    }
}