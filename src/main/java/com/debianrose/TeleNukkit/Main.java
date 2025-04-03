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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        
        initLanguages();
        language = getConfig().getString("language", "en");
        
        try {
            bridgeManager = new BridgeManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            
            if (getConfig().getBoolean("check-for-updates", true)) {
                new UpdateChecker(this).check();
            }
            
            getLogger().info("TeleNukkit успешно запущен!");
        } catch (Exception e) {
            getLogger().error("Ошибка при запуске плагина", e);
        }
    }

    private void initLanguages() {
        languages = new HashMap<>();
        languages.put("en", new LanguagePack("Players online: ", " joined the game!", " left the game!"));
        languages.put("ru", new LanguagePack("Игроков онлайн: ", " зашел на сервер!", " вышел с сервера!"));
        languages.put("ua", new LanguagePack("Гравців онлайн: ", " зайшов на сервер!", " вийшов з сервера!"));
        languages.put("kz", new LanguagePack("Ойыншылар онлайн: ", " серверге кірді!", " серверден шықты!"));
        languages.put("es", new LanguagePack("Jugadores en línea: ", " se unió al juego!", " dejó el juego!"));
        languages.put("ar", new LanguagePack("اللاعبون على الإنترنت: ", " انضم إلى اللعبة!", " غادر اللعبة!"));
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
        if (command.getName().equalsIgnoreCase("setlanguage")) {
            if (args.length == 1 && languages.containsKey(args[0])) {
                language = args[0];
                getConfig().set("language", args[0]);
                saveConfig();
                sender.sendMessage("Language set to " + args[0]);
                return true;
            }
            sender.sendMessage("Usage: /setlanguage <ru|en|es|uk|kk|ar>");
            return true;
        }
        return false;
    }

    public String getLanguage() { return language; }
    public LanguagePack getLanguagePack() { return languages.get(language); }
    public BridgeManager getBridgeManager() { return bridgeManager; }

    static class LanguagePack {
        public final String online, join, quit;
        public LanguagePack(String online, String join, String quit) {
            this.online = online; this.join = join; this.quit = quit;
        }
    }
}

class BridgeManager {
    private final Main plugin;
    private TelegramBridge telegramBridge;
    private MatrixBridge matrixBridge;
    
    public BridgeManager(Main plugin) throws Exception {
        this.plugin = plugin;
        
        String telegramToken = plugin.getConfig().getString("telegram.botToken");
        if (telegramToken != null && !telegramToken.isEmpty()) {
            telegramBridge = new TelegramBridge(plugin, telegramToken);
        }
        
        if (plugin.getConfig().exists("matrix")) {
            matrixBridge = new MatrixBridge(
                plugin,
                plugin.getConfig().getString("matrix.homeserver"),
                plugin.getConfig().getString("matrix.username"),
                plugin.getConfig().getString("matrix.password"),
                plugin.getConfig().getString("matrix.roomId")
            );
        }
    }
    
    public void sendToBridges(String source, String sender, String message) {
        String format = plugin.getConfig().getString(source + "-to-bridge-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        String formatted = format.replace("{sender}", sender).replace("{message}", message);
        
        if (telegramBridge != null) telegramBridge.sendMessage(formatted);
        if (matrixBridge != null) matrixBridge.sendMessage(formatted);
    }
    
    public void sendToMinecraft(String source, String sender, String message) {
        String format = plugin.getConfig().getString(source + "-to-minecraft-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        plugin.getServer().broadcastMessage(format.replace("{sender}", sender).replace("{message}", message));
    }
}

class TelegramBridge extends TelegramLongPollingBot {
    private final Main plugin;
    private final String token;
    
    public TelegramBridge(Main plugin, String token) throws Exception {
        this.plugin = plugin;
        this.token = token;
        new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
        plugin.getLogger().info("Telegram бот подключен!");
    }
    
    @Override public String getBotUsername() { return "TeleNukkitBot"; }
    @Override public String getBotToken() { return token; }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            String sender = update.getMessage().getFrom().getUserName();
            
            if (text.equalsIgnoreCase("/online")) {
                sendMessage(plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
            } else {
                plugin.getBridgeManager().sendToMinecraft("telegram", sender, text);
            }
        }
    }
    
    public void sendMessage(String message) {
        try {
            execute(new SendMessage("@your_channel", message));
        } catch (TelegramApiException e) {
            plugin.getLogger().error("Ошибка отправки в Telegram", e);
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
        
        plugin.getLogger().info("Matrix бот подключен к комнате: " + roomId);
    }
    
    public void sendMessage(String message) {
        try {
            room.sendMessage(Message.builder()
                .body(message)
                .formattedBody("<b>" + message + "</b>")
                .build());
        } catch (Exception e) {
            plugin.getLogger().error("Ошибка отправки в Matrix", e);
        }
    }
}

class UpdateChecker {
    private final Main plugin;
    public UpdateChecker(Main plugin) { this.plugin = plugin; }
    public void check() {}
}
