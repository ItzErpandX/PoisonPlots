package me.itzerpandx.PoisonPlots;

import me.itzerpandx.PoisonPlots.config.PluginConfig;
import me.itzerpandx.PoisonPlots.database.SqliteDatabase;
import me.itzerpandx.PoisonPlots.structures.Plot;
import me.itzerpandx.PoisonPlots.structures.Point;
import me.itzerpandx.PoisonPlots.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlotsPlugin extends JavaPlugin implements Listener {
    public static PlotsPlugin INSTANCE;

    private PluginConfig pluginConfig;
    private SqliteDatabase database;
    private YamlConfiguration currencyConfig;
    private File currencyFile;
    private BukkitTask saveTask;

    private final Map<String, Plot> plots = new HashMap<>();
    private final Map<UUID, Boolean> bypassMap = new HashMap<>();

    @Override
    public void onLoad() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        database = new SqliteDatabase(getDataFolder(), "plots.db", this);

        currencyFile = new File(getDataFolder(), "currency.yml");
        loadCurrencyConfig();
        loadPlots();

        PlotManagerCommand plotCmd = new PlotManagerCommand();
        if (getCommand("plotmanager") != null) {
            getCommand("plotmanager").setExecutor(plotCmd);
            getCommand("plotmanager").setTabCompleter(plotCmd);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // This task only collects/submits saves. SQLite work is serialized by SqliteDatabase.
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                savePlots();
            }
        }.runTaskTimer(this,
                pluginConfig.getAutoSaveInterval(),
                pluginConfig.getAutoSaveInterval());

        getLogger().info("PoisonPlots v1.1 has been enabled!");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        // Stop plot listeners/timers before final database shutdown.
        for (Plot plot : new ArrayList<>(plots.values())) {
            if (plot != null) {
                plot.destroy();
            }
        }

        if (database != null) {
            database.saveAllPlotsAndShutdown(new HashMap<>(plots));
            database = null;
        }

        saveCurrencyConfig();
        bypassMap.clear();
        plots.clear();
        INSTANCE = null;
    }

    private void loadCurrencyConfig() {
        if (!currencyFile.exists()) {
            try {
                File parent = currencyFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                currencyFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create currency.yml!", e);
            }
        }
        currencyConfig = YamlConfiguration.loadConfiguration(currencyFile);
    }

    private void saveCurrencyConfig() {
        if (currencyConfig == null || currencyFile == null) {
            return;
        }
        try {
            currencyConfig.save(currencyFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save currency.yml!", e);
        }
    }

    private void loadPlots() {
        plots.clear();
        try {
            plots.putAll(database.loadAllPlots());
            getLogger().info("Loaded " + plots.size() + " plots from database");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to load plots from database!", e);
        }
    }

    /** Must run on the server thread. Database writes are queued internally. */
    public void savePlots() {
        for (Map.Entry<String, Plot> entry : new HashMap<>(plots).entrySet()) {
            if (entry.getValue() != null) {
                database.savePlotAsync(entry.getKey(), entry.getValue());
            }
        }
    }

    public Plot getPlot(String id) {
        return plots.get(id);
    }

    public Map<String, Plot> getPlots() {
        return new HashMap<>(plots);
    }

    public void registerPlot(String id, Plot plot) {
        if (plot == null) {
            return;
        }
        plot.setPlotId(id);
        plots.put(id, plot);
        database.savePlotAsync(id, plot);
    }

    public void deletePlot(String id) {
        Plot previous = plots.remove(id);
        if (previous != null) {
            previous.destroy();
        }
        database.deletePlotAsync(id);
    }

    public String getPlotAt(Point point) {
        for (Map.Entry<String, Plot> entry : plots.entrySet()) {
            Plot plot = entry.getValue();
            if (plot != null && plot.getRegion().contains(point)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean plotExists(String id) {
        return plots.containsKey(id);
    }

    public int getPlayerOwnedPlotsCount(UUID playerUUID) {
        int count = 0;
        for (Plot plot : plots.values()) {
            if (plot != null && playerUUID.equals(plot.getOwner()) && plot.getRemaining() > 0L) {
                count++;
            }
        }
        return count;
    }

    public List<String> getPlayerOwnedPlots(UUID playerUUID) {
        List<String> ownedPlotIds = new ArrayList<>();
        for (Map.Entry<String, Plot> entry : plots.entrySet()) {
            Plot plot = entry.getValue();
            if (plot != null && playerUUID.equals(plot.getOwner()) && plot.getRemaining() > 0L) {
                ownedPlotIds.add(entry.getKey());
            }
        }
        return ownedPlotIds;
    }

    public int getMaxPlotsForPlayer(Player player) {
        if (!pluginConfig.isPlotOwnershipLimitEnabled()) {
            return Integer.MAX_VALUE;
        }
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("poisonplots.own_plots." + i)) {
                return i;
            }
        }
        return 1;
    }

    public ItemStack getCurrency() {
        if (!currencyConfig.contains("currency")) {
            ItemBuilder builder = new ItemBuilder(Material.SUNFLOWER);

            builder.setName("<yellow>Gold Coin</yellow>");
            builder.addEnchantment(Enchantment.UNBREAKING, 10);
            builder.addFlags(ItemFlag.HIDE_ENCHANTS);

            setCurrency(builder.create());
        }

        ItemStack currency = currencyConfig.getItemStack("currency");

        if (currency == null) {
            return new ItemStack(Material.SUNFLOWER);
        }

        return currency.clone();
    }

    public void setCurrency(ItemStack itemStack) {
        ItemStack cloned = itemStack.clone();
        cloned.setAmount(1);
        currencyConfig.set("currency", cloned);
        saveCurrencyConfig();
    }

    public boolean isBypassing(Player player) {
        if (!player.hasPermission(getName() + ".plotsbypass")) {
            return false;
        }
        return bypassMap.getOrDefault(player.getUniqueId(), false);
    }

    public void setBypassing(Player player, boolean value) {
        bypassMap.put(player.getUniqueId(), value);
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public SqliteDatabase getDatabase() {
        return database;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bypassMap.remove(event.getPlayer().getUniqueId());
    }
}