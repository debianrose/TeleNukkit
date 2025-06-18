package com.debianrose.TeleNukkit;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.Config;
import cn.nukkit.scheduler.AsyncTask;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.json.JSONObject;
import okhttp3.*;
import java.util.*;
import java.io.IOException;
import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

public class Main extends PluginBase implements Listener {
    private BridgeManager bridgeManager;
    private String language;
    private final Map<String, LanguagePack> languages = new HashMap<>();
    private Config prefixes;
    private Config accountLinks;
    private final HashMap<String, String> prefixCache = new HashMap<>();
    private final HashMap<String, String> accountLinkCache = new HashMap<>();
    private final Map<String, String> linkCodes = new HashMap<>();
    private final Map<String, String> reverseLinks = new HashMap<>();
    private boolean updateAvailable = false;
    private String latestVersion = "";

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
        
        public void sendToGame(String sender, String message) {
            if (!plugin.getConfig().getBoolean("features.cross-chat", true)) return;
            
            String format = plugin.getConfig().getString("formats.telegram-to-minecraft", "[TG] {sender}: {message}");
            String formatted = format.replace("{sender}", sender).replace("{message}", message);
            for (Player player : plugin.getServer().getOnlinePlayers().values()) {
                player.sendMessage(formatted);
            }
        }
        
        public void sendToBridges(String source, String sender, String message) {
            String format = source.equals("system") 
                ? plugin.getConfig().getString("formats.system-message", "[System] {message}")
                : plugin.getConfig().getString("formats." + source + "-to-bridge", "[{sender}] {message}");
            
            String formatted = format.replace("{sender}", sender).replace("{message}", message);
            
            if (!source.equalsIgnoreCase("telegram") && telegramBridge != null) {
                telegramBridge.sendMessage(formatted);
            }
            if (!source.equalsIgnoreCase("matrix") && matrixBridge != null) {
                matrixBridge.sendMessage(formatted);
            }
            if (!source.equalsIgnoreCase("discord") && discordBridge != null) {
                discordBridge.sendMessage(formatted);
            }
        }
        
