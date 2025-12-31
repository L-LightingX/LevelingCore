package com.azuredoom.levelingcore.database;

import com.azuredoom.levelingcore.exceptions.DataSourceConfigurationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A factory class for creating and configuring database connection pools using HikariCP.
 * <p>
 * This class provides a utility method to construct a {@link HikariDataSource} instance which can be used for managing
 * database connections in a high-performance and efficient manner. It is designed for ease of use and supports
 * configuration of core connection parameters such as JDBC URL, username, and password.
 * <p>
 * This class is not meant to be instantiated and is designed to be used statically.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {}

    /**
     * Creates and returns a configured HikariCP connection pool (HikariDataSource) based on the provided inputs.
     * Ensures that the connection pool is initialized and validates connectivity with the database.
     *
     * @param jdbcUrl     The JDBC URL used for the database connection. Must not be null or blank and should start with
     *                    the prefix "jdbc:". Example: "jdbc:postgresql://host:5432/db".
     * @param username    The database username used for authentication. Must not be null or blank.
     * @param password    The database password used for authentication. May be null or blank, depending on the database
     *                    configuration.
     * @param maxPoolSize The maximum size of the connection pool. Must be greater than or equal to 1.
     * @return A fully initialized HikariDataSource configured with the specified parameters.
     * @throws IllegalArgumentException         If any of the parameters do not meet the specified conditions.
     * @throws DataSourceConfigurationException If the connection pool initialization or validation fails.
     */
    public static HikariDataSource create(
        String jdbcUrl,
        String username,
        String password,
        int maxPoolSize
    ) {
        validateBasicConfig(jdbcUrl, username, maxPoolSize);

        var cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);

        cfg.setDriverClassName(driverClassNameFor(jdbcUrl));

        cfg.setMaximumPoolSize(maxPoolSize);
        cfg.setMinimumIdle(1);

        cfg.setInitializationFailTimeout(10_000);
        cfg.setConnectionTimeout(10_000);
        cfg.setValidationTimeout(5_000);

        HikariDataSource ds;
        try {
            ds = new HikariDataSource(cfg);
        } catch (RuntimeException e) {
            throw DataSourceConfigurationException.from("Failed to initialize connection pool", jdbcUrl, username, e);
        }

        try (var c = ds.getConnection()) {
            c.isValid(2);
        } catch (Exception e) {
            try {
                ds.close();
            } catch (Exception ignored) {}
            throw DataSourceConfigurationException.from("Database connection test failed", jdbcUrl, username, e);
        }

        return ds;
    }

    /**
     * Checks if the provided JDBC URL corresponds to an H2 database.
     *
     * @param jdbcUrl The JDBC URL used for the database connection. It may be null. If specified, it is expected to
     *                start with the prefix "jdbc:h2:".
     * @return true if the provided JDBC URL is non-null and starts with "jdbc:h2:". false otherwise.
     */
    private static boolean isH2(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:h2:");
    }

    /**
     * Validates the basic configuration parameters required for establishing a database connection.
     *
     * @param jdbcUrl     The JDBC URL used for the database connection. It must not be null or blank and should start
     *                    with the prefix "jdbc:". Example: "jdbc:postgresql://host:5432/db".
     * @param username    The database username used for authentication. It must not be null or blank.
     * @param maxPoolSize The maximum size of the connection pool. It must be greater than or equal to 1.
     * @throws IllegalArgumentException If any of the parameters do not meet the specified conditions.
     */
    private static void validateBasicConfig(String jdbcUrl, String username, int maxPoolSize) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl is missing/blank (example: jdbc:postgresql://host:5432/db)");
        }
        if (!jdbcUrl.toLowerCase().startsWith("jdbc:")) {
            throw new IllegalArgumentException("jdbcUrl must start with 'jdbc:' (got: " + jdbcUrl + ")");
        }
        if (!isH2(jdbcUrl) && (username == null || username.isBlank())) {
            throw new IllegalArgumentException("database username is missing/blank");
        }
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize must be >= 1 (got: " + maxPoolSize + ")");
        }
    }

    /**
     * Determines the appropriate driver class name based on the provided JDBC URL.
     *
     * @param jdbcUrl The JDBC URL used to establish a database connection. Supports schemes: mysql, mariadb,
     *                postgresql, h2.
     * @return The fully qualified name of the JDBC driver class corresponding to the specified JDBC URL.
     * @throws IllegalArgumentException If the JDBC URL scheme is not supported.
     */
    private static String driverClassNameFor(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase();

        if (url.startsWith("jdbc:mysql:"))
            return "com.mysql.cj.jdbc.Driver";
        if (url.startsWith("jdbc:mariadb:"))
            return "org.mariadb.jdbc.Driver";
        if (url.startsWith("jdbc:postgresql:"))
            return "org.postgresql.Driver";
        if (url.startsWith("jdbc:h2:"))
            return "org.h2.Driver";

        throw new IllegalArgumentException(
            "Unsupported jdbcUrl scheme. Supported: mysql, mariadb, postgresql, h2. Got: " + jdbcUrl
        );
    }
}
