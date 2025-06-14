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
    private final Map<String, LanguagePack> languages = new HashMap<>();

    public class BridgeManager {
        private final Main plugin;
        private TelegramBridge telegramBridge;
        private MatrixBridge matrixBridge;
        private DiscordBridge discordBridge;
        
        public BridgeManager(Main plugin) throws Exception {
            this.plugin = plugin;
            if (plugin.getConfig().getBoolean("telegram.enabled") && !plugin.getConfig().getString("telegram.botToken").isEmpty())
                telegramBridge = new TelegramBridge(plugin, plugin.getConfig().getString("telegram.botToken"));
            if (plugin.getConfig().getBoolean("matrix.enabled") && !plugin.getConfig().getString("matrix.accessToken").isEmpty())
                matrixBridge = new MatrixBridge(plugin,
                    plugin.getConfig().getString("matrix.homeserver"),
                    plugin.getConfig().getString("matrix.accessToken"),
                    plugin.getConfig().getString("matrix.roomId"));
            if (plugin.getConfig().getBoolean("discord.enabled") && !plugin.getConfig().getString("discord.token").isEmpty())
                discordBridge = new DiscordBridge(plugin,
                    plugin.getConfig().getString("discord.token"),
                    plugin.getConfig().getString("discord.channel_id"));
        }
        
        public void sendToBridges(String source, String sender, String message) {
            String format = plugin.getConfig().getString("formats." + source + "-to-bridge-format",
                "[" + source.toUpperCase() + "] {sender}: {message}");
            String formatted = format.replace("{sender}", sender).replace("{message}", message);
            if (telegramBridge != null) telegramBridge.sendMessage(formatted);
            if (matrixBridge != null) matrixBridge.sendMessage(formatted);
            if (discordBridge != null) discordBridge.sendMessage(formatted);
        }
    }

    public class DiscordBridge {
        private final Main plugin;
        private final String token;
        private final String channelId;
        private final OkHttpClient httpClient = new OkHttpClient();
        
        public DiscordBridge(Main plugin, String token, String channelId) {
            this.plugin = plugin;
            this.token = token;
            this.channelId = channelId;
        }
        
        public void sendMessage(String message) {
            try {
                JSONObject body = new JSONObject().put("content", message);
                Request request = new Request.Builder()
                    .url("https://discord.com/api/v9/channels/" + channelId + "/messages")
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .header("Authorization", "Bot " + token)
                    .header("User-Agent", "TeleNukkit")
                    .build();
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {
                        plugin.getLogger().error("Discord message failed: " + e.getMessage());
                    }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) plugin.getLogger().error("Discord message error: " + response.body().string());
                        response.close();
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().error("Discord send error", e);
            }
        }
    }

    public class TelegramBridge extends TelegramLongPollingBot {
        private final Main plugin;
        private final String token;
        private String activeGroupChatId;
        
        public TelegramBridge(Main plugin, String token) throws Exception {
            this.plugin = plugin;
            this.token = token;
            new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
        }
        
        @Override public String getBotUsername() { return "TeleNukkitBot"; }
        @Override public String getBotToken() { return token; }
        
        private void sendToChat(String chatId, String message) {
            try {
                execute(new SendMessage(chatId, message));
            } catch (TelegramApiException e) {
                plugin.getLogger().error("Error sending to Telegram", e);
            }
        }
        
        @Override public void onUpdateReceived(Update update) {
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
                if (text.equalsIgnoreCase("/online"))
                    sendToChat(activeGroupChatId, plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
                else
                    plugin.getBridgeManager().sendToBridges("telegram", sender, text);
            }
        }
        
        public void sendMessage(String message) {
            if (activeGroupChatId != null) sendToChat(activeGroupChatId, message);
        }
    }

    public class MatrixBridge {
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
                JSONObject body = new JSONObject().put("msgtype", "m.text").put("body", message);
                Request request = new Request.Builder()
                    .url(homeserver + "/_matrix/client/r0/rooms/" + roomId + "/send/m.room.message?access_token=" + accessToken)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .build();
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {
                        plugin.getLogger().error("Matrix message failed: " + e.getMessage());
                    }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) plugin.getLogger().error("Matrix message error: " + response.body().string());
                        response.close();
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().error("Matrix send error", e);
            }
        }
    }

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
        if (getConfig().getBoolean("settings.first-run-setup", true)) {
            getLogger().info(" ");
            getLogger().info("§e=== TeleNukkit First-Time Setup ===");
            getLogger().info("§aPlease run these commands in console:");
            getLogger().info("§b/telesetup telegram <botToken>");
            getLogger().info("§b/telesetup matrix <homeserver> <accessToken> <roomId>");
            getLogger().info("§b/telesetup discord <token> <channelId>");
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
        Map<String, String> en = Map.of("online", "Players online: ", "join", " joined the game!", "quit", " left the game!");
        languages.put("en", new LanguagePack(en));
        Map<String, String> ru = Map.of("online", "Игроков онлайн: ", "join", " зашел на сервер!", "quit", " вышел с сервера!");
        languages.put("ru", new LanguagePack(ru));
        Map<String, String> es = Map.of("online", "Jugadores en línea: ", "join", " se unió al juego!", "quit", " dejó el juego!");
        languages.put("es", new LanguagePack(es));
        Map<String, String> fr = Map.of("online", "Joueurs en ligne: ", "join", " a rejoint le jeu!", "quit", " a quitté le jeu!");
        languages.put("fr", new LanguagePack(fr));
        Map<String, String> zh = Map.of("online", "在线玩家: ", "join", " 加入了游戏!", "quit", " 离开了游戏!");
        languages.put("zh", new LanguagePack(zh));
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
        if (!sender.hasPermission("op")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /telesetup <telegram|matrix|discord> [settings]");
            sender.sendMessage("§eExample for Telegram: /telesetup telegram YOUR_BOT_TOKEN");
            sender.sendMessage("§eExample for Matrix: /telesetup matrix HOMESERVER ACCESS_TOKEN ROOM_ID");
            sender.sendMessage("§eExample for Discord: /telesetup discord BOT_TOKEN CHANNEL_ID");
            return true;
        }

        String bridgeType = args[0].toLowerCase();
        switch (bridgeType) {
            case "telegram":
                return setupTelegram(sender, args);
            case "matrix":
                return setupMatrix(sender, args);
            case "discord":
                return setupDiscord(sender, args);
            default:
                sender.sendMessage("§cUnknown bridge type. Available: telegram, matrix, discord");
                return true;
        }
    }
    return false;
}