        public void processLinkCommand(String messenger, String externalId, String code) {
            if (!plugin.getConfig().getBoolean("features.account-linking", true)) {
                sendToBridges(messenger, "System", "Account linking is currently disabled");
                return;
            }
            plugin.handleLinkCode(messenger, externalId, code);
        }
    }

    public class TelegramBridge extends TelegramLongPollingBot {
        private final Main plugin;
        private final String token;
        private String activeGroupChatId;
        private final Set<String> processedMessages = new HashSet<>();
        
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
            String messageId = message.getMessageId().toString();
            if (processedMessages.contains(messageId)) return;
            processedMessages.add(messageId);
            
            Chat chat = message.getChat();
            String text = message.getText();
            User user = message.getFrom();
            String sender = user.getUserName() != null ? user.getUserName() : user.getFirstName();
            
            if (text.startsWith("/link ")) {
                String code = text.substring(6).trim();
                plugin.getBridgeManager().processLinkCommand("telegram", sender, code);
                return;
            }
            
            if (chat.isGroupChat() || chat.isSuperGroupChat()) {
                if (activeGroupChatId == null) {
                    activeGroupChatId = chat.getId().toString();
                    sendToChat(activeGroupChatId, "Bot activated in this group!");
                    return;
                }
                
                if (text.equalsIgnoreCase("/online")) {
                    sendToChat(activeGroupChatId, plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
                } else if (!text.startsWith("/")) {
                    String minecraftName = plugin.reverseLinks.get(sender);
                    String displayName = minecraftName != null ? minecraftName : sender;
                    plugin.getBridgeManager().sendToGame(displayName, text);
                }
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        initLanguages();
        loadDataFiles();
        language = getConfig().getString("language", "en");

        if (getConfig().getBoolean("settings.check-for-updates", true)) {
            checkForUpdates();
        }

        try {
            bridgeManager = new BridgeManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("TeleNukkit successfully enabled!");
            if (updateAvailable) {
                getLogger().info("§eUpdate available! Version " + latestVersion);
                bridgeManager.sendToBridges("system", "Update Checker", 
                    "New plugin update available: " + latestVersion);
            }
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

    private void loadDataFiles() {
        this.getDataFolder().mkdirs();
        this.prefixes = new Config(new File(this.getDataFolder(), "prefixes.yml"), Config.YAML);
        this.accountLinks = new Config(new File(this.getDataFolder(), "account_links.yml"), Config.YAML);
        
        for (String key : accountLinks.getKeys()) {
            String externalId = accountLinks.getString(key);
            reverseLinks.put(externalId, key);
        }
    }

    private void checkForUpdates() {
        getServer().getScheduler().scheduleAsyncTask(this, new AsyncTask() {
            @Override
            public void onRun() {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url("https://api.github.com/repos/debianrose/TeleNukkit/releases/latest")
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(response.body().string());
                        latestVersion = json.getString("tag_name").replace("v", "");
                        
                        if (!getDescription().getVersion().equals(latestVersion)) {
                            updateAvailable = true;
                        }
                    }
                } catch (Exception e) {
                    getLogger().error("Failed to check for updates: " + e.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String prefix = getPrefix(player);
        String linkedAccount = getLinkedAccount(player);
        
        String senderName = player.getName();
        if (linkedAccount != null) {
            senderName = "[MC] " + senderName;
        }
        if (!prefix.isEmpty()) {
            senderName = prefix + " " + senderName;
        }
        
        bridgeManager.sendToBridges("minecraft", senderName, event.getMessage());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("features.join-notifications", true)) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).join);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("features.quit-notifications", true)) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).quit);
        }
    }

    public void handleLinkCode(String messenger, String externalId, String code) {
        String playerName = linkCodes.get(code);
        if (playerName == null) {
            bridgeManager.sendToBridges(messenger, "System", "Invalid link code!");
            return;
        }
        
        accountLinks.set(playerName.toLowerCase(), externalId);
        accountLinks.save();
        accountLinkCache.put(playerName.toLowerCase(), externalId);
        reverseLinks.put(externalId, playerName.toLowerCase());
        
        prefixes.set(playerName.toLowerCase(), "[" + messenger.toUpperCase() + "]");
        prefixes.save();
        prefixCache.put(playerName.toLowerCase(), "[" + messenger.toUpperCase() + "]");
        
        bridgeManager.sendToBridges("system", "System", playerName + " linked their account to " + messenger);
        Player player = getServer().getPlayerExact(playerName);
        if (player != null) {
            player.sendMessage("§aYour account has been linked to " + messenger + "!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "telesetup":
                return handleTeleSetup(sender, args);
            case "getlinkcode":
            case "linkcode":
            case "getcodelink":
                return handleGetLinkCode(sender);
            case "unlinkaccount":
                return handleUnlinkAccount(sender);
            case "togglelinking":
                return handleToggleLinking(sender, args);
            case "checkupdate":
                return handleCheckUpdate(sender);
            default:
                return handleUnknownCommand(sender);
        }
    }

    private boolean handleTeleSetup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telenukkit.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage("§eUsage:");
            sender.sendMessage("§b/telesetup telegram <botToken>");
            sender.sendMessage("§b/telesetup matrix <homeserver> <accessToken> <roomId>");
            sender.sendMessage("§b/telesetup discord <token> <channelId>");
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

    private boolean setupTelegram(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /telesetup telegram <botToken>");
            return true;
        }
        
        getConfig().set("telegram.enabled", true);
        getConfig().set("telegram.botToken", args[1]);
        saveConfig();
        
        try {
            bridgeManager = new BridgeManager(this);
            sender.sendMessage("§aTelegram configured! Restart server to apply changes.");
            bridgeManager.sendToBridges("system", "System", "Telegram bridge was configured");
        } catch (Exception e) {
            sender.sendMessage("§cError configuring Telegram: " + e.getMessage());
        }
        return true;
    }

    private boolean setupMatrix(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /telesetup matrix <homeserver> <accessToken> <roomId>");
            return true;
        }
        
        getConfig().set("matrix.enabled", true);
        getConfig().set("matrix.homeserver", args[1]);
        getConfig().set("matrix.accessToken", args[2]);
        getConfig().set("matrix.roomId", args[3]);
        saveConfig();
        
        try {
            bridgeManager = new BridgeManager(this);
            sender.sendMessage("§aMatrix configured! Restart server to apply changes.");
            bridgeManager.sendToBridges("system", "System", "Matrix bridge was configured");
        } catch (Exception e) {
            sender.sendMessage("§cError configuring Matrix: " + e.getMessage());
        }
        return true;
    }

    private boolean setupDiscord(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /telesetup discord <token> <channelId>");
            return true;
        }
        
        getConfig().set("discord.enabled", true);
        getConfig().set("discord.token", args[1]);
        getConfig().set("discord.channel_id", args[2]);
        saveConfig();
        
        try {
            bridgeManager = new BridgeManager(this);
            sender.sendMessage("§aDiscord configured! Restart server to apply changes.");
            bridgeManager.sendToBridges("system", "System", "Discord bridge was configured");
        } catch (Exception e) {
            sender.sendMessage("§cError configuring Discord: " + e.getMessage());
        }
        return true;
    }

    private boolean handleGetLinkCode(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command is for players only!");
            return true;
        }
        
        if (!getConfig().getBoolean("features.account-linking", true)) {
            sender.sendMessage("§cAccount linking is currently disabled!");
            return true;
        }
        
        Player player = (Player) sender;
        String code = generateLinkCode();
        linkCodes.put(code, player.getName());
        
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            linkCodes.remove(code);
        }, 20 * 60 * 5);
        
        player.sendMessage("§aYour link code: §e" + code);
        player.sendMessage("§aUse this code in your messaging app with /link command");
        return true;
    }

    private boolean handleUnlinkAccount(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command is for players only!");
            return true;
        }
        
        Player player = (Player) sender;
        String playerName = player.getName().toLowerCase();
        
        if (!accountLinks.exists(playerName)) {
            sender.sendMessage("§cYour account is not linked!");
            return true;
        }
        
        String externalId = accountLinks.getString(playerName);
        accountLinks.remove(playerName);
        accountLinks.save();
        accountLinkCache.remove(playerName);
        reverseLinks.remove(externalId);
        prefixes.remove(playerName);
        prefixes.save();
        prefixCache.remove(playerName);
        
        sender.sendMessage("§aYour account has been unlinked!");
        bridgeManager.sendToBridges("system", "System", player.getName() + " unlinked their account");
        return true;
    }

    private boolean handleToggleLinking(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telenukkit.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /togglelinking <on|off>");
            return true;
        }
        
        boolean enable = args[0].equalsIgnoreCase("on");
        getConfig().set("features.account-linking", enable);
        saveConfig();
        
        String status = enable ? "§aenabled" : "§cdisabled";
        sender.sendMessage("§7Account linking has been " + status);
        bridgeManager.sendToBridges("system", "System", 
            "Account linking has been " + (enable ? "enabled" : "disabled"));
        return true;
    }

    private boolean handleCheckUpdate(CommandSender sender) {
        if (updateAvailable) {
            sender.sendMessage("§eUpdate available! Version " + latestVersion);
            sender.sendMessage("§eDownload at: https://github.com/debianrose/TeleNukkit/releases");
        } else {
            sender.sendMessage("§aYou're using the latest version!");
        }
        return true;
    }

    private boolean handleUnknownCommand(CommandSender sender) {
        sender.sendMessage("§cUnknown command. Available commands:");
        sender.sendMessage("§a/getlinkcode §7- Get account linking code");
        sender.sendMessage("§a/unlinkaccount §7- Unlink your account");
        sender.sendMessage("§a/togglelinking §7- Toggle account linking (admin)");
        sender.sendMessage("§a/checkupdate §7- Check for plugin updates");
        sender.sendMessage("§a/telesetup §7- Configure bridges (admin)");
        return true;
    }

    private String generateLinkCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
    }

    private String getPrefix(Player player) {
        String name = player.getName().toLowerCase();
        if (prefixCache.containsKey(name)) {
            return prefixCache.get(name);
        }
        if (prefixes.exists(name)) {
            String prefix = prefixes.getString(name);
            prefixCache.put(name, prefix);
            return prefix;
        }
        return "";
    }

    private String getLinkedAccount(Player player) {
        String name = player.getName().toLowerCase();
        if (accountLinkCache.containsKey(name)) {
            return accountLinkCache.get(name);
        }
        if (accountLinks.exists(name)) {
            String account = accountLinks.getString(name);
            accountLinkCache.put(name, account);
            return account;
        }
        return null;
    }

    public BridgeManager getBridgeManager() {
        return bridgeManager;
    }

    public LanguagePack getLanguagePack() {
        return languages.get(language);
    }
}
