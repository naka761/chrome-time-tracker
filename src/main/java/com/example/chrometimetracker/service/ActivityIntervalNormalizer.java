package com.example.chrometimetracker.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.chrometimetracker.model.ActivityInterval;

/**
 * activity_logの区間を、同時に1サイトだけとなるよう正規化する。
 */
@Component
public class ActivityIntervalNormalizer {

    /**
     * 後から開始された区間を優先し、前の区間をその開始時刻で打ち切る。
     *
     * started_atが同じ場合はIDが大きい行を新しい記録として扱う。
     * 0秒以下となった区間は集計対象から除外する。
     */
    public List<ActivityInterval> normalize(
            List<ActivityInterval> intervals
    ) {
        List<ActivityInterval> sorted = intervals
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        ActivityInterval::startedAt
                                )
                                .thenComparingLong(
                                        ActivityInterval::id
                                )
                )
                .toList();

        List<ActivityInterval> normalized =
                new ArrayList<>();

        for (int index = 0;
                index < sorted.size();
                index++) {

            ActivityInterval current =
                    sorted.get(index);

            Instant normalizedEnd =
                    current.endedAt();

            if (index + 1 < sorted.size()) {
                Instant nextStartedAt =
                        sorted
                                .get(index + 1)
                                .startedAt();

                if (nextStartedAt.isBefore(
                        normalizedEnd
                )) {
                    normalizedEnd = nextStartedAt;
                }
            }

            if (!current.startedAt().isBefore(
                    normalizedEnd
            )) {
                continue;
            }

            normalized.add(
                    new ActivityInterval(
                            current.id(),
                            current.site(),
                            current.startedAt(),
                            normalizedEnd
                    )
            );
        }

        return List.copyOf(normalized);
    }
}
