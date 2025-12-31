package com.azuredoom.levelingcore.exceptions;

import java.sql.SQLException;

public class DataSourceConfigurationException extends RuntimeException {

    public DataSourceConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a {@link DataSourceConfigurationException} by analyzing the provided context, JDBC URL, username, and
     * root cause of the exception. This method generates a detailed error message with specific explanations based on
     * the type of the root cause, such as {@link SQLException} or {@link ClassNotFoundException}.
     *
     * @param context  a description of the operation or context in which the error occurred. Must not be null.
     * @param jdbcUrl  the JDBC URL used for the database connection. May be null, in which case "null" is handled in
     *                 the error message. Passwords in the URL are redacted for security.
     * @param username the username used for authentication with the database. Must not be null.
     * @param cause    the underlying exception that caused the error. Must not be null.
     * @return a new instance of {@link DataSourceConfigurationException} containing a detailed error message and the
     *         provided cause.
     */
    public static DataSourceConfigurationException from(
        String context,
        String jdbcUrl,
        String username,
        Throwable cause
    ) {
        var root = rootCause(cause);
        var redacted = redactJdbcUrl(jdbcUrl);
        var base = context + " (jdbcUrl=" + redacted + ", username=" + username + "). ";

        if (root instanceof SQLException sqlEx) {
            return new DataSourceConfigurationException(base + explain(sqlEx), cause);
        }

        if (root instanceof ClassNotFoundException) {
            return new DataSourceConfigurationException(
                base +
                    "JDBC driver not found on the classpath. " +
                    "Make sure the correct driver dependency is installed for this jdbcUrl.",
                cause
            );
        }

        return new DataSourceConfigurationException(
            base +
                "Unexpected error while creating/connecting to the datasource: " +
                root.getClass().getSimpleName() + ": " + safeMsg(root.getMessage()),
            cause
        );
    }

    /**
     * Provides a descriptive explanation for a given {@link SQLException} by interpreting its SQL state and
     * vendor-specific error code. This method categorizes database connection issues, authentication failures, syntax
     * errors, or missing resources based on well-known SQLSTATE classifications.
     *
     * @param e the {@link SQLException} to explain. Must not be null.
     * @return a human-readable explanation describing the root cause of the database error. This includes the nature of
     *         the issue (e.g., connection failure, authentication problem, or resource issue), along with important
     *         details such as the SQLSTATE code, vendor-specific error code, and the accompanying error message.
     */
    private static String explain(SQLException e) {
        var state = e.getSQLState();
        var code = e.getErrorCode();
        var msg = safeMsg(e.getMessage());

        if (state != null) {
            if (state.startsWith("08")) {
                return "Connection failed (network/host/port/firewall). " +
                    "Check host, port, DB is running, and that the server allows your IP. " +
                    details(state, code, msg);
            }
            if (state.startsWith("28")) {
                return "Authentication failed. Check username/password and account permissions. " +
                    details(state, code, msg);
            }
            if (state.startsWith("3D")) { // often “invalid catalog name” in Postgres -> db does not exist
                return "Database does not exist (or is not reachable under that name). " +
                    "Check the database name in the JDBC URL. " +
                    details(state, code, msg);
            }
            if (state.startsWith("42")) {
                return "SQL error during initialization (syntax / permissions / missing table/schema). " +
                    details(state, code, msg);
            }
        }

        return "Database error while connecting. " + details(state, code, msg);
    }

    /**
     * Constructs a detailed message string describing an error or exception in terms of its SQL state, vendor-specific
     * error code, and an additional message. It provides context for diagnosing database-related issues.
     *
     * @param state the SQLSTATE code used to classify the nature of the database error. Can be null.
     * @param code  the vendor-specific error code representing the error type.
     * @param msg   the message describing the error or exception. Must not be null.
     * @return a formatted string containing the error details, including the SQLSTATE code, vendor error code, and the
     *         accompanying message. If the SQLSTATE code is null, "n/a" will be used instead.
     */
    private static String details(String state, int code, String msg) {
        var s = (state == null ? "n/a" : state);
        return "Root cause: " + msg + " (SQLSTATE=" + s + ", vendorCode=" + code + ")";
    }

    /**
     * Determines the root cause of a given throwable by traversing its cause chain. This method repeatedly retrieves
     * the cause of the throwable until it finds the originating cause or encounters a circular reference.
     *
     * @param t the throwable for which to determine the root cause. Must not be null.
     * @return the root cause of the given throwable. If the throwable has no cause, returns the input throwable itself.
     */
    private static Throwable rootCause(Throwable t) {
        var cur = t;
        while (cur.getCause() != null && cur.getCause() != cur)
            cur = cur.getCause();
        return cur;
    }

    /**
     * Ensures that a provided message string is not null. If the input message is null, a default fallback message ("no
     * message") is returned.
     *
     * @param msg the message string to check. Can be null.
     * @return the input message string if it is not null, otherwise the fallback message "no message".
     */
    private static String safeMsg(String msg) {
        return msg == null ? "no message" : msg;
    }

    /**
     * Redacts a sensitive portion of a JDBC URL by masking the password in the connection string. The password, if
     * present, is replaced with "****" to prevent exposing sensitive information.
     * <p>
     * Example of the redacted format: jdbc:mysql://username:****@host:port/database
     *
     * @param jdbcUrl the JDBC URL string to be redacted. If null, the method will return "null".
     * @return the redacted JDBC URL with the password masked, or "null" if the input was null.
     */
    private static String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null)
            return "null";
        return jdbcUrl.replaceAll("(//[^/@]*:)[^@]*@", "$1****@");
    }
}
