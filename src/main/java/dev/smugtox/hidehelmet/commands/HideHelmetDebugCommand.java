package dev.smugtox.hidehelmet.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import java.util.Arrays;

public class HideHelmetDebugCommand extends CommandBase {

    public HideHelmetDebugCommand(String name, String description) {
        super(name, description);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        var sender = context.sender();
        if (!(sender instanceof Player player)) return;

        ItemContainer armor = player.getInventory().getArmor();

        // Imprime capacidad y luego cada slot con itemId
        player.sendMessage(Message.raw("Armor capacity: " + armor.getCapacity()));

        // forEachWithMeta(slot, itemStack, metaArrayOrSomething)
        String[] ids = new String[armor.getCapacity()];
        Arrays.fill(ids, "");

        armor.forEachWithMeta((slot, itemStack, arr) -> {
            String id = (itemStack != null) ? itemStack.getItemId() : "null";
            ids[slot] = id;
        }, ids);

        for (int i = 0; i < ids.length; i++) {
            player.sendMessage(Message.raw("Armor[" + i + "]: " + ids[i]));
        }
    }
}
