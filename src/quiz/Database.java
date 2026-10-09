package quiz;

import java.nio.file.*;
import java.sql.*;

/** H2 for the original desktop app; PostgreSQL for the shared hosted app. */
public class Database {
    public static Connection connect() throws SQLException {
        return DatabaseConnection.getConnection();
    }
    public static void initialize() throws Exception {
        Files.createDirectories(Paths.get("data"));
        String schema=Files.readString(Paths.get("database/schema.sql"));
        try(Connection c=connect();Statement statement=c.createStatement()){
            for(String sql:schema.split(";"))if(!sql.trim().isEmpty())statement.execute(sql);
        }
    }
}
