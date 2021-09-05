package dev.buchstabet.chatfilter;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class Chatfilter extends JavaPlugin implements Listener {

  private String message, command;
  private List<String> blockedWords;
  private Sound sound;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    this.getServer().getPluginManager().registerEvents(this, this);

    if (this.getConfig().isSet("command")) {
      this.command = this.getConfig().getString("command");
    }

    this.message = this.getConfig().getString("message").replace("&", "§");
    this.blockedWords = this.getConfig().getStringList("blockedWords");
    sound = Sound.valueOf(this.getConfig().getString("sound"));
  }

  @Override
  public void onDisable() {}

  @EventHandler
  public void onChat(AsyncPlayerChatEvent e) {
    String[] message = e.getMessage().split(" ");
    for (String s : message) {
      if (checkWord(s.toLowerCase())) {
        e.setCancelled(true);
        e.getPlayer().sendMessage(this.message);
        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            command.replace("{0}", e.getPlayer().getName()).replace("{1}", s).replace("&", "§"));
        e.getPlayer().playSound(e.getPlayer().getLocation(), sound, 1, 1);
        break;
      }
    }
  }

  private boolean checkWord(String word) {
    if (blockedWords.contains(word)) return true;
    String replacedWord =
        word.replace("1", "i")
            .replace("4", "a")
            .replace("3", "e")
            .replace("8", "b")
            .replace("5", "s")
            .replace("0", "o")
            .replace("ß", "b");

    if (blockedWords.contains(replacedWord)) return true;
    return checkSimilarity(replacedWord);
  }

  private boolean checkSimilarity(String replacedWord) {
    for (String blockedWord : blockedWords) {
      int similarCount = 0;
      for (int i = 0; i < blockedWord.length(); i++) {
        if (i >= replacedWord.length()) break;

        if (replacedWord.split("")[i].equalsIgnoreCase(blockedWord.split("")[i])) {
          similarCount++;
        }
      }

      if ((double) similarCount / blockedWord.length() > 0.7) {
        return true;
      }
    }

    return false;
  }
}
