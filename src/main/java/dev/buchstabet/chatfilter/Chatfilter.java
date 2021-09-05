package dev.buchstabet.chatfilter;

import dev.buchstabet.chatfilter.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chatfilter extends JavaPlugin implements Listener, TabCompleter {

  private String message, command;
  private List<String> blockedWords, domains;
  private Sound sound;
  private final StringSimilarity stringSimilarity = new StringSimilarity();
  private double similarityIndex;
  private DatabaseManager databaseManager;
  private boolean alreadySend, advertising;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    this.getServer().getPluginManager().registerEvents(this, this);

    if (this.getConfig().isSet("command")) {
      this.command = this.getConfig().getString("command");
    }
    this.similarityIndex = this.getConfig().getDouble("similarity");
    this.message = this.getConfig().getString("message").replace("&", "§");
    this.sound = Sound.valueOf(this.getConfig().getString("sound"));
    this.alreadySend = this.getConfig().getBoolean("alreadySend");
    this.advertising = this.getConfig().getBoolean("advertising");
    this.domains = this.getConfig().getStringList("domains");

    ConfigurationSection section = this.getConfig().getConfigurationSection("mysql");
    if (section.getBoolean("enabled")) {
      blockedWords = new ArrayList<>();
      databaseManager =
          new DatabaseManager(
              2,
              section.getString("hostname"),
              section.getInt("port"),
              section.getString("database"),
              section.getString("username"),
              section.getString("password"));

      Connection connection = databaseManager.getConnection();
      try {
        connection
            .prepareStatement(
                "CREATE TABLE IF NOT EXISTS `chatfilter_blockedwords` (`word` VARCHAR(100) NOT NULL,PRIMARY KEY (`word`));")
            .executeUpdate();

        PreparedStatement statement =
            connection.prepareStatement("SELECT * FROM `chatfilter_blockedwords`");
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          blockedWords.add(resultSet.getString("word"));
        }
      } catch (SQLException e) {
        e.printStackTrace();
      } finally {
        databaseManager.closeConnect(connection);
      }
    } else {
      blockedWords = this.getConfig().getStringList("blockedWords");
    }

    this.getCommand("chatfilter").setExecutor(this);
    this.getCommand("chatfilter").setTabCompleter(this);
  }

  @Override
  public void onDisable() {}

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent e) {
    if (e.isCancelled()) return;
    String[] message = e.getMessage().split(" ");
    for (String s : message) {
      if (checkWord(s.toLowerCase(), e.getMessage())) {
        e.setCancelled(!alreadySend);
        e.getPlayer().sendMessage(this.message);
        if (command != null)
          Bukkit.dispatchCommand(
              Bukkit.getConsoleSender(),
              command.replace("{0}", e.getPlayer().getName()).replace("{1}", s).replace("&", "§"));
        e.getPlayer().playSound(e.getPlayer().getLocation(), sound, 1, 1);
        break;
      }
    }
  }

  private boolean checkWord(String word, String wholeMessage) {
    if (blockedWords.contains(word)) return true;
    if (advertising && checkAdvertising(wholeMessage)) return true;
    String replacedWord =
        word.replace("1", "i")
            .replace("4", "a")
            .replace("3", "e")
            .replace("8", "b")
            .replace("5", "s")
            .replace("0", "o")
            .replace("ß", "b");

    if (blockedWords.contains(replacedWord)) return true;
    return checkSimilarity(replacedWord, wholeMessage);
  }

  private boolean checkAdvertising(String wholeMessage) {
    wholeMessage = wholeMessage.toLowerCase();
    for (String domain : domains) {
      if (wholeMessage.contains(domain)
          || wholeMessage.replace(" ", "").contains(domain)
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "(.)"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "(,)"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", ","))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", ":"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", ";"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "-"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "#"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "~"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "+"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "="))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", ">"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "<"))
          || wholeMessage.replace(" ", "").contains(domain.replace(".", "*"))) {

        return true;
      }
    }
    return false;
  }

  private boolean checkSimilarity(String replacedWord, String wholeMessage) {
    for (String blockedWord : blockedWords) {
      if (stringSimilarity.similarity(replacedWord, blockedWord) > similarityIndex
          || stringSimilarity.similarity(wholeMessage, blockedWord) > similarityIndex) {
        return true;
      }
    }

    return false;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    return Arrays.asList("add", "remove", "list");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (databaseManager == null) {
      sender.sendMessage(
          "§cThis command is activated only when the database connection is enabled.");
      return true;
    }
    if (args.length < 1) {
      sender.sendMessage("§cPlease use: /" + label + " <add/remove/list> [word]");
    } else {
      Connection connection = databaseManager.getConnection();
      try {
        switch (args[0]) {
          case "add":
            {
              if (args.length != 2) {
                break;
              }

              args[1] = args[1].toLowerCase();
              PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO `chatfilter_blockedwords` (`word`) VALUES (?)");
              statement.setString(1, args[1]);
              statement.executeUpdate();
              blockedWords.add(args[1]);
              sender.sendMessage(
                  "§aThe word §e"
                      + args[1]
                      + "§a is now blocked by the chat filter. Please note, all servers must restart.");

              break;
            }

          case "remove":
            {
              if (args.length != 2) {
                break;
              }

              args[1] = args[1].toLowerCase();
              PreparedStatement statement =
                  connection.prepareStatement(
                      "DELETE FROM `chatfilter_blockedwords` WHERE `word` = ?");
              statement.setString(1, args[1]);
              statement.executeUpdate();
              blockedWords.remove(args[1]);
              sender.sendMessage(
                  "§aThe word §e"
                      + args[1]
                      + " §ais no longer blocked by the chat filter. Please note, all servers must restart.");

              break;
            }

          case "list":
            {
              sender.sendMessage(
                  "§6List of words (" + blockedWords.size() + ") that are in the chat filters:");
              blockedWords.forEach(s -> sender.sendMessage("§7- §e" + s));
              break;
            }
        }
      } catch (SQLException e) {
        sender.sendMessage("§cAn error occurred: " + e.getMessage());
      } finally {
        databaseManager.closeConnect(connection);
      }
    }
    return true;
  }
}
