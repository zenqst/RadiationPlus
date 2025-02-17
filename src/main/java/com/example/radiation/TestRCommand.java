package com.example.radiation;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TestRCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Да!");
        return true;
    }
}
