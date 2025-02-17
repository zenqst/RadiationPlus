package com.example.radiation;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RadiationTabCompleter implements TabCompleter {

    private final RadiationPlugin plugin;

    public RadiationTabCompleter(RadiationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("toggle");
            completions.add("delete");
            completions.add("list");
            completions.add("status");
        } else {
            switch (args[0].toLowerCase()) {
                case "create":
                    handleCreateCompletions(sender, args, completions);
                    break;
                case "delete":
                    handleDeleteCompletions(completions);
                    break;
            }
        }
        return filterCompletions(completions, args[args.length - 1]);
    }

    private void handleCreateCompletions(CommandSender sender, String[] args, List<String> completions) {
        if (args.length == 2) {
            completions.add("<название_зоны>");
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            Location loc = player.getLocation();
            switch (args.length) {
                case 3:
                    completions.add(String.valueOf(loc.getBlockX()));
                    break;
                case 4:
                    completions.add(String.valueOf(loc.getBlockY()));
                    break;
                case 5:
                    completions.add(String.valueOf(loc.getBlockZ()));
                    break;
                case 6:
                    completions.add(String.valueOf(loc.getBlockX() + 5));
                    break;
                case 7:
                    completions.add(String.valueOf(loc.getBlockY() + 5));
                    break;
                case 8:
                    completions.add(String.valueOf(loc.getBlockZ() + 5));
                    break;
            }
        }
    }

    private void handleDeleteCompletions(List<String> completions) {
        completions.addAll(plugin.getZones().keySet());
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(completion);
            }
        }
        return filtered;
    }
}