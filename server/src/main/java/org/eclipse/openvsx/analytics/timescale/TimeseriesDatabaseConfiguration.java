/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.analytics.timescale;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The time-series database behind download analytics: its own PostgreSQL instance (with the
 * timescaledb extension), its own connection pool, its own Flyway migration set and its own
 * jOOQ context, all configured from {@code ovsx.analytics.datasource.*}. Nothing is created
 * unless {@code ovsx.analytics.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
class TimeseriesDatabaseConfiguration {

    private static final String DEFAULT_POOL_SIZE = "5";

    // A download records its event on the request path, so an unreachable time-series database has
    // to surface as a fast failure the caller can swallow. Hikari's 30 second default would hold a
    // request thread for that long on every download until the outage ends.
    private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "2000";

    // The derived validation timeout floors at this, and Hikari rejects a validation timeout that
    // is not below the connection timeout, so the connection timeout has to exceed it.
    private static final long VALIDATION_TIMEOUT_FLOOR_MS = 250L;

    /** JDBC URL of the time-series database. Required whenever analytics are enabled. */
    @Value("${ovsx.analytics.datasource.url:}")
    String url;

    /** User name for the time-series database. Optional: unset leaves it to the driver. */
    @Value("${ovsx.analytics.datasource.username:}")
    String username;

    /** Password for the time-series database. Optional: unset leaves it to the driver. */
    @Value("${ovsx.analytics.datasource.password:}")
    String password;

    /** Maximum size of the time-series connection pool. Default: {@code 5} */
    @Value("${ovsx.analytics.datasource.maximum-pool-size:" + DEFAULT_POOL_SIZE + "}")
    int maximumPoolSize;

    /** How long to wait for a connection from the pool, in ms. Default: {@code 2000} */
    @Value("${ovsx.analytics.datasource.connection-timeout:" + DEFAULT_CONNECTION_TIMEOUT_MS + "}")
    long connectionTimeout;

    /**
     * Rejects a half-configured analytics deployment at startup rather than at the first download.
     * These beans only exist when {@code ovsx.analytics.enabled=true}, so reaching here at all means
     * the operator asked for analytics, and a missing URL is a mistake rather than a way to opt out.
     */
    @PostConstruct
    void validateConfiguration() {
        if (StringUtils.isEmpty(url)) {
            throw new IllegalStateException(
                    "ovsx.analytics.datasource.url must be set when ovsx.analytics.enabled is true");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalStateException(
                    "ovsx.analytics.datasource.maximum-pool-size must be at least 1, but was " + maximumPoolSize);
        }
        if (connectionTimeout <= VALIDATION_TIMEOUT_FLOOR_MS) {
            throw new IllegalStateException(
                    "ovsx.analytics.datasource.connection-timeout must be greater than "
                            + VALIDATION_TIMEOUT_FLOOR_MS + "ms, but was " + connectionTimeout + "ms");
        }
    }

    // defaultCandidate = false keeps these beans invisible to @ConditionalOnMissingBean and to
    // plain by-type injection, so Boot still auto-configures the primary DataSource, the main
    // Flyway chain and the primary DSLContext; only an explicit @Qualifier reaches them.
    @Bean(destroyMethod = "close", defaultCandidate = false)
    DataSource timeseriesDataSource() {
        var config = new HikariConfig();
        config.setPoolName("timeseries");
        config.setJdbcUrl(url);
        // Unset has to reach Hikari as null, not "": an empty user name is a value, and the driver
        // would send it rather than fall back to its own resolution (a URL parameter, .pgpass, IAM).
        config.setUsername(emptyToNull(username));
        config.setPassword(emptyToNull(password));
        config.setMaximumPoolSize(maximumPoolSize);
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(Math.max(VALIDATION_TIMEOUT_FLOOR_MS, connectionTimeout / 2));
        return new HikariDataSource(config);
    }

    private static @Nullable String emptyToNull(String value) {
        return StringUtils.isEmpty(value) ? null : value;
    }

    /**
     * Migrates the time-series schema. Configured programmatically rather than through
     * {@code spring.flyway.*} so that the main and the time-series migration settings cannot leak
     * into each other; in particular there is no baseline, because this database starts empty and
     * an unexpected schema must fail the startup.
     */
    @Bean(defaultCandidate = false)
    Flyway timeseriesFlyway(@Qualifier("timeseriesDataSource") DataSource dataSource) {
        var flyway = Flyway.configure()
                .dataSource(dataSource)
                // a sibling of db/migration, never a child: Flyway scans locations recursively,
                // so a child would be swept into the registry's migration chain as well
                .locations("classpath:db/migration-timeseries")
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * Standalone jOOQ context on the time-series pool. Being outside Spring's transaction and
     * exception-translation infrastructure, queries throw jOOQ's {@code DataAccessException}
     * rather than Spring's, and never join a caller's registry transaction.
     */
    @Bean(defaultCandidate = false)
    DSLContext timeseriesDsl(
            @Qualifier("timeseriesDataSource") DataSource dataSource,
            // depended upon so the schema exists before the first query
            @Qualifier("timeseriesFlyway") Flyway flyway
    ) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
