package quiz;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Shared JDBC connections: PostgreSQL online, H2 for local runs and tests. */
public final class DatabaseConnection {
    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        String override = System.getProperty("quiz.db");
        if (override != null) return DriverManager.getConnection(override, "sa", "");
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            URI uri = URI.create(databaseUrl);
            if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme()))
                throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL URL.");
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            String host = uri.getHost();
            if (host.contains(":")) host = "[" + host + "]";
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) query = "sslmode=require";
            else if (!query.contains("sslmode=")) query += "&sslmode=require";
            String jdbc = "jdbc:postgresql://" + host + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort())
                + uri.getRawPath() + "?" + query;
            return DriverManager.getConnection(jdbc, decode(credentials[0]),
                credentials.length > 1 ? decode(credentials[1]) : "");
        }
        return DriverManager.getConnection(System.getenv().getOrDefault("QUIZ_DB_URL", "jdbc:h2:./data/quiz"),
            System.getenv().getOrDefault("QUIZ_DB_USER", "sa"), System.getenv().getOrDefault("QUIZ_DB_PASSWORD", ""));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
