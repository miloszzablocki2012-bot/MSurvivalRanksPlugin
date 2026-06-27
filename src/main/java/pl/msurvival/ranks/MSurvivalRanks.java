package pl.msurvival.ranks;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class MSurvivalRanks extends JavaPlugin implements Listener {

    private File playersFile;
    private FileConfiguration players;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayers();
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();
        startActionBar();
        getLogger().info("MSurvivalRanks wlaczony!");
    }

    private void registerCommands() {
        if (getCommand("setrank") != null) {
            getCommand("setrank").setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("msurvivalranks.admin")) {
                    sender.sendMessage(color(getConfig().getString("messages.no-permission", "&cNie masz uprawnień.")));
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage(color(getConfig().getString("messages.usage-setrank", "&cUżycie: &e/setrank <gracz> <ranga>")));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(color(getConfig().getString("messages.player-not-found", "&cNie znaleziono gracza online.")));
                    return true;
                }

                String rank = normalize(args[1]);

                if (!rankExists(rank)) {
                    sender.sendMessage(color(getConfig().getString("messages.rank-not-found", "&cNie ma takiej rangi.")));
                    sender.sendMessage(color(getConfig().getString("messages.rank-list", "&7Dostępne rangi: &e%ranks%")
                            .replace("%ranks%", getRankNamesText())));
                    return true;
                }

                setRank(target.getName(), rank);
                updatePlayerVisuals(target);
                sendActionBar(target);

                String msg = getConfig().getString("messages.rank-set", "&aUstawiono rangę %display% &adla gracza &e%player%&a.");
                sender.sendMessage(color(applyPlaceholders(msg, target, rank)));

                String received = getConfig().getString("messages.rank-received", "&aOtrzymałeś rangę: %display%");
                target.sendMessage(color(applyPlaceholders(received, target, rank)));

                return true;
            });
        }

        if (getCommand("rank") != null) {
            getCommand("rank").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Ta komenda jest tylko dla gracza.");
                    return true;
                }

                Player player = (Player) sender;
                String rank = getRank(player.getName());

                String msg = getConfig().getString("messages.your-rank", "&8┃ &7Gracz: &f%player% &8┃ &7Ranga: %display% &8┃");
                player.sendMessage(color(applyPlaceholders(msg, player, rank)));
                sendActionBar(player);

                return true;
            });
        }

        if (getCommand("ranksreload") != null) {
            getCommand("ranksreload").setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("msurvivalranks.admin")) {
                    sender.sendMessage(color(getConfig().getString("messages.no-permission", "&cNie masz uprawnień.")));
                    return true;
                }

                reloadConfig();
                loadPlayers();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!players.contains("players." + normalize(player.getName()))) {
                        setRank(player.getName(), getDefaultRank());
                    }
                    updatePlayerVisuals(player);
                    sendActionBar(player);
                }

                sender.sendMessage(color(getConfig().getString("messages.reload", "&aPrzeładowano konfigurację rang.")));
                return true;
            });
        }
    }

    private void startActionBar() {
        long refreshTicks = getConfig().getLong("actionbar.refresh-ticks", 20L);

        if (refreshTicks < 5L) {
            refreshTicks = 20L;
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("actionbar.enabled", true)) {
                return;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                sendActionBar(player);
            }
        }, 20L, refreshTicks);
    }

    private void sendActionBar(Player player) {
        if (!getConfig().getBoolean("actionbar.enabled", true)) {
            return;
        }

        String rank = getRank(player.getName());
        String format = getConfig().getString(
                "actionbar.format",
                "&8┃ &6✦ &7Gracz: &f%player% &8┃ &6✦ &7Ranga: %display% &8┃"
        );

        String message = applyPlaceholders(format, player, rank);
        player.sendActionBar(Component.text(color(message)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!players.contains("players." + normalize(player.getName()))) {
            setRank(player.getName(), getDefaultRank());
        }

        updatePlayerVisuals(player);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                sendActionBar(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String rank = getRank(player.getName());

        String format = getConfig().getString("chat.format", "%prefix% &7%player% &8» &f%message%");
        String message = applyPlaceholders(format, player, rank).replace("%message%", event.getMessage());

        event.setFormat(color(message).replace("%", "%%"));
    }

    private void updatePlayerVisuals(Player player) {
        String rank = getRank(player.getName());

        if (getConfig().getBoolean("tablist.enabled", true)) {
            String tabFormat = getConfig().getString("tablist.format", "%prefix% &f%player%");
            player.setPlayerListName(color(applyPlaceholders(tabFormat, player, rank)));
        }

        player.setDisplayName(color(getPrefix(rank) + " &f" + player.getName()));
    }

    private String applyPlaceholders(String text, Player player, String rank) {
        if (text == null) {
            return "";
        }

        return text
                .replace("%prefix%", getPrefix(rank))
                .replace("%display%", getDisplay(rank))
                .replace("%rank%", rank)
                .replace("%player%", player.getName());
    }

    private void loadPlayers() {
        playersFile = new File(getDataFolder(), "players.yml");

        if (!playersFile.exists()) {
            try {
                getDataFolder().mkdirs();
                playersFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        players = YamlConfiguration.loadConfiguration(playersFile);
    }

    private void savePlayers() {
        try {
            players.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setRank(String playerName, String rank) {
        players.set("players." + normalize(playerName), normalize(rank));
        savePlayers();
    }

    private String getRank(String playerName) {
        String path = "players." + normalize(playerName);
        String rank = normalize(players.getString(path, getDefaultRank()));

        if (!rankExists(rank)) {
            return getDefaultRank();
        }

        return rank;
    }

    private String getDefaultRank() {
        String rank = normalize(getConfig().getString("default-rank", "gracz"));

        if (!rankExists(rank)) {
            return "gracz";
        }

        return rank;
    }

    private boolean rankExists(String rank) {
        return getConfig().contains("ranks." + normalize(rank));
    }

    private String getPrefix(String rank) {
        return getConfig().getString("ranks." + normalize(rank) + ".prefix", "&8[&7GRACZ&8]");
    }

    private String getDisplay(String rank) {
        return getConfig().getString("ranks." + normalize(rank) + ".display", "&7&lGRACZ");
    }

    private Set<String> getRankNames() {
        ConfigurationSection section = getConfig().getConfigurationSection("ranks");

        if (section == null) {
            return Set.of("gracz");
        }

        return section.getKeys(false);
    }

    private String getRankNamesText() {
        return getRankNames().stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT);
    }

    private String color(String text) {
        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
