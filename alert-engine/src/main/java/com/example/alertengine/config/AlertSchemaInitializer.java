package com.example.alertengine.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class AlertSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(AlertSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public AlertSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensureIndexes() {
        if (!supportsPartialIndexes()) {
            log.info("event=schema_initializer_skipped reason=partial_index_unsupported");
            return;
        }

        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_active_machine_type " +
                            "ON alert (machine_id, alert_type) WHERE status = 'ACTIVE'"
            );
            log.info("event=schema_initialized object=uq_alert_active_machine_type");
        } catch (DataAccessException exception) {
            log.warn("event=schema_initializer_failed object=uq_alert_active_machine_type", exception);
        }
    }

    private boolean supportsPartialIndexes() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getMetaData().getDatabaseProductName();
            return databaseName != null && databaseName.toLowerCase().contains("postgres");
        } catch (SQLException exception) {
            log.warn("event=schema_initializer_database_detection_failed", exception);
            return false;
        }
    }
}
