package dev.smugtox.hidehelmet.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.smugtox.hidehelmet.HideArmorState;

import javax.annotation.Nonnull;

public class HideHelmetCommand extends CommandBase {

    public HideHelmetCommand(String name, String description) {
        super(name, description);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {

        var sender = context.sender();
        if (!(sender instanceof Player player)) {
            return;
        }

        HideArmorState.toggleSlot(player.getPlayerRef().getUuid(), HideArmorState.SLOT_HEAD);
        boolean enabled = HideArmorState.isHidden(player.getPlayerRef().getUuid(), HideArmorState.SLOT_HEAD);
        player.sendMessage(Message.raw(enabled ? "HideHelmet: ON" : "HideHelmet: OFF"));

        forceRefresh(player);
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
