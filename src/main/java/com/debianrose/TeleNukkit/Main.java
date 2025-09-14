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
import org.yaml.snakeyaml.Yaml;
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
import java.util.*;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.HttpURLConnection;

public class Main extends PluginBase implements Listener {
    private BridgeManager bridgeManager;
    private String language;
    private final Map<String, LanguagePack> languages = new HashMap<>();
    private Config prefixes;
    private Config accountLinks;
    private final HashMap<String, String> prefixCache = new HashMap<>();
    private final HashMap<String, String> accountLinkCache = new HashMap<>();
    private final Map<String, String> linkCodes = new ConcurrentHashMap<>();
    private final Map<String, String> reverseLinks = new HashMap<>();
    private boolean updateAvailable = false;
    private String latestVersion = "";
    private final String langFolderName = "lang";
    private final String crowdinBaseUrl = "https://raw.githubusercontent.com/debianrose/TeleNukkit/main/lang/";
    private final String[] supportedLanguages = {"en", "ru", "es", "fr", "zh"};

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadDataFiles();
        language = getConfig().getString("language", "en");
        downloadAndLoadLangFiles();

        if (getConfig().getBoolean("settings.check-for-updates", true)) {
            checkForUpdatesAsync();
        }

        try {
            bridgeManager = new BridgeManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("TeleNukkit successfully enabled!");
            notifyUpdateIfAvailable();
        } catch (Exception e) {
            getLogger().error("Error while starting plugin", e);
        }
    }

    private void notifyUpdateIfAvailable() {
        if (updateAvailable) {
            getLogger().info(tr("update_available").replace("{version}", latestVersion));
            bridgeManager.sendToBridges("system", "System", tr("update_available").replace("{version}", latestVersion));
        }
    }

    private void downloadAndLoadLangFiles() {
        File langFolder = new File(getDataFolder(), langFolderName);
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        for (String langCode : supportedLanguages) {
            File langFile = new File(langFolder, langCode + ".yml");
            if (!langFile.exists()) {
                downloadLangFile(langCode, langFile);
            }
            loadLanguagePackFromFile(langCode, langFile);
        }
    }

    private void downloadLangFile(String langCode, File langFile) {
        String urlStr = crowdinBaseUrl + langCode + ".yml";
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(langFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private void loadLanguagePackFromFile(String langCode, File langFile) {
        if (!langFile.exists()) {
            return;
        }
        try (InputStream in = new FileInputStream(langFile)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(in);
            if (raw instanceof Map) {
                Map<String, String> messages = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                    messages.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                languages.put(langCode, new LanguagePack(messages));
            }
        } catch (Exception ignored) {
        }
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

    private void checkForUpdatesAsync() {
        getServer().getScheduler().scheduleAsyncTask(this, new AsyncTask() {
            @Override
            public void onRun() {
                OkHttpClient client = createHttpClientWithProxy();
                Request request = new Request.Builder()
                    .url("https://api.github.com/repos/debianrose/TeleNukkit/releases/latest")
                    .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(response.body().string());
                        latestVersion = json.optString("tag_name", "").replace("v", "");
                        updateAvailable = !getDescription().getVersion().equals(latestVersion);
                    }
                } catch (Exception e) {
                    getLogger().error("Failed to check for updates: " + e.getMessage());
                }
            }
        });
    }

    private Proxy getProxyFromConfig() {
        String proxyType = getConfig().getString("proxy.type", "");
        String proxyHost = getConfig().getString("proxy.host", "");
        int proxyPort = getConfig().getInt("proxy.port", 0);
        if (proxyType.isEmpty() || proxyHost.isEmpty() || proxyPort == 0) return Proxy.NO_PROXY;

        Proxy.Type type;
        switch (proxyType.toLowerCase()) {
            case "socks":
            case "socks5":
                type = Proxy.Type.SOCKS;
                break;
            case "http":
                type = Proxy.Type.HTTP;
                break;
            default:
                type = Proxy.Type.DIRECT;
        }
        return new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
    }

    private OkHttpClient createHttpClientWithProxy() {
        Proxy proxy = getProxyFromConfig();
        return new OkHttpClient.Builder().proxy(proxy).build();
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String senderName = player.getName();
        String prefix = getPrefix(player);

        if (!prefix.isEmpty()) {
            senderName = prefix + " " + senderName;
        } else {
            senderName = "[MC] " + senderName;
        }

        bridgeManager.sendToBridges("minecraft", senderName, event.getMessage());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("features.join-notifications", true)) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), tr("join").replace("{player}", event.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("features.quit-notifications", true)) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), tr("quit").replace("{player}", event.getPlayer().getName()));
        }
    }

    public void handleLinkCode(String messenger, String externalId, String code) {
        String playerName = linkCodes.get(code);
        if (playerName == null) {
            bridgeManager.sendToBridges(messenger, "System", tr("invalid_link_code"));
            return;
        }

        accountLinks.set(playerName.toLowerCase(), externalId);
        accountLinks.save();
        accountLinkCache.put(playerName.toLowerCase(), externalId);
        reverseLinks.put(externalId, playerName.toLowerCase());

        prefixes.set(playerName.toLowerCase(), "[" + messenger.toUpperCase() + "]");
        prefixes.save();
        prefixCache.put(playerName.toLowerCase(), "[" + messenger.toUpperCase() + "]");

        bridgeManager.sendToBridges("system", "System", tr("link_success").replace("{player}", playerName).replace("{messenger}", messenger));
        Player player = getServer().getPlayerExact(playerName);
        if (player != null) {
            player.sendMessage("§a" + tr("link_success").replace("{player}", playerName).replace("{messenger}", messenger));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String command = cmd.getName().toLowerCase();
        switch (command) {
            case "telesetup": return handleTeleSetup(sender, args);
            case "getlinkcode":
            case "linkcode":
            case "getcodelink": return handleGetLinkCode(sender);
            case "unlinkaccount": return handleUnlinkAccount(sender);
            case "togglelinking": return handleToggleLinking(sender, args);
            case "checkupdate": return handleCheckUpdate(sender);
            case "setlang": return handleSetLang(sender, args);
            case "testproxy": return handleTestProxy(sender);
            default: return handleUnknownCommand(sender);
        }
    }

    private boolean handleSetLang(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telenukkit.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§eUsage: /setlang <lang>");
            sender.sendMessage("§eAvailable: en, ru, es, fr, zh");
            return true;
        }
        String newLang = args[0];
        if (!languages.containsKey(newLang)) {
            sender.sendMessage("§cLanguage not found: " + newLang);
            return true;
        }
        language = newLang;
        getConfig().set("language", newLang);
        saveConfig();
        sender.sendMessage("§aLanguage switched to: " + newLang);
        return true;
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
            case "telegram": return setupTelegram(sender, args);
            case "matrix": return setupMatrix(sender, args);
            case "discord": return setupDiscord(sender, args);
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
            sender.sendMessage("§c" + tr("account_linking_disabled"));
            return true;
        }

        Player player = (Player) sender;
        String code = generateLinkCode();
        linkCodes.put(code, player.getName());

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            linkCodes.remove(code);
        }, 20 * 60 * 5);

        player.sendMessage("§a" + tr("your_link_code").replace("{code}", code));
        player.sendMessage("§a" + tr("link_instruction"));
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
            sender.sendMessage("§c" + tr("account_not_linked"));
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

        sender.sendMessage("§a" + tr("unlink_success").replace("{player}", player.getName()));
        bridgeManager.sendToBridges("system", "System", tr("unlink_success").replace("{player}", player.getName()));
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

        String status = enable ? tr("enabled") : tr("disabled");
        sender.sendMessage("§7" + tr("account_linking_status").replace("{status}", status));
        bridgeManager.sendToBridges("system", "System", tr("account_linking_status").replace("{status}", status));
        return true;
    }

    private boolean handleCheckUpdate(CommandSender sender) {
        if (updateAvailable) {
            sender.sendMessage("§e" + tr("update_available").replace("{version}", latestVersion));
            sender.sendMessage("§eDownload at: https://github.com/debianrose/TeleNukkit/releases");
        } else {
            sender.sendMessage("§a" + tr("latest_version"));
        }
        return true;
    }

    private boolean handleTestProxy(CommandSender sender) {
        OkHttpClient client = createHttpClientWithProxy();
        Request req = new Request.Builder().url("https://api.github.com/").build();
        sender.sendMessage(tr("proxy_check_started"));
        getServer().getScheduler().scheduleAsyncTask(this, new AsyncTask() {
            @Override
            public void onRun() {
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful()) {
                        sender.sendMessage(tr("proxy_ok").replace("{code}", String.valueOf(resp.code())));
                    } else {
                        sender.sendMessage(tr("proxy_failed").replace("{code}", String.valueOf(resp.code())));
                    }
                } catch (Exception e) {
                    sender.sendMessage(tr("proxy_error").replace("{error}", e.getMessage()));
                }
            }
        });
        return true;
    }

    private boolean handleUnknownCommand(CommandSender sender) {
        sender.sendMessage("§c" + tr("unknown_command"));
        sender.sendMessage("§a/getlinkcode §7- " + tr("cmd_getlinkcode"));
        sender.sendMessage("§a/unlinkaccount §7- " + tr("cmd_unlinkaccount"));
        sender.sendMessage("§a/togglelinking §7- " + tr("cmd_togglelinking"));
        sender.sendMessage("§a/checkupdate §7- " + tr("cmd_checkupdate"));
        sender.sendMessage("§a/telesetup §7- " + tr("cmd_telesetup"));
        sender.sendMessage("§a/setlang §7- " + tr("cmd_setlang"));
        sender.sendMessage("§a/testproxy §7- " + tr("cmd_testproxy"));
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

    public BridgeManager getBridgeManager() {
        return bridgeManager;
    }

    public String tr(String key) {
        LanguagePack pack = languages.getOrDefault(language, languages.get("en"));
        return pack.get(key);
    }

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
                : plugin.getConfig().getString("formats.minecraft-to-bridge", "[{sender}] {message}");

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
                sendToBridges(messenger, "System", plugin.tr("account_linking_disabled"));
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

            Proxy proxy = plugin.getProxyFromConfig();
            if (!proxy.equals(Proxy.NO_PROXY)) {
                InetSocketAddress addr = (InetSocketAddress) proxy.address();
                if (proxy.type() == Proxy.Type.SOCKS) {
                    System.setProperty("socksProxyHost", addr.getHostName());
                    System.setProperty("socksProxyPort", Integer.toString(addr.getPort()));
                } else if (proxy.type() == Proxy.Type.HTTP) {
                    System.setProperty("http.proxyHost", addr.getHostName());
                    System.setProperty("http.proxyPort", Integer.toString(addr.getPort()));
                }
            }
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
            String sender = message.getFrom().getUserName();

            if (text.startsWith("/link ")) {
                String code = text.substring(6).trim();
                Main.this.getBridgeManager().processLinkCommand("telegram", sender, code);
                return;
            }

            if (chat.isGroupChat() || chat.isSuperGroupChat()) {
                if (activeGroupChatId == null) {
                    activeGroupChatId = chat.getId().toString();
                    sendToChat(activeGroupChatId, "Bot activated in this group!");
                    return;
                }

                if (text.equalsIgnoreCase("/online")) {
                    sendToChat(activeGroupChatId, Main.this.tr("online") + plugin.getServer().getOnlinePlayers().size());
                } else if (!text.startsWith("/")) {
                    String minecraftName = plugin.reverseLinks.get(sender);
                    String displayName = minecraftName != null ? minecraftName : sender;
                    Main.this.getBridgeManager().sendToGame(displayName, text);
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
        private final OkHttpClient httpClient;

        public MatrixBridge(Main plugin, String homeserver, String accessToken, String roomId) {
            this.plugin = plugin;
            this.homeserver = homeserver;
            this.accessToken = accessToken;
            this.roomId = roomId;
            this.httpClient = plugin.createHttpClientWithProxy();
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
        private final OkHttpClient httpClient;

        public DiscordBridge(Main plugin, String token, String channelId) {
            this.plugin = plugin;
            this.token = token;
            this.channelId = channelId;
            this.httpClient = plugin.createHttpClientWithProxy();
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

    public static class LanguagePack {
        private final Map<String, String> messages;
        public LanguagePack(Map<String, String> messages) {
            this.messages = messages;
        }
        public String get(String key) {
            return messages.getOrDefault(key, key);
        }
    }
}
