package com.example.chrometimetracker.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.chrometimetracker.model.ActivityLog;
import com.example.chrometimetracker.repository.ActivityLogRepository;

@ExtendWith(MockitoExtension.class)
class TrackerServiceTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-01-15T10:00:00Z");

    private static final Instant OBSERVED_AT =
            Instant.parse("2026-01-15T10:05:00Z");

    @Mock
    private ActivityLogRepository activityLogRepository;

    private TrackerService trackerService;

    @BeforeEach
    void setUp() {
        trackerService = new TrackerService(
                activityLogRepository,
                new ActivityIntervalNormalizer()
        );
    }

    @Test
    void differentSiteClosesAllOpenActivitiesBeforeInsert() {
        when(activityLogRepository.findOpenActivity())
                .thenReturn(
                        Optional.of(
                                new ActivityLog(
                                        1,
                                        "chatgpt.com",
                                        STARTED_AT,
                                        null
                                )
                        )
                );

        trackerService.updateContext(
                "github.com",
                OBSERVED_AT
        );

        verify(activityLogRepository)
                .closeAllOpenActivities(OBSERVED_AT);

        verify(activityLogRepository)
                .insertActivity(
                        "github.com",
                        OBSERVED_AT
                );
    }

    @Test
    void sameSiteDoesNotWriteAnotherInterval() {
        when(activityLogRepository.findOpenActivity())
                .thenReturn(
                        Optional.of(
                                new ActivityLog(
                                        1,
                                        "chatgpt.com",
                                        STARTED_AT,
                                        null
                                )
                        )
                );

        trackerService.updateContext(
                "www.chatgpt.com",
                OBSERVED_AT
        );

        verify(activityLogRepository, never())
                .closeAllOpenActivities(OBSERVED_AT);

        verify(activityLogRepository, never())
                .insertActivity(
                        "chatgpt.com",
                        OBSERVED_AT
                );
    }
}
