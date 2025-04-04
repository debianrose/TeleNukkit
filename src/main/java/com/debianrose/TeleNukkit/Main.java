package com.debianrose.TeleNukkit;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.cosium.matrix_communication_client.*;
import java.util.HashMap;
import java.util.Map;

public class Main extends PluginBase implements Listener {
    private BridgeManager bridgeManager;
    private String language;
    private Map<String, LanguagePack> languages;
    private boolean setupCompleted;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        
        initLanguages();
        language = getConfig().getString("language", "en");
        setupCompleted = !getConfig().getBoolean("settings.first-run-setup", true);

        if (!setupCompleted) {
            getLogger().info(getMessage("setup.initial"));
            getLogger().info(getMessage("setup.telegram_status") + 
                (getConfig().getString("telegram.botToken", "").isEmpty() ? 
                getMessage("setup.not_configured") : getMessage("setup.configured")));
            getLogger().info(getMessage("setup.matrix_status") + 
                (getConfig().getString("matrix.username", "").isEmpty() ? 
                getMessage("setup.not_configured") : getMessage("setup.configured")));
            getLogger().info(getMessage("setup.instructions"));
            return;
        }

        try {
            bridgeManager = new BridgeManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            
            if (getConfig().getBoolean("settings.check-for-updates", true)) {
                new UpdateChecker(this).check();
            }
            
            getLogger().info(getMessage("plugin.enabled"));
        } catch (Exception e) {
            getLogger().error(getMessage("error.startup"), e);
        }
    }

    private void initLanguages() {
        languages = new HashMap<>();
        
        // English
        Map<String, String> en = new HashMap<>();
        en.put("plugin.enabled", "TeleNukkit successfully enabled!");
        en.put("error.startup", "Error while starting plugin");
        en.put("setup.initial", "========== Initial Setup ==========");
        en.put("setup.telegram_status", "1. Telegram bot: ");
        en.put("setup.matrix_status", "2. Matrix bot: ");
        en.put("setup.not_configured", "NOT CONFIGURED");
        en.put("setup.configured", "CONFIGURED");
        en.put("setup.instructions", "Edit config.yml and restart server");
        en.put("command.usage.setlanguage", "Usage: /setlanguage <ru|en|es|uk|kk|ar>");
        en.put("command.language_set", "Language set to ");
        en.put("command.togglebridge.usage", "Usage: /togglebridge <telegram|matrix>");
        en.put("command.togglebridge.status", " bridge ");
        en.put("command.togglebridge.enabled", "ENABLED");
        en.put("command.togglebridge.disabled", "DISABLED");
        en.put("command.bridgestatus.title", "Bridge status:");
        en.put("telegram.connected", "Telegram bot connected!");
        en.put("telegram.group_detected", "Telegram group detected: ");
        en.put("telegram.error.send", "Error sending to Telegram");
        en.put("matrix.connected", "Matrix bot connected to room: ");
        en.put("matrix.error.send", "Error sending to Matrix");
        en.put("update.checking", "Checking for updates...");
        en.put("update.available", "New version available: ");
        languages.put("en", new LanguagePack(
            "Players online: ", 
            " joined the game!", 
            " left the game!",
            en
        ));
        
        // Russian
        Map<String, String> ru = new HashMap<>();
        ru.put("plugin.enabled", "TeleNukkit успешно запущен!");
        ru.put("error.startup", "Ошибка при запуске плагина");
        ru.put("setup.initial", "========== Первоначальная настройка ==========");
        ru.put("setup.telegram_status", "1. Telegram бот: ");
        ru.put("setup.matrix_status", "2. Matrix бот: ");
        ru.put("setup.not_configured", "НЕ НАСТРОЕН");
        ru.put("setup.configured", "НАСТРОЕН");
        ru.put("setup.instructions", "Измените config.yml и перезапустите сервер");
        ru.put("command.usage.setlanguage", "Использование: /setlanguage <ru|en|es|uk|kk|ar>");
        ru.put("command.language_set", "Язык изменен на ");
        ru.put("command.togglebridge.usage", "Использование: /togglebridge <telegram|matrix>");
        ru.put("command.togglebridge.status", " мост ");
        ru.put("command.togglebridge.enabled", "ВКЛЮЧЕН");
        ru.put("command.togglebridge.disabled", "ВЫКЛЮЧЕН");
        ru.put("command.bridgestatus.title", "Состояние мостов:");
        ru.put("telegram.connected", "Telegram бот подключен!");
        ru.put("telegram.group_detected", "Группа Telegram определена: ");
        ru.put("telegram.error.send", "Ошибка отправки в Telegram");
        ru.put("matrix.connected", "Matrix бот подключен к комнате: ");
        ru.put("matrix.error.send", "Ошибка отправки в Matrix");
        ru.put("update.checking", "Проверка обновлений...");
        ru.put("update.available", "Доступна новая версия: ");
        languages.put("ru", new LanguagePack(
            "Игроков онлайн: ", 
            " зашел на сервер!", 
            " вышел с сервера!",
            ru
        ));
        
    }

    public String getMessage(String key) {
        LanguagePack pack = languages.get(language);
        if (pack != null && pack.messages.containsKey(key)) {
            return pack.messages.get(key);
        }
        // Fallback to English
        return languages.get("en").messages.get(key);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        if (setupCompleted) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), event.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (setupCompleted) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), 
                languages.get(language).join);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (setupCompleted) {
            bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), 
                languages.get(language).quit);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!setupCompleted) {
            sender.sendMessage(getMessage("setup.instructions"));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "setlanguage":
                if (args.length == 1 && languages.containsKey(args[0])) {
                    language = args[0];
                    getConfig().set("language", args[0]);
                    saveConfig();
                    sender.sendMessage(getMessage("command.language_set") + args[0]);
                } else {
                    sender.sendMessage(getMessage("command.usage.setlanguage"));
                }
                return true;
                
            case "togglebridge":
                if (args.length == 1) {
                    String bridgeName = args[0].toLowerCase();
                    String configPath = bridgeName + ".enabled";
                    boolean currentState = getConfig().getBoolean(configPath, true);
                    getConfig().set(configPath, !currentState);
                    saveConfig();
                    
                    String status = !currentState ? 
                        getMessage("command.togglebridge.enabled") : 
                        getMessage("command.togglebridge.disabled");
                    sender.sendMessage(bridgeName + getMessage("command.togglebridge.status") + status);
                    return true;
                }
                sender.sendMessage(getMessage("command.togglebridge.usage"));
                return true;
                
            case "bridgestatus":
                sender.sendMessage(getMessage("command.bridgestatus.title"));
                sender.sendMessage("- Telegram: " + 
                    (getConfig().getBoolean("telegram.enabled", true) ? 
                    getMessage("command.togglebridge.enabled") : 
                    getMessage("command.togglebridge.disabled")));
                sender.sendMessage("- Matrix: " + 
                    (getConfig().getBoolean("matrix.enabled", true) ? 
                    getMessage("command.togglebridge.enabled") : 
                    getMessage("command.togglebridge.disabled")));
                return true;
        }
        return false;
    }

    public String getLanguage() { return language; }
    public LanguagePack getLanguagePack() { return languages.get(language); }
    public BridgeManager getBridgeManager() { return bridgeManager; }
    public boolean isSetupCompleted() { return setupCompleted; }

    static class LanguagePack {
        public final String online;
        public final String join;
        public final String quit;
        public final Map<String, String> messages;
        
        public LanguagePack(String online, String join, String quit, Map<String, String> messages) {
            this.online = online;
            this.join = join;
            this.quit = quit;
            this.messages = messages;
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
            String telegramToken = plugin.getConfig().getString("telegram.botToken");
            if (telegramToken != null && !telegramToken.isEmpty()) {
                telegramBridge = new TelegramBridge(plugin, telegramToken);
                plugin.getLogger().info(plugin.getMessage("telegram.connected"));
            }
        }
        
        if (plugin.getConfig().getBoolean("matrix.enabled", false)) {
            String username = plugin.getConfig().getString("matrix.username");
            String password = plugin.getConfig().getString("matrix.password");
            String roomId = plugin.getConfig().getString("matrix.roomId");
            
            if (username != null && password != null && roomId != null) {
                matrixBridge = new MatrixBridge(
                    plugin,
                    plugin.getConfig().getString("matrix.homeserver"),
                    username,
                    password,
                    roomId
                );
                plugin.getLogger().info(plugin.getMessage("matrix.connected") + roomId);
            }
        }
    }
    
    public void sendToBridges(String source, String sender, String message) {
        String formatKey = source + "-to-bridge-format";
        String format = plugin.getConfig().getString("formats." + formatKey, 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        String formatted = format.replace("{sender}", sender).replace("{message}", message);
        
        if (telegramBridge != null && plugin.getConfig().getBoolean("telegram.enabled", true)) {
            telegramBridge.sendMessage(formatted);
        }
        
        if (matrixBridge != null && plugin.getConfig().getBoolean("matrix.enabled", true)) {
            matrixBridge.sendMessage(formatted);
        }
    }
    
    public void sendToMinecraft(String source, String sender, String message) {
        String formatKey = source + "-to-minecraft-format";
        String format = plugin.getConfig().getString("formats." + formatKey, 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        plugin.getServer().broadcastMessage(format.replace("{sender}", sender).replace("{message}", message));
    }
}

class TelegramBridge extends TelegramLongPollingBot {
    private final Main plugin;
    private final String token;
    private String activeGroupChatId = null;
    
    public TelegramBridge(Main plugin, String token) throws Exception {
        this.plugin = plugin;
        this.token = token;
        new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
    }
    
    @Override 
    public String getBotUsername() { return "TeleNukkitBot"; }
    
    @Override 
    public String getBotToken() { return token; }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        
        Message message = update.getMessage();
        Chat chat = message.getChat();
        
        if (chat.isGroupChat() || chat.isSuperGroupChat()) {
            if (activeGroupChatId == null) {
                activeGroupChatId = chat.getId().toString();
                plugin.getLogger().info(plugin.getMessage("telegram.group_detected") + chat.getTitle());
                sendMessage(activeGroupChatId, plugin.getMessage("plugin.enabled"));
                return;
            }
            
            if (!chat.getId().toString().equals(activeGroupChatId)) {
                return;
            }
            
            String text = message.getText();
            String sender = message.getFrom().getUserName();
            
            if (text.equalsIgnoreCase("/online")) {
                sendMessage(activeGroupChatId, 
                    plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
            } else {
                plugin.getBridgeManager().sendToMinecraft("telegram", sender, text);
            }
        }
    }
    
    public void sendMessage(String message) {
        if (activeGroupChatId != null) {
            try {
                execute(new SendMessage(activeGroupChatId, message));
            } catch (TelegramApiException e) {
                plugin.getLogger().error(plugin.getMessage("telegram.error.send"), e);
            }
        }
    }
}

class MatrixBridge {
    private final Main plugin;
    private final MatrixResources matrix;
    private final RoomResource room;
    
    public MatrixBridge(Main plugin, String homeserver, String username, String password, String roomId) throws Exception {
        this.plugin = plugin;
        this.matrix = MatrixResources.factory()
            .builder()
            .https()
            .hostname(homeserver)
            .defaultPort()
            .usernamePassword(username, password)
            .build();
        this.room = matrix.rooms().byId(roomId);
    }
    
    public void sendMessage(String message) {
        try {
            room.sendMessage(Message.builder()
                .body(message)
                .formattedBody("<b>" + message + "</b>")
                .build());
        } catch (Exception e) {
            plugin.getLogger().error(plugin.getMessage("matrix.error.send"), e);
        }
    }
}

class UpdateChecker {
    private final Main plugin;
    public UpdateChecker(Main plugin) { this.plugin = plugin; }
    
    public void check() {
        plugin.getLogger().info(plugin.getMessage("update.checking"));
    }
}
