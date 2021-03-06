package com.nguyenquyhy.discordbridge.logics;

import com.google.common.reflect.TypeToken;
import com.nguyenquyhy.discordbridge.DiscordBridge;
import com.nguyenquyhy.discordbridge.config.ChannelConfig;
import com.nguyenquyhy.discordbridge.config.GlobalConfig;
import com.nguyenquyhy.discordbridge.config.TokenStore;
import com.nguyenquyhy.discordbridge.config.command.CommandConfig;
import com.nguyenquyhy.discordbridge.database.InMemoryStorage;
import com.nguyenquyhy.discordbridge.database.JsonFileStorage;
import com.nguyenquyhy.discordbridge.utils.ConfigUtil;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by Hy on 8/6/2016.
 */
public class ConfigHandler {
    private static DiscordBridge mod = DiscordBridge.getInstance();
    private static Logger logger = mod.getLogger();
    private static Path configDir = mod.getConfigDir();

    public static GlobalConfig loadConfiguration() throws ObjectMappingException, IOException {

        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        Path configFile = Paths.get(configDir + "/config.json");

        GsonConfigurationLoader configLoader = GsonConfigurationLoader.builder().setPath(configFile).build();
        ConfigurationNode configNode = configLoader.load();

        GlobalConfig config = configNode.getValue(TypeToken.of(GlobalConfig.class), new GlobalConfig());

        if (!Files.exists(configFile)) {
            Files.createFile(configFile);
            logger.info("Created default configuration!");

            ChannelConfig channel = new ChannelConfig();
            channel.initializeDefault();

            Path legacyConfigFile = Paths.get(configDir + "/config.conf");
            if (Files.exists(legacyConfigFile)) {
                logger.info("Migrating legacy config!");
                CommentedConfigurationNode legacyConfigNode = HoconConfigurationLoader.builder().setPath(legacyConfigFile).build().load();

                config.botToken = ConfigUtil.readString(legacyConfigNode, "BotToken", "");
                String token = ConfigUtil.readString(legacyConfigNode, "TokenStore", "JSON");
                switch (token) {
                    case "NONE":
                        config.tokenStore = TokenStore.NONE;
                        break;
                    case "InMemory":
                    case "MEMORY":
                        config.tokenStore = TokenStore.MEMORY;
                        break;
                    default:
                        config.tokenStore = TokenStore.JSON;
                        break;
                }
                channel.discordId = ConfigUtil.readString(legacyConfigNode, "Channel", "");
                channel.discord.joinedTemplate = ConfigUtil.readString(legacyConfigNode, "JoinedMessageTemplate", "_%s just joined the server_");
                channel.discord.leftTemplate = ConfigUtil.readString(legacyConfigNode, "LeftMessageTemplate", "_%s just left the server_");
                channel.discord.publicChat.authenticatedChatTemplate = ConfigUtil.readString(legacyConfigNode, "MessageInDiscordTemplate", "%s");
                channel.discord.publicChat.anonymousChatTemplate = ConfigUtil.readString(legacyConfigNode, "MessageInDiscordAnonymousTemplate", "_<%a>_ %s");
                channel.discord.serverUpMessage = ConfigUtil.readString(legacyConfigNode, "MessageInDiscordServerUp", "Server has started.");
                channel.discord.serverDownMessage = ConfigUtil.readString(legacyConfigNode, "MessageInDiscordServerDown", "Server has stopped.");
                channel.minecraft.chatTemplate = ConfigUtil.readString(legacyConfigNode, "MessageInMinecraftTemplate", "&7<%a> &f%s");
            } else {
                logger.info("Discord Bridge will not run until you have edited this file!");
            }
            config.channels.add(channel);
        }

        config.migrate();

        configNode.setValue(TypeToken.of(GlobalConfig.class), config);
        configLoader.save(configNode);
        logger.info("Configuration loaded.");

        if (config.channels.isEmpty()
                || !config.channels.stream().anyMatch(c -> !StringUtils.isBlank(c.discordId))) {
            logger.error("Channel ID is not set!");
        }

        switch (config.tokenStore) {
            case MEMORY:
                mod.setStorage(new InMemoryStorage());
                logger.info("Use InMemory storage.");
                break;
            case JSON:
                mod.setStorage(new JsonFileStorage(configDir));
                logger.info("Use JSON storage.");
                break;
            default:
                logger.warn("No Token Store! Logging in will be disabled.");
                break;
        }
        return config;
    }

    public static CommandConfig loadCommandConfiguration() throws ObjectMappingException, IOException {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        Path configFile = Paths.get(configDir + "/commands.json");

        GsonConfigurationLoader configLoader = GsonConfigurationLoader.builder().setPath(configFile).build();
        ConfigurationNode configNode = configLoader.load();

        CommandConfig config = configNode.getValue(TypeToken.of(CommandConfig.class), new CommandConfig());

        if (!Files.exists(configFile)) {
            Files.createFile(configFile);
            logger.info("Created default command configuration!");
        }

        configNode.setValue(TypeToken.of(CommandConfig.class), config);
        configLoader.save(configNode);
        logger.info("Command Configuration loaded.");

        return config;
    }
}
