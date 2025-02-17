package com.example.radiation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import java.util.Map;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        loadConfigValues();

        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Использование: /radzone <create|toggle|delete|list|status> [аргументы]");
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
                sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда.");
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length != 8) {
            sender.sendMessage(ChatColor.RED + "Использование: /radzone create <название> <x1> <y1> <z1> <x2> <y2> <z2>");
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

            sender.sendMessage(ChatColor.GREEN + "Зона '" + zoneName + "' создана в мире " + worldName + ".");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Координаты должны быть числами.");
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
        sender.sendMessage(ChatColor.GREEN + "Радиация " + (plugin.isRadiationActive() ? "активирована" : "деактивирована") + ".");
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /radzone delete <название>");
            return true;
        }

        String zoneName = args[1];
        if (plugin.getZones().remove(zoneName) != null) {
            plugin.saveZones();
            sender.sendMessage(ChatColor.GREEN + "Зона '" + zoneName + "' удалена.");
        } else {
            sender.sendMessage(ChatColor.RED + "Зона с названием '" + zoneName + "' не найдена.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, RadiationZone> zones = plugin.getZones();
        if (zones.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Нет созданных радиационных зон.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "─── " + ChatColor.BOLD + "Список зон (" + zones.size() + ")" + ChatColor.RESET + ChatColor.GOLD + " ───");
        zones.forEach((name, zone) -> {
            sender.sendMessage(ChatColor.GREEN + "▸ " + ChatColor.BOLD + name + ChatColor.RESET
                + ChatColor.GRAY + " [Мир: " + zone.getWorld() + "]"
                + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + "X: " + Math.round(zone.getX1()) + "..." + Math.round(zone.getX2())
                + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + "Y: " + Math.round(zone.getY1()) + "..." + Math.round(zone.getY2())
                + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + "Z: " + Math.round(zone.getZ1()) + "..." + Math.round(zone.getZ2()));
        });
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        String status = plugin.isRadiationActive() 
            ? ChatColor.GREEN + "✔ АКТИВИРОВАНА" 
            : ChatColor.RED + "✖ ДЕАКТИВИРОВАНА";

        sender.sendMessage(ChatColor.GOLD + "─── " + ChatColor.BOLD + "Статус радиации" + ChatColor.RESET + ChatColor.GOLD + " ───");
        sender.sendMessage(ChatColor.GRAY + "Состояние: " + status);
        sender.sendMessage(ChatColor.GOLD + "\nУровни воздействия:");
        sender.sendMessage(ChatColor.DARK_GREEN + "▸ " + String.format("%.1f", level2Threshold) + "+ " + ChatColor.GRAY + "- Замедление I");
        sender.sendMessage(ChatColor.GOLD + "▸ " + String.format("%.1f", level3Threshold) + "+ " + ChatColor.GRAY + "- Замедление II");
        sender.sendMessage(ChatColor.RED + "▸ " + String.format("%.1f", level4Threshold) + "+ " + ChatColor.GRAY + "- Урон + Замедление");
        sender.sendMessage(ChatColor.DARK_RED + "▸ " + String.format("%.1f", level5Threshold) + "+ " + ChatColor.GRAY + "- Двойной урон + Замедление II");
        return true;
    }
}