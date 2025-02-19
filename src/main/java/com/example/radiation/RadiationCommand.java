package com.example.radiation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class RadiationCommand implements CommandExecutor {

    public double level2Threshold;
    public double level3Threshold;
    public double level4Threshold;
    public double level5Threshold;

    private final RadiationPlugin plugin;

    public RadiationCommand(RadiationPlugin plugin) {
        this.plugin = plugin;
    }

    // Поля для локализации
    private File langFile;
    private FileConfiguration langConfig;

    /**
     * Загружает языковой файл из папки data/languages.
     * Использует значение параметра "lang" из config.yml (по умолчанию "en_US").
     */
    private void loadLanguage() {
        String lang = plugin.getConfig().getString("lang", "en_US");
        langFile = new File(plugin.getDataFolder(), "languages" + File.separator + lang + ".yml");
        if (!langFile.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("languages/" + lang + ".yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    /**
     * Возвращает сообщение из языкового файла по ключу с переводом цветовых кодов.
     */
    public String getLangMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, key));
    }

    /**
     * Возвращает сообщение с подстановкой плейсхолдеров.
     */
    public String getLangMessage(String key, Map<String, String> placeholders) {
        String msg = ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, key));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        loadConfigValues();
        loadLanguage();

        if (!sender.isOp()) {
            sender.sendMessage(getLangMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(getLangMessage("command_usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreate(sender, args);
            case "toggle":
                return handleToggle(sender);
            case "delete":
                return handleDelete(sender, args);
            case "list":
                return handleList(sender);
            case "status":
                return handleStatus(sender);
            default:
                sender.sendMessage(getLangMessage("unknown_command"));
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length != 8) {
            sender.sendMessage(getLangMessage("command_usage"));
            return true;
        }

        String zoneName = args[1];
        try {
            double x1 = Double.parseDouble(args[2]);
            double y1 = Double.parseDouble(args[3]);
            double z1 = Double.parseDouble(args[4]);
            double x2 = Double.parseDouble(args[5]);
            double y2 = Double.parseDouble(args[6]);
            double z2 = Double.parseDouble(args[7]);

            String worldName = sender instanceof Player ?
                    ((Player) sender).getWorld().getName() :
                    Bukkit.getWorlds().get(0).getName();

            RadiationZone zone = new RadiationZone(zoneName, worldName, x1, y1, z1, x2, y2, z2);
            plugin.getZones().put(zoneName, zone);
            plugin.saveZones();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("zone", zoneName);
            placeholders.put("world", worldName);
            sender.sendMessage(getLangMessage("zone_created", placeholders));
        } catch (NumberFormatException e) {
            sender.sendMessage(getLangMessage("number_format_error"));
        }
        return true;
    }

    private void loadConfigValues() {
        level2Threshold = plugin.getConfig().getDouble("level2Threshold", 6.0);
        level3Threshold = plugin.getConfig().getDouble("level3Threshold", 9.0);
        level4Threshold = plugin.getConfig().getDouble("level4Threshold", 15.0);
        level5Threshold = plugin.getConfig().getDouble("level5Threshold", 25.0);
    }

    private boolean handleToggle(CommandSender sender) {
        boolean current = plugin.isRadiationActive();
        plugin.setRadiationActive(!current);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("state", plugin.isRadiationActive() ? getLangMessage("radiation_enabled") : getLangMessage("radiation_disabled"));
        sender.sendMessage(getLangMessage("radiation_toggle", placeholders));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(getLangMessage("delete_usage"));
            return true;
        }

        String zoneName = args[1];
        if (plugin.getZones().remove(zoneName) != null) {
            plugin.saveZones();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("zone", zoneName);
            sender.sendMessage(getLangMessage("zone_deleted", placeholders));
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("zone", zoneName);
            sender.sendMessage(getLangMessage("zone_not_found", placeholders));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, RadiationZone> zones = plugin.getZones();
        if (zones.isEmpty()) {
            sender.sendMessage(getLangMessage("no_zones"));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(zones.size()));
        sender.sendMessage(getLangMessage("zone_list_header", placeholders));
        zones.forEach((name, zone) -> {
            Map<String, String> ph = new HashMap<>();
            ph.put("zone", name);
            ph.put("world", zone.getWorld());
            ph.put("x1", String.valueOf(Math.round(zone.getX1())));
            ph.put("x2", String.valueOf(Math.round(zone.getX2())));
            ph.put("y1", String.valueOf(Math.round(zone.getY1())));
            ph.put("y2", String.valueOf(Math.round(zone.getY2())));
            ph.put("z1", String.valueOf(Math.round(zone.getZ1())));
            ph.put("z2", String.valueOf(Math.round(zone.getZ2())));
            sender.sendMessage(getLangMessage("zone_list_item", ph));
        });
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        String status = plugin.isRadiationActive() 
            ? getLangMessage("activated") 
            : getLangMessage("disabled");

        sender.sendMessage(getLangMessage("radiation_status_header"));
        sender.sendMessage(getLangMessage("status") + status);
        sender.sendMessage(getLangMessage("levels"));
        sender.sendMessage(ChatColor.DARK_GREEN + "▸ " + String.format("%.1f", level2Threshold) + "+ " + getLangMessage("effect_level_2"));
        sender.sendMessage(ChatColor.GOLD + "▸ " + String.format("%.1f", level3Threshold) + "+ " + getLangMessage("effect_level_3"));
        sender.sendMessage(ChatColor.RED + "▸ " + String.format("%.1f", level4Threshold) + "+ " + getLangMessage("effect_level_4"));
        sender.sendMessage(ChatColor.DARK_RED + "▸ " + String.format("%.1f", level5Threshold) + "+ " + getLangMessage("effect_level_5"));
        return true;
    }
}
