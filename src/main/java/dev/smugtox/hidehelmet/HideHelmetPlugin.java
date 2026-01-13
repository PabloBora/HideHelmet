package dev.smugtox.hidehelmet;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.smugtox.hidehelmet.commands.HideHelmetCommand;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HideHelmetPlugin extends JavaPlugin {

    private final Set<Object> hideHelmetEnabled = ConcurrentHashMap.newKeySet();

    public HideHelmetPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        this.getCommandRegistry().registerCommand(
                new HideHelmetCommand(
                        "hidehelmet",
                        "Toggle helmet visibility",
                        hideHelmetEnabled,
                        (ctx, enabled) -> {
                            var player = ctx.senderAsPlayer();
                            player.getWorld().execute(() -> {
                                // applyHelmetVisibility(player, enabled); // lo implementamos cuando encontremos el API
                            });
                        }
                )
        );

        // Reaplicar al entrar al mundo (si lo tenía activado)
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, (event) -> {
            var player = event.getPlayer();
            boolean enabled = hideHelmetEnabled.contains(player.getReference());
            player.getWorld().execute(() -> {
                // applyHelmetVisibility(player, enabled);
            });
        });
    }
}
