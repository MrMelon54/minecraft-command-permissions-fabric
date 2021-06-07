package com.github.tjeukayim.commandpermissionsfabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PermissionsMod implements ModInitializer {
    /**
     * Permission string prefix compatible with other modding frameworks.
     */
    public static final String PREFIX = "minecraft.command.";
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            if ("true".equals(System.getenv("minecraft-command-permissions.test"))) {
                var allCommands = dispatcher.getRoot().getChildren()
                        .stream()
                        .map(c -> "\"" + c.getName() + "\",")
                        .sorted()
                        .collect(Collectors.joining("\n"));
                LOGGER.info("All commands:\n{}", allCommands);
            }
            for (String name : VANILLA_COMMANDS) {
                alterCommand(dispatcher, name);
            }
            LOGGER.info("Loaded Minecraft Command Permissions");
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LuckPermsProvider.get().getEventBus().subscribe(UserDataRecalculateEvent.class, e -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getUser().getUniqueId());
                if (player != null) {
                    server.getPlayerManager().sendCommandTree(player);
                    LOGGER.debug(() -> "Updated command tree for " + player.getName());
                }
            });
            LOGGER.info("Set-up autocompletion refresh for LuckPerms");
        });
    }

    private void alterCommand(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
        LOGGER.debug("Alter command {}", name);
        CommandNode<ServerCommandSource> child = dispatcher.getRoot().getChild(name);
        try {
            Field field = CommandNode.class.getDeclaredField("requirement");
            field.setAccessible(true);
            Predicate<ServerCommandSource> original = child.getRequirement();
            field.set(child, (Predicate<ServerCommandSource>) (source) ->
                    Permissions.check(source, PREFIX + name, original.test(source))
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.warn("Failed to alter field CommandNode.requirement" + name, e);
        }
    }

    /**
     * List of commands built-in to vanilla Minecraft that will get permission checks, in alphabetical order.
     * Aliases are treated the same as normal commands and have separate permissions, for example
     * "minecraft.command.tp" is a separate permission from "minecraft.command.teleport".
     */
    private static final String[] VANILLA_COMMANDS = {
            "advancement",
            "attribute",
            "ban",
            "ban-ip",
            "banlist",
            "bossbar",
            "clear",
            "clone",
            "data",
            "datapack",
            "debug",
            "defaultgamemode",
            "deop",
            "difficulty",
            "effect",
            "enchant",
            "execute",
            "experience", // <- xp
            "fill",
            "forceload",
            "function",
            "gamemode",
            "gamerule",
            "give",
            "help",
            "item",
            "kick",
            "kill",
            "list",
            "locate",
            "locatebiome",
            "loot",
            "me",
            "msg", // <- tell, w
            "op",
            "pardon",
            "pardon-ip",
            "particle",
            "playsound",
            "recipe",
            "reload",
            "save-all",
            "save-off",
            "save-on",
            "say",
            "schedule",
            "scoreboard",
            "seed",
            "setblock",
            "setidletimeout",
            "setworldspawn",
            "spawnpoint",
            "spectate",
            "spreadplayers",
            "stop",
            "stopsound",
            "summon",
            "tag",
            "team",
            "teammsg", // <- tm
            "teleport", // <- tp
            "tell", // -> msg
            "tellraw",
            "time",
            "title",
            "tm", // -> teammsg
            "tp", // -> teleport (Sponge uses tp, while Bukkit uses teleport)
            "trigger",
            "w", // w -> msg
            "weather",
            "whitelist",
            "worldborder",
            "xp", // -> experience
    };
}
