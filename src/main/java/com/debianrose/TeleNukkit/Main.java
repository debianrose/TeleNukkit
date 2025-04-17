package com.debianrose.TeleNukkit;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.json.JSONObject;
import okhttp3.*;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

public class Main extends PluginBase implements Listener {
    private BridgeManager bridgeManager;
    private String language;
    private Map<String, LanguagePack> languages;

    public LanguagePack getLanguagePack() {
        return languages.get(language);
    }

    public BridgeManager getBridgeManager() {
        return bridgeManager;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        initLanguages();
        language = getConfig().getString("language", "en");

        if (getConfig().getBoolean("first-run", true)) {
            getLogger().info(" ");
            getLogger().info("§e=== TeleNukkit First-Time Setup ===");
            getLogger().info("§aPlease run these commands in console:");
            getLogger().info("§b/telesetup telegram <botToken>");
            getLogger().info("§b/telesetup matrix <homeserver> <accessToken> <roomId>");
            getLogger().info(" ");
            return;
        }

        try {
            bridgeManager = new BridgeManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("TeleNukkit successfully enabled!");
        } catch (Exception e) {
            getLogger().error("Error while starting plugin", e);
        }
    }

    private void initLanguages() {
        languages = new HashMap<>();
        Map<String, String> en = new HashMap<>();
        en.put("online", "Players online: ");
        en.put("join", " joined the game!");
        en.put("quit", " left the game!");
        languages.put("en", new LanguagePack(en));
        
        Map<String, String> ru = new HashMap<>();
        ru.put("online", "Игроков онлайн: ");
        ru.put("join", " зашел на сервер!");
        ru.put("quit", " вышел с сервера!");
        languages.put("ru", new LanguagePack(ru));
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).join);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).quit);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("telesetup")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /telesetup <telegram|matrix> [settings]");
                return true;
            }

            String bridgeType = args[0].toLowerCase();
            
            switch (bridgeType) {
                case "telegram":
                    if (args.length != 2) {
                        sender.sendMessage("§cUsage: /telesetup telegram <botToken>");
                        return true;
                    }
                    getConfig().set("telegram.botToken", args[1]);
                    sender.sendMessage("§aTelegram token set successfully!");
                    break;
                    
                case "matrix":
                    if (args.length != 4) {
                        sender.sendMessage("§cUsage: /telesetup matrix <homeserver> <accessToken> <roomId>");
                        return true;
                    }
                    getConfig().set("matrix.homeserver", args[1]);
                    getConfig().set("matrix.accessToken", args[2]);
                    getConfig().set("matrix.roomId", args[3]);
                    sender.sendMessage("§aMatrix settings configured!");
                    break;
                    
                default:
                    sender.sendMessage("§cUnknown bridge type. Use 'telegram' or 'matrix'");
                    return true;
            }
            
            getConfig().set("first-run", false);
            saveConfig();
            sender.sendMessage("§aRestart server to apply changes!");
            return true;
        }
        return false;
    }

    static class LanguagePack {
        public final String online;
        public final String join;
        public final String quit;

        public LanguagePack(Map<String, String> messages) {
            this.online = messages.get("online");
            this.join = messages.get("join");
            this.quit = messages.get("quit");
        }
    }
}
