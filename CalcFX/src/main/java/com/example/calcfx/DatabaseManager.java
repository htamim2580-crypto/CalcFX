package com.example.calcfx;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQLite storage for CalcFX. Every finished calculation and every
 * currency conversion is saved here so it survives app restarts.
 *
 * The database file lives at ~/.calcfx/history.db so it works the same way
 * whether you run the app from IntelliJ, Maven, or a packaged jar.
 */
public class DatabaseManager {

    private static final String DB_FILE_PATH = buildDbPath();
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE_PATH;

    private static DatabaseManager instance;

    public record HistoryEntry(int id, String type, String expression, String result, String timestamp) {}

    private DatabaseManager() {
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private static String buildDbPath() {
        File dir = new File(System.getProperty("user.home"), ".calcfx");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "history.db").getAbsolutePath().replace("\\", "/");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void initializeDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                expression TEXT NOT NULL,
                result TEXT NOT NULL,
                timestamp TEXT NOT NULL
            )
            """;
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    /** Saves a completed calculator computation, e.g. "12×(3+4)" -> "84". */
    public void saveCalculation(String expression, String result) {
        save("CALC", expression, result);
    }

    /** Saves a completed currency conversion, e.g. "100 USD → EUR" -> "92.34". */
    public void saveConversion(String expression, String result) {
        save("CONVERT", expression, result);
    }
    /** Saves a completed matrix operation, e.g. "det([1 2; 3 4])" -> "-2". */
    public void saveMatrix(String expression, String result) {
        save("MATRIX", expression, result);
    }
    /** Saves a completed complex-number operation. */
    public void saveComplex(String expression, String result) {
        save("COMPLEX", expression, result);
    }

    private void save(String type, String expression, String result) {
        String sql = "INSERT INTO history (type, expression, result, timestamp) VALUES (?, ?, ?, ?)";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"));
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, expression);
            ps.setString(3, result);
            ps.setString(4, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save history entry: " + e.getMessage());
        }
    }
    /** Updates an existing entry in place — the CRUD "U". */
    public void updateEntry(int id, String expression, String result) {
        String sql = "UPDATE history SET expression = ?, result = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, expression);
            ps.setString(2, result);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update history entry: " + e.getMessage());
        }
    }

    /** Returns the most recent entries, newest first. */
    public List<HistoryEntry> getRecentHistory(int limit) {
        List<HistoryEntry> list = new ArrayList<>();
        String sql = "SELECT id, type, expression, result, timestamp FROM history ORDER BY id DESC LIMIT ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HistoryEntry(
                            rs.getInt("id"),
                            rs.getString("type"),
                            rs.getString("expression"),
                            rs.getString("result"),
                            rs.getString("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load history: " + e.getMessage());
        }
        return list;
    }

    public void clearHistory() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM history");
            stmt.execute("DELETE FROM sqlite_sequence WHERE name='history'");
        } catch (SQLException e) {
            System.err.println("Failed to clear history: " + e.getMessage());
        }
    }
}