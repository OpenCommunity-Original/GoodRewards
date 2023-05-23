package org.opencommunity.goodrewards;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main extends JavaPlugin implements Listener {

    private Map<String, List<String>> playerRewards;
    private Map<String, Integer> playerVoteCounts;

    private BufferedWriter logWriter;

    @Override
    public void onEnable() {
        // Load configuration and register events
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialize player rewards and vote count maps
        playerRewards = new HashMap<>();
        playerVoteCounts = new HashMap<>();

        // Create log file writer
        try {
            File logFile = new File(getDataFolder(), "log.txt");
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            getLogger().severe("Failed to create log file writer: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Close log file writer
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                getLogger().severe("Failed to close log file writer: " + e.getMessage());
            }
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadrewards")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Rewards configuration reloaded.");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String playerName = vote.getUsername();

        // Check the player's vote count
        int voteCount = playerVoteCounts.getOrDefault(playerName, 0);
        int maxVotes = getConfig().getInt("max_votes", 10);
        if (voteCount >= maxVotes) {
            // Player has reached the maximum vote count, stop giving rewards
            getLogger().warning("Player '" + playerName + "' has reached the maximum vote count. Rewards will not be given.");
            logReachedMaxVoteCount(playerName);
            return;
        }

        // Increment the player's vote count
        playerVoteCounts.put(playerName, voteCount + 1);

        // Store vote rewards for the player
        List<String> rewards = getConfig().getStringList("rewards");
        if (playerRewards.containsKey(playerName)) {
            // Player already has pending rewards, append new rewards to the existing list
            playerRewards.get(playerName).addAll(rewards);
        } else {
            // Player doesn't have any pending rewards, create a new list
            playerRewards.put(playerName, new ArrayList<>(rewards));
        }

        // Give rewards immediately if player is online
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            givePlayerRewards(player);
            playerRewards.remove(playerName); // Remove rewards from memory
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Give rewards if player has any pending
        if (playerRewards.containsKey(playerName)) {
            givePlayerRewards(player);
            playerRewards.remove(playerName); // Remove rewards from memory
        }
    }

    private void givePlayerRewards(Player player) {
        List<String> rewards = playerRewards.get(player.getName());

        // Give rewards to the player
        for (String reward : rewards) {
            String[] rewardParts = reward.split(" ", 2);
            if (rewardParts.length == 2) {
                String rewardType = rewardParts[0];
                String rewardValue = rewardParts[1];

                if (rewardType.equalsIgnoreCase("command")) {
                    String commandToExecute = rewardValue.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                } else if (rewardType.equalsIgnoreCase("item")) {
                    String[] itemParts = rewardValue.split(" ", 2);
                    if (itemParts.length == 2) {
                        String itemName = itemParts[0];
                        int quantity = Integer.parseInt(itemParts[1]);

                        Material itemMaterial = Material.matchMaterial(itemName);
                        if (itemMaterial != null) {
                            ItemStack itemStack = new ItemStack(itemMaterial, quantity);
                            player.getInventory().addItem(itemStack);
                        }
                    }
                }
            }
        }
        // Send thank message to the player
        String thankMessage = getConfig().getString("thank_message");
        if (thankMessage != null && !thankMessage.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', thankMessage));
        }
    }

    private void logReachedMaxVoteCount(String playerName) {
        if (logWriter != null) {
            LocalDateTime timestamp = LocalDateTime.now();
            String logEntry = "[" + timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "] Player '" + playerName + "' reached the maximum vote count.";
            try {
                logWriter.write(logEntry);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                getLogger().severe("Failed to write to log file: " + e.getMessage());
            }
        }
    }
}

