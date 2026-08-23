package me.itzerpandx.PoisonPlots.database;

import me.itzerpandx.PoisonPlots.PlotsPlugin;
import me.itzerpandx.PoisonPlots.structures.Plot;
import me.itzerpandx.PoisonPlots.structures.Point;
import me.itzerpandx.PoisonPlots.structures.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SqliteDatabase {
    private final File databaseFile;
    private final Plugin plugin;
    private final ExecutorService databaseExecutor;
    private Connection connection;
    private volatile boolean shuttingDown;

    public SqliteDatabase(File dataFolder, String filename, Plugin plugin) {
        this.databaseFile = new File(dataFolder, filename);
        this.plugin = plugin;
        this.databaseExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "PoisonPlots-Database");
            thread.setDaemon(true);
            return thread;
        });
        this.initialize();
    }

    private void initialize() {
        try {
            File parent = databaseFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }

            createTables();
            plugin.getLogger().info("Connected to SQLite database: " + databaseFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database!", e);
        }
    }

    private void createTables() throws SQLException {
        String plotTable = "CREATE TABLE IF NOT EXISTS plots (" +
                "id TEXT PRIMARY KEY," +
                "owner TEXT," +
                "expires LONG," +
                "max_members INTEGER," +
                "rent INTEGER," +
                "sign_world TEXT," +
                "sign_x DOUBLE," +
                "sign_y DOUBLE," +
                "sign_z DOUBLE," +
                "region_min_x DOUBLE," +
                "region_min_y DOUBLE," +
                "region_min_z DOUBLE," +
                "region_max_x DOUBLE," +
                "region_max_y DOUBLE," +
                "region_max_z DOUBLE," +
                "region_world TEXT," +
                "members TEXT" +
                ")";

        try (Statement statement = connection.createStatement()) {
            statement.execute(plotTable);
        }
    }

    public void savePlotAsync(String id, Plot plot) {
        if (shuttingDown || plot == null) {
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                savePlot(id, plot);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save plot " + id + " to database!", e);
            }
        });
    }

    public void deletePlotAsync(String id) {
        if (shuttingDown) {
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                deletePlot(id);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete plot " + id + " from database!", e);
            }
        });
    }

    public void saveAllPlotsAndShutdown(Map<String, Plot> plots) {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        for (Map.Entry<String, Plot> entry : plots.entrySet()) {
            try {
                savePlot(entry.getKey(), entry.getValue());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save plot " + entry.getKey() + " on shutdown!", e);
            }
        }

        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        closeConnection();
    }

    public void savePlot(String id, Plot plot) throws SQLException {
        String query = "INSERT OR REPLACE INTO plots VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, id);
            statement.setString(2, plot.getOwner() == null ? null : plot.getOwner().toString());
            statement.setLong(3, plot.getExpires());
            statement.setInt(4, plot.getMaxMembers());
            statement.setInt(5, plot.getRent());

            Location sign = plot.getSignLocation();
            if (sign != null && sign.getWorld() != null) {
                statement.setString(6, sign.getWorld().getName());
                statement.setDouble(7, sign.getX());
                statement.setDouble(8, sign.getY());
                statement.setDouble(9, sign.getZ());
            } else {
                statement.setNull(6, Types.VARCHAR);
                statement.setDouble(7, 0);
                statement.setDouble(8, 0);
                statement.setDouble(9, 0);
            }

            Region region = plot.getRegion();
            if (region == null || region.getWorld() == null) {
                throw new SQLException("Plot " + id + " has no valid region");
            }

            Point min = region.getMin();
            Point max = region.getMax();
            statement.setDouble(10, min.x());
            statement.setDouble(11, min.y());
            statement.setDouble(12, min.z());
            statement.setDouble(13, max.x());
            statement.setDouble(14, max.y());
            statement.setDouble(15, max.z());
            statement.setString(16, region.getWorld().getName());
            statement.setString(17, String.join(",", plot.getMembers().stream().map(UUID::toString).toArray(String[]::new)));
            statement.executeUpdate();
        }
    }

    public Plot loadPlot(String id) throws SQLException {
        String query = "SELECT * FROM plots WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                World regionWorld = PlotsPlugin.INSTANCE.getServer().getWorld(result.getString("region_world"));
                if (regionWorld == null) {
                    plugin.getLogger().warning("Skipping plot " + id + ": region world is not loaded");
                    return null;
                }

                Region region = new Region(
                        regionWorld,
                        new Point(result.getInt("region_min_x"), result.getInt("region_min_y"), result.getInt("region_min_z")),
                        new Point(result.getInt("region_max_x"), result.getInt("region_max_y"), result.getInt("region_max_z"))
                );

                Plot plot = new Plot(region);
                plot.setPlotId(id);
                plot.setExpires(result.getLong("expires"));
                plot.setMaxMembers(result.getInt("max_members"));
                plot.setRent(result.getInt("rent"));

                String owner = result.getString("owner");
                if (owner != null && !owner.isBlank()) {
                    plot.setOwner(UUID.fromString(owner));
                }

                String signWorldName = result.getString("sign_world");
                if (signWorldName != null) {
                    World signWorld = PlotsPlugin.INSTANCE.getServer().getWorld(signWorldName);
                    if (signWorld != null) {
                        plot.setSignLocation(new Location(
                                signWorld,
                                result.getDouble("sign_x"),
                                result.getDouble("sign_y"),
                                result.getDouble("sign_z")
                        ));
                    }
                }

                String members = result.getString("members");
                if (members != null && !members.isBlank()) {
                    for (String member : members.split(",")) {
                        if (!member.isBlank()) {
                            plot.addMember(UUID.fromString(member));
                        }
                    }
                }

                return plot;
            }
        }
    }

    public Map<String, Plot> loadAllPlots() throws SQLException {
        Map<String, Plot> plots = new HashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id FROM plots")) {
            List<String> ids = new ArrayList<>();
            while (result.next()) {
                ids.add(result.getString("id"));
            }

            for (String id : ids) {
                Plot plot = loadPlot(id);
                if (plot != null) {
                    plots.put(id, plot);
                }
            }
        }
        return plots;
    }

    public void deletePlot(String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM plots WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }


    private void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close SQLite database connection!", e);
        }
    }
}