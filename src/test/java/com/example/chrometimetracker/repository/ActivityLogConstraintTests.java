package com.example.chrometimetracker.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ActivityLogConstraintTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void databaseRejectsSecondOpenActivity() {
        AtomicBoolean firstInsertCompleted =
                new AtomicBoolean(false);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> new TransactionTemplate(
                        transactionManager
                ).executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    """
                                    UPDATE activity_log
                                    SET ended_at = last_seen_at
                                    WHERE ended_at IS NULL
                                    """
                            );

                            insertOpenActivity(
                                    "first.constraint-test.invalid",
                                    Instant.parse(
                                            "2000-01-01T00:00:00Z"
                                    )
                            );

                            firstInsertCompleted.set(true);

                            insertOpenActivity(
                                    "second.constraint-test.invalid",
                                    Instant.parse(
                                            "2000-01-01T00:01:00Z"
                                    )
                            );
                        }
                )
        );

        assertTrue(firstInsertCompleted.get());
    }

    private void insertOpenActivity(
            String site,
            Instant startedAt
    ) {
        Timestamp timestamp =
                Timestamp.from(startedAt);

        jdbcTemplate.update(
                """
                INSERT INTO activity_log (
                    site,
                    started_at,
                    last_seen_at
                )
                VALUES (?, ?, ?)
                """,
                site,
                timestamp,
                timestamp
        );
    }
}