private boolean setupTelegram(CommandSender sender, String[] args) {
    if (args.length != 2) {
        sender.sendMessage("§cUsage: /telesetup telegram <botToken>");
        return true;
    }
    
    getConfig().set("telegram.enabled", true);
    getConfig().set("telegram.botToken", args[1]);
    saveConfig();
    
    sender.sendMessage("§aTelegram token set successfully! Restart server to apply changes.");
    return true;
}

private boolean setupMatrix(CommandSender sender, String[] args) {
    if (args.length != 4) {
        sender.sendMessage("§cUsage: /telesetup matrix <homeserver> <accessToken> <roomId>");
        sender.sendMessage("§eExample: /telesetup matrix https://matrix.org MYSECRETTOKEN !roomid:matrix.org");
        return true;
    }
    
    getConfig().set("matrix.enabled", true);
    getConfig().set("matrix.homeserver", args[1]);
    getConfig().set("matrix.accessToken", args[2]);
    getConfig().set("matrix.roomId", args[3]);
    saveConfig();
    
    sender.sendMessage("§aMatrix settings configured! Restart server to apply changes.");
    return true;
}

private boolean setupDiscord(CommandSender sender, String[] args) {
    if (args.length != 3) {
        sender.sendMessage("§cUsage: /telesetup discord <botToken> <channelId>");
        sender.sendMessage("§eExample: /telesetup discord 1234567890abcdef 987654321098765432");
        return true;
    }
    
    getConfig().set("discord.enabled", true);
    getConfig().set("discord.token", args[1]);
    getConfig().set("discord.channel_id", args[2]);
    saveConfig();
    
    sender.sendMessage("§aDiscord settings configured! Restart server to apply changes.");
    return true;
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
