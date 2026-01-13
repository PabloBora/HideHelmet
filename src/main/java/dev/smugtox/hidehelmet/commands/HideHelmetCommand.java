package dev.smugtox.hidehelmet.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Set;

public class HideHelmetCommand extends CommandBase {

    private final Set<Object> enabledPlayers;
    private final ToggleCallback callback;

    public interface ToggleCallback {
        void onToggle(@Nonnull CommandContext ctx, boolean enabled);
    }

    public HideHelmetCommand(String name, String description, Set<Object> enabledPlayers, ToggleCallback callback) {
        super(name, description);
        this.enabledPlayers = enabledPlayers;
        this.callback = callback;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        var player = context.senderAsPlayer();
        Object ref = player.getReference();

        boolean enabled;
        if (enabledPlayers.contains(ref)) {
            enabledPlayers.remove(ref);
            enabled = false;
        } else {
            enabledPlayers.add(ref);
            enabled = true;
        }

        // Feedback como en el template (Message.raw + sendMessage)
        player.sendMessage(Message.raw(enabled ? "HideHelmet: ON" : "HideHelmet: OFF"));

        if (callback != null) callback.onToggle(context, enabled);
    }
}
