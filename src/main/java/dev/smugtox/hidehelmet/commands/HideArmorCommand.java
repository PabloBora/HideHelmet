package dev.smugtox.hidehelmet.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.smugtox.hidehelmet.HideArmorState;

import javax.annotation.Nonnull;
import java.util.Arrays;

public class HideArmorCommand extends CommandBase {

    public HideArmorCommand(String name, String description) {
        super(name, description);
        setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        var sender = context.sender();
        if (!(sender instanceof Player player)) return;

        String[] args = parseArgs(context, "hidearmor");
        if (args.length == 0) {
            sendHelp(player);
            sendStatus(player);
            return;
        }

        String first = args[0].toLowerCase();
        if ("status".equals(first)) {
            sendStatus(player);
            return;
        }

        if ("all".equals(first)) {
            int current = HideArmorState.getMask(player.getPlayerRef().getUuid());
            int newMask = HideArmorState.setAll(player.getPlayerRef().getUuid(), current != 15);
            player.sendMessage(Message.raw("HideArmor: " + HideArmorState.formatMask(newMask)));
            forceRefresh(player);
            return;
        }

        if ("on".equals(first) || "off".equals(first)) {
            if (args.length < 2) {
                sendHelp(player);
                return;
            }

            boolean enable = "on".equals(first);
            String target = args[1].toLowerCase();
            if ("all".equals(target)) {
                int newMask = HideArmorState.setAll(player.getPlayerRef().getUuid(), enable);
                player.sendMessage(Message.raw("HideArmor: " + HideArmorState.formatMask(newMask)));
                forceRefresh(player);
                return;
            }

            int slot = resolveSlot(target);
            if (slot < 0) {
                sendHelp(player);
                return;
            }

            int mask = HideArmorState.getMask(player.getPlayerRef().getUuid());
            int bit = 1 << slot;
            int newMask = enable ? (mask | bit) : (mask & ~bit);
            HideArmorState.setMask(player.getPlayerRef().getUuid(), newMask);
            player.sendMessage(Message.raw("HideArmor: " + HideArmorState.formatMask(newMask)));
            forceRefresh(player);
            return;
        }

        int slot = resolveSlot(first);
        if (slot >= 0) {
            int newMask = HideArmorState.toggleSlot(player.getPlayerRef().getUuid(), slot);
            player.sendMessage(Message.raw("HideArmor: " + HideArmorState.formatMask(newMask)));
            forceRefresh(player);
            return;
        }

        sendHelp(player);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Message.raw("Usage: /hidearmor [status|all|on|off] [head|chest|hands|legs]"));
    }

    private void sendStatus(Player player) {
        int mask = HideArmorState.getMask(player.getPlayerRef().getUuid());
        player.sendMessage(Message.raw("Hidden: " + HideArmorState.formatMask(mask)));
    }

    private int resolveSlot(String arg) {
        return switch (arg) {
            case "head" -> HideArmorState.SLOT_HEAD;
            case "chest" -> HideArmorState.SLOT_CHEST;
            case "hands" -> HideArmorState.SLOT_HANDS;
            case "legs" -> HideArmorState.SLOT_LEGS;
            default -> -1;
        };
    }

    private String[] parseArgs(CommandContext context, String commandName) {
        String input = context.getInputString();
        if (input == null) return new String[0];

        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new String[0];

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) return new String[0];

        String first = parts[0];
        if (first.startsWith("/")) first = first.substring(1);

        if (first.equalsIgnoreCase(commandName)) {
            return parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
        }

        return parts;
    }

    private void forceRefresh(Player player) {
        var world = player.getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                player.invalidateEquipmentNetwork();
            } catch (Throwable ignored) {
            }
        });
    }
}
