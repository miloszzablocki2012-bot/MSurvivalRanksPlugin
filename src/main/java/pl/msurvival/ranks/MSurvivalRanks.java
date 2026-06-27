package pl.msurvival.ranks;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class MSurvivalRanks extends JavaPlugin implements Listener {

    private File playersFile;
    private FileConfiguration players;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayers();

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();
        startActionBarTask();

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
                    sender.sendMessage(color(getConfig().getString("messages.player-not-found", "&cNie znaleziono gracza.")));
                    return true;
                }

                String rank = normalize(args[1]);
                if (!rankExists(rank)) {
                    sender.sendMessage(color(getConfig().getString("messages.rank-not-found", "&cNie ma takiej rangi.")));
                    sender.sendMessage(color("&7Dostępne rangi: &e" + String.join(", ", getRankNames())));
                    return true;
                }

                setRank(target.getName(), rank);
                updatePlayerName(target);

                String msg = getConfig().getString("messages.rank-set", "&aUstawiono rangę &e%rank% &adla gracza &e%player%&a.");
                msg = msg.replace("%rank%", getRankDisplay(rank)).replace("%player%", target.getName());

                sender.sendMessage(color(msg));
                target.sendMessage(color("&aTwoja nowa ranga: " + getPrefix(rank)));
                return true;
            });
        }

        if (getCommand("rank") != null) {
            getCommand("rank").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player)) {
                    return true;
                }

                Player player = (Player) sender;
                String rank = getRank(player.getName());

                String msg = getConfig().getString("messages.your-rank", "&7Twoja ranga: %prefix%");
                msg = msg.replace("%prefix%", getPrefix(rank)).replace("%rank%", getRankDisplay(rank));

                player.sendMessage(color(msg));
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
                    updatePlayerName(player);
                }

                sender.sendMessage(color(getConfig().getString("messages.reload", "&aPrzeładowano konfigurację rang.")));
                return true;
            });
        }
    }

    private void startActionBarTask() {
        long refreshTicks = getConfig().getLong("actionbar.refresh-ticks", 40L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("actionbar.enabled", true)) {
                return;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                String rank = getRank(player.getName());
                String display = getRankDisplay(rank);
                String prefix = getPrefix(rank);

                String format = getConfig().getString(
                        "actionbar.format",
                        "&8┃ &7Twoja ranga: %display% &8┃"
                );

                String message = format
                        .replace("%display%", display)
                        .replace("%prefix%", prefix)
                        .replace("%rank%", rank)
                        .replace("%player%", player.getName());

                player.sendActionBar(Component.text(color(message)));
            }
        }, 20L, refreshTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!players.contains("players." + normalize(player.getName()))) {
            setRank(player.getName(), getDefaultRank());
        }

        updatePlayerName(player);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = getRank(player.getName());

        String format = getConfig().getString("chat-format", "%prefix% &7%player% &8» &f%message%");
        format = format.replace("%prefix%", getPrefix(rank))
                .replace("%player%", player.getName())
                .replace("%rank%", getRankDisplay(rank))
                .replace("%message%", event.getMessage());

        event.setFormat(color(format).replace("%", "%%"));
    }

    private void updatePlayerName(Player player) {
        String rank = getRank(player.getName());
        String name = color(getPrefix(rank) + " &f" + player.getName());

        player.setPlayerListName(name);
        player.setDisplayName(name);
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
        String rank = players.getString("players." + normalize(playerName), getDefaultRank());

        if (!rankExists(rank)) {
            return getDefaultRank();
        }

        return normalize(rank);
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

    private String getRankDisplay(String rank) {
        return getConfig().getString("ranks." + normalize(rank) + ".display", "&7GRACZ");
    }

    private Set<String> getRankNames() {
        ConfigurationSection section = getConfig().getConfigurationSection("ranks");

        if (section == null) {
            return Set.of("gracz");
        }

        return section.getKeys(false);
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
}// Wklej tutaj MSurvivalRanks.java
