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
    private boolean setupCompleted;

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
        setupCompleted = !getConfig().getBoolean("settings.first-run-setup", true);

        if (!setupCompleted) {
            getLogger().info("========== Initial Setup ==========");
            getLogger().info("1. Telegram bot: " + 
                (getConfig().getString("telegram.botToken", "").isEmpty() ? 
                "NOT CONFIGURED" : "CONFIGURED"));
            getLogger().info("2. Matrix bot: " + 
                (getConfig().getString("matrix.accessToken", "").isEmpty() ? 
                "NOT CONFIGURED" : "CONFIGURED"));
            getLogger().info("Edit config.yml and restart server");
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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "setlanguage":
                if (args.length == 1 && languages.containsKey(args[0])) {
                    language = args[0];
                    getConfig().set("language", args[0]);
                    saveConfig();
                    sender.sendMessage("Language set to " + args[0]);
                    return true;
                }
                sender.sendMessage("Usage: /setlanguage <ru|en>");
                return true;
                
            case "togglebridge":
                if (args.length == 1) {
                    String bridgeName = args[0].toLowerCase();
                    boolean currentState = getConfig().getBoolean(bridgeName + ".enabled", true);
                    getConfig().set(bridgeName + ".enabled", !currentState);
                    saveConfig();
                    sender.sendMessage(bridgeName + " bridge " + (!currentState ? "ENABLED" : "DISABLED"));
                    return true;
                }
                sender.sendMessage("Usage: /togglebridge <telegram|matrix>");
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

class BridgeManager {
    private final Main plugin;
    private TelegramBridge telegramBridge;
    private MatrixBridge matrixBridge;
    
    public BridgeManager(Main plugin) throws Exception {
        this.plugin = plugin;
        
        if (plugin.getConfig().getBoolean("telegram.enabled", false)) {
            String token = plugin.getConfig().getString("telegram.botToken");
            if (token != null && !token.isEmpty()) {
                telegramBridge = new TelegramBridge(plugin, token);
            }
        }
        
        if (plugin.getConfig().getBoolean("matrix.enabled", false)) {
            matrixBridge = new MatrixBridge(
                plugin,
                plugin.getConfig().getString("matrix.homeserver"),
                plugin.getConfig().getString("matrix.accessToken"),
                plugin.getConfig().getString("matrix.roomId")
            );
        }
    }
    
    public void sendToBridges(String source, String sender, String message) {
        String format = plugin.getConfig().getString("formats." + source + "-to-bridge-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        String formatted = format.replace("{sender}", sender).replace("{message}", message);
        
        if (telegramBridge != null) telegramBridge.sendMessage(formatted);
        if (matrixBridge != null) matrixBridge.sendMessage(formatted);
    }
    
    public void sendToMinecraft(String source, String sender, String message) {
        String format = plugin.getConfig().getString("formats." + source + "-to-minecraft-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        plugin.getServer().broadcastMessage(format.replace("{sender}", sender).replace("{message}", message));
    }
}

class TelegramBridge extends TelegramLongPollingBot {
    private final Main plugin;
    private final String token;
    private String activeGroupChatId;
    
    public TelegramBridge(Main plugin, String token) throws Exception {
        this.plugin = plugin;
        this.token = token;
        new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
    }
    
    @Override 
    public String getBotUsername() { return "TeleNukkitBot"; }
    
    @Override 
    public String getBotToken() { return token; }
    
    private void sendToChat(String chatId, String message) {
        try {
            execute(new SendMessage(chatId, message));
        } catch (TelegramApiException e) {
            plugin.getLogger().error("Error sending to Telegram", e);
        }
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        
        Message message = update.getMessage();
        Chat chat = message.getChat();
        
        if (chat.isGroupChat() || chat.isSuperGroupChat()) {
            if (activeGroupChatId == null) {
                activeGroupChatId = chat.getId().toString();
                sendToChat(activeGroupChatId, "Bot activated in this group!");
                return;
            }
            
            String text = message.getText();
            String sender = message.getFrom().getUserName();
            
            if (text.equalsIgnoreCase("/online")) {
                sendToChat(activeGroupChatId, 
                    plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
            } else {
                plugin.getBridgeManager().sendToMinecraft("telegram", sender, text);
            }
        }
    }
    
    public void sendMessage(String message) {
        if (activeGroupChatId != null) {
            sendToChat(activeGroupChatId, message);
        }
    }
}

class MatrixBridge {
    private final Main plugin;
    private final String homeserver;
    private final String accessToken;
    private final String roomId;
    private final OkHttpClient httpClient = new OkHttpClient();
    
    public MatrixBridge(Main plugin, String homeserver, String accessToken, String roomId) {
        this.plugin = plugin;
        this.homeserver = homeserver;
        this.accessToken = accessToken;
        this.roomId = roomId;
    }
    
    public void sendMessage(String message) {
        try {
            JSONObject body = new JSONObject()
                .put("msgtype", "m.text")
                .put("body", message);
            
            Request request = new Request.Builder()
                .url(homeserver + "/_matrix/client/r0/rooms/" + roomId + "/send/m.room.message?access_token=" + accessToken)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    plugin.getLogger().error("Matrix message failed: " + e.getMessage());
                }
                
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        plugin.getLogger().error("Matrix message error: " + response.body().string());
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().error("Matrix send error", e);
        }
    }
}
