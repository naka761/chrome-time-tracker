package com.example.chrometimetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.chrometimetracker.dto.AnalyticsResponse;
import com.example.chrometimetracker.dto.CategoryUsageResponse;
import com.example.chrometimetracker.dto.HourlyUsageResponse;
import com.example.chrometimetracker.model.ActivityInterval;
import com.example.chrometimetracker.repository.ActivityLogRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final LocalDate TARGET_DATE =
            LocalDate.of(2026, 1, 15);

    @Mock
    private ActivityLogRepository activityLogRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                activityLogRepository,
                new SiteCategoryClassifier(),
                new ActivityIntervalNormalizer()
        );

        when(activityLogRepository.findFirstStartedAt())
                .thenReturn(Optional.empty());
    }

    @Test
    void aggregatesNormalIntervals() {
        AnalyticsResponse analytics = analyze(
                interval(
                        1,
                        "chatgpt.com",
                        19,
                        0,
                        19,
                        20
                ),
                interval(
                        2,
                        "github.com",
                        19,
                        20,
                        19,
                        50
                )
        );

        HourlyUsageResponse hour =
                analytics.hourly().get(19);

        assertEquals(3000, hour.totalSeconds());
        assertEquals(
                Map.of(
                        "ai", 1200L,
                        "development", 1800L
                ),
                categorySeconds(hour.categories())
        );
    }

    @Test
    void truncatesPartialOverlapAtNextStart() {
        AnalyticsResponse analytics = analyze(
                interval(
                        1,
                        "chatgpt.com",
                        19,
                        0,
                        19,
                        40
                ),
                interval(
                        2,
                        "github.com",
                        19,
                        30,
                        19,
                        55
                )
        );

        HourlyUsageResponse hour =
                analytics.hourly().get(19);

        assertEquals(3300, hour.totalSeconds());
        assertEquals(
                Map.of(
                        "ai", 1800L,
                        "development", 1500L
                ),
                categorySeconds(hour.categories())
        );
    }

    @Test
    void doesNotRestoreContainingIntervalAfterNestedInterval() {
        AnalyticsResponse analytics = analyze(
                interval(
                        1,
                        "chatgpt.com",
                        19,
                        0,
                        20,
                        0
                ),
                interval(
                        2,
                        "google.com",
                        19,
                        20,
                        19,
                        30
                )
        );

        HourlyUsageResponse hour =
                analytics.hourly().get(19);

        assertEquals(1800, hour.totalSeconds());
        assertEquals(
                Map.of(
                        "ai", 1200L,
                        "search", 600L
                ),
                categorySeconds(hour.categories())
        );
    }

    @Test
    void higherIdWinsWhenIntervalsStartTogether() {
        AnalyticsResponse analytics = analyze(
                interval(
                        10,
                        "chatgpt.com",
                        19,
                        0,
                        19,
                        30
                ),
                interval(
                        11,
                        "github.com",
                        19,
                        0,
                        19,
                        10
                )
        );

        HourlyUsageResponse hour =
                analytics.hourly().get(19);

        assertEquals(600, hour.totalSeconds());
        assertEquals(
                Map.of("development", 600L),
                categorySeconds(hour.categories())
        );
    }

    @Test
    void splitsIntervalAtHourBoundary() {
        AnalyticsResponse analytics = analyze(
                interval(
                        1,
                        "chatgpt.com",
                        18,
                        55,
                        19,
                        5
                )
        );

        assertEquals(
                300,
                analytics.hourly().get(18).totalSeconds()
        );
        assertEquals(
                300,
                analytics.hourly().get(19).totalSeconds()
        );
    }

    @Test
    void keepsHourlyDailyAndCategoryTotalsConsistent() {
        AnalyticsResponse analytics = analyze(
                interval(
                        1,
                        "chatgpt.com",
                        0,
                        0,
                        23,
                        59
                ),
                interval(
                        2,
                        "github.com",
                        19,
                        30,
                        19,
                        55
                )
        );

        long hourlyTotal = analytics
                .hourly()
                .stream()
                .mapToLong(
                        HourlyUsageResponse::totalSeconds
                )
                .sum();

        long categoryTotal = analytics
                .categories()
                .stream()
                .mapToLong(
                        CategoryUsageResponse::seconds
                )
                .sum();

        assertTrue(
                analytics.hourly()
                        .stream()
                        .allMatch(
                                hour ->
                                        hour.totalSeconds()
                                        <= 3600
                        )
        );

        assertTrue(
                analytics.hourly()
                        .stream()
                        .allMatch(
                                hour ->
                                        hour.totalSeconds()
                                        == hour
                                                .categories()
                                                .stream()
                                                .mapToLong(
                                                        CategoryUsageResponse::seconds
                                                )
                                                .sum()
                        )
        );

        assertTrue(analytics.totalSeconds() <= 86400);
        assertEquals(analytics.totalSeconds(), hourlyTotal);
        assertEquals(analytics.totalSeconds(), categoryTotal);
    }

    private AnalyticsResponse analyze(
            ActivityInterval... intervals
    ) {
        when(
                activityLogRepository.findIntervals(
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(List.of(intervals));

        return analyticsService.getAnalytics(
                TARGET_DATE
        );
    }

    private ActivityInterval interval(
            long id,
            String site,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        ZoneId zoneId = ZoneId.systemDefault();

        Instant start = TARGET_DATE
                .atTime(
                        startHour,
                        startMinute
                )
                .atZone(zoneId)
                .toInstant();

        LocalDate endDate = endHour == 0
                && startHour > 0
                        ? TARGET_DATE.plusDays(1)
                        : TARGET_DATE;

        Instant end = endDate
                .atTime(
                        endHour,
                        endMinute
                )
                .atZone(zoneId)
                .toInstant();

        return new ActivityInterval(
                id,
                site,
                start,
                end
        );
    }

    private Map<String, Long> categorySeconds(
            List<CategoryUsageResponse> categories
    ) {
        return categories
                .stream()
                .collect(
                        Collectors.toMap(
                                CategoryUsageResponse::category,
                                CategoryUsageResponse::seconds
                        )
                );
    }
}
