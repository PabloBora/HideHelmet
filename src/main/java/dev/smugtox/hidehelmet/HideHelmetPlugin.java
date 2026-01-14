package dev.smugtox.hidehelmet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerCraftEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import dev.smugtox.hidehelmet.commands.HideArmorCommand;
import dev.smugtox.hidehelmet.commands.HideHelmetCommand;
import dev.smugtox.hidehelmet.commands.HideHelmetDebugCommand;
import dev.smugtox.hidehelmet.net.HideHelmetPacketReceiver;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HideHelmetPlugin extends JavaPlugin {

    private static final int MAX_MASK = 15;
    private static final long DEFAULT_INVALIDATE_COOLDOWN_MS = 150;
    private static final boolean DEFAULT_PICKUP_IMMEDIATE = true;

    private final Object saveLock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private File dataFile;
    private ScheduledExecutorService saveExecutor;
    private ScheduledExecutorService invalidateExecutor;
    private ScheduledFuture<?> pendingSave;
    private boolean dirty;
    private long invalidateCooldownMs = DEFAULT_INVALIDATE_COOLDOWN_MS;
    private boolean pickupImmediate = DEFAULT_PICKUP_IMMEDIATE;
    private final Map<UUID, Long> lastInvalidateByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingInvalidates = new ConcurrentHashMap<>();

    public HideHelmetPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        initDataFile();
        int loadedCount = loadStateFromDisk();
        this.getLogger().at(Level.INFO).log("HideHelmet enabled (self-only). Loaded " + loadedCount + " players.");
        HideArmorState.setOnChange(this::markDirtyAndScheduleSave);
        initInvalidateScheduler();

        // Commands
        this.getCommandRegistry().registerCommand(
                new HideHelmetCommand("hidehelmet", "Toggle helmet visibility")
        );

        this.getCommandRegistry().registerCommand(
                new HideArmorCommand("hidearmor", "Toggle armor visibility")
        );

        this.getCommandRegistry().registerCommand(
                new HideHelmetDebugCommand("hhdebug", "Print armor slot indices")
        );

        // Install packet wrapper per player when they are ready
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, (event) -> {
            Player player = event.getPlayer();
            var world = player.getWorld();
            if (world == null) return;

            world.execute(() -> {
                try {
                    // Obtener viewer component y envolver su packetReceiver
                    var store = world.getEntityStore().getStore();
                    var ref = player.getReference();

                    EntityViewer viewer = store.getComponent(ref, EntityViewer.getComponentType());
                    if (viewer == null || viewer.packetReceiver == null) return;

                    // Evitar doble wrap
                    if (!(viewer.packetReceiver instanceof HideHelmetPacketReceiver)) {
                        viewer.packetReceiver = new HideHelmetPacketReceiver(
                                viewer.packetReceiver,
                                player.getPlayerRef().getUuid(),
                                player.getNetworkId()
                        );
                    }

                    if (HideArmorState.getMask(player.getPlayerRef().getUuid()) != 0) {
                        try {
                            player.invalidateEquipmentNetwork();
                        } catch (Throwable ignored) {}
                    }

                } catch (Throwable t) {
                    // Si algo cambia en el SDK, evitamos crashear el server
                }
            });
        });

        // Fallback: re-aplicar hide cuando cambie inventario (el cliente rehidrata armadura).
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, (event) -> {
            if (!(event.getEntity() instanceof Player player)) return;
            requestEquipmentInvalidate(player, false);
        });

        // Acciones rápidas (clicks, ataque, romper bloques) pueden re-sincronizar visual del equipo.
        this.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, (event) -> {
            Player player = event.getPlayer();
            requestEquipmentInvalidate(player, false);
        });

        this.getEventRegistry().registerGlobal(PlayerCraftEvent.class, (event) -> {
            Player player = event.getPlayer();
            requestEquipmentInvalidate(player, false);
        });

        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, (event) -> {
            Player player = event.getPlayer();
            if (player == null) return;
            if (HideArmorState.getMask(player.getPlayerRef().getUuid()) == 0) return;

            InteractionType type = event.getActionType();
            if (type == null) return;

            if (type == InteractionType.Pickup) {
                requestEquipmentInvalidate(player, pickupImmediate);
            } else if (type == InteractionType.Primary
                    || type == InteractionType.Secondary
                    || type == InteractionType.Use
                    || type == InteractionType.Pick
                    || type == InteractionType.SwapTo
                    || type == InteractionType.SwapFrom
                    || type == InteractionType.Wielding
                    || type == InteractionType.Equipped) {
                requestEquipmentInvalidate(player, false);
            }
        });
    }

    @Override
    protected void shutdown() {
        int savedCount = saveStateToDisk();
        this.getLogger().at(Level.INFO).log("HideHelmet disabled. Saved " + savedCount + " players.");
        if (saveExecutor != null) {
            saveExecutor.shutdownNow();
        }
        if (invalidateExecutor != null) {
            invalidateExecutor.shutdownNow();
        }
    }

    private void initDataFile() {
        Path dataDir = getDataDirectory();
        if (dataDir == null) return;

        File dir = dataDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        dataFile = new File(dir, "players.json");
        if (!dataFile.exists()) {
            try {
                String seed = "{\"players\":{},\"config\":{\"invalidateCooldownMs\":"
                        + DEFAULT_INVALIDATE_COOLDOWN_MS
                        + ",\"pickupImmediate\":"
                        + DEFAULT_PICKUP_IMMEDIATE
                        + "}}";
                Files.writeString(dataFile.toPath(), seed, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("HideHelmet: Failed to create players.json: " + e.getMessage());
            }
        }

        saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HideHelmet-Save");
            t.setDaemon(true);
            return t;
        });
    }

    private void initInvalidateScheduler() {
        invalidateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HideHelmet-Invalidate");
            t.setDaemon(true);
            return t;
        });
    }

    private void markDirtyAndScheduleSave() {
        dirty = true;
        if (saveExecutor == null) return;

        synchronized (saveLock) {
            if (pendingSave == null || pendingSave.isDone()) {
                pendingSave = saveExecutor.schedule(this::saveStateToDisk, 1500, TimeUnit.MILLISECONDS);
            }
        }
    }

    private int loadStateFromDisk() {
        if (dataFile == null || !dataFile.exists()) return 0;

        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            SaveModel model = gson.fromJson(json, SaveModel.class);
            if (model == null) return 0;
            if (model.config != null) {
                if (model.config.invalidateCooldownMs != null && model.config.invalidateCooldownMs > 0) {
                    invalidateCooldownMs = model.config.invalidateCooldownMs;
                }
                if (model.config.pickupImmediate != null) {
                    pickupImmediate = model.config.pickupImmediate;
                }
            }
            if (model.players == null) return 0;

            int loaded = 0;
            for (Map.Entry<String, Integer> entry : model.players.entrySet()) {
                Integer mask = entry.getValue();
                if (mask == null) continue;

                int clamped = Math.max(0, Math.min(MAX_MASK, mask));
                if (clamped == 0) continue;

                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    HideArmorState.setMaskSilently(uuid, clamped);
                    loaded++;
                } catch (IllegalArgumentException ignored) {
                }
            }
            return loaded;
        } catch (Exception e) {
            System.err.println("HideHelmet: Failed to load state: " + e.getMessage());
            return 0;
        }
    }

    private int saveStateToDisk() {
        if (dataFile == null) return 0;

        synchronized (saveLock) {
            if (!dirty) return 0;
            dirty = false;
        }

        try {
            Map<UUID, Integer> snapshot = HideArmorState.snapshot();
            Map<String, Integer> out = new HashMap<>();

            for (Map.Entry<UUID, Integer> entry : snapshot.entrySet()) {
                Integer mask = entry.getValue();
                if (mask == null) continue;
                int clamped = Math.max(0, Math.min(MAX_MASK, mask));
                if (clamped == 0) continue;
                out.put(entry.getKey().toString(), clamped);
            }

            SaveModel model = new SaveModel();
            model.players = out;
            SaveConfig config = new SaveConfig();
            config.invalidateCooldownMs = invalidateCooldownMs;
            config.pickupImmediate = pickupImmediate;
            model.config = config;

            String json = gson.toJson(model);
            Files.writeString(dataFile.toPath(), json, StandardCharsets.UTF_8);
            return out.size();
        } catch (Exception e) {
            synchronized (saveLock) {
                dirty = true;
            }
            System.err.println("HideHelmet: Failed to save state: " + e.getMessage());
            return 0;
        }
    }

    private static final class SaveModel {
        Map<String, Integer> players = new HashMap<>();
        SaveConfig config;
    }

    private static final class SaveConfig {
        Long invalidateCooldownMs;
        Boolean pickupImmediate;
    }

    private void requestEquipmentInvalidate(Player player, boolean immediate) {
        if (player == null) return;
        if (HideArmorState.getMask(player.getPlayerRef().getUuid()) == 0) return;

        long now = System.currentTimeMillis();
        if (immediate) {
            cancelPendingInvalidate(player.getPlayerRef().getUuid());
            lastInvalidateByPlayer.put(player.getPlayerRef().getUuid(), now);
            executeInvalidate(player);
            return;
        }
        Long last = lastInvalidateByPlayer.get(player.getPlayerRef().getUuid());
        if (last == null || (now - last) >= invalidateCooldownMs) {
            lastInvalidateByPlayer.put(player.getPlayerRef().getUuid(), now);
            executeInvalidate(player);
            return;
        }

        long delay = invalidateCooldownMs - (now - last);
        scheduleDeferredInvalidate(player, delay);
    }

    private void scheduleDeferredInvalidate(Player player, long delayMs) {
        if (invalidateExecutor == null) return;
        UUID uuid = player.getPlayerRef().getUuid();
        pendingInvalidates.compute(uuid, (key, existing) -> {
            if (existing != null && !existing.isDone()) return existing;
            return invalidateExecutor.schedule(() -> {
                try {
                    executeInvalidate(player);
                } finally {
                    pendingInvalidates.remove(key);
                    lastInvalidateByPlayer.put(key, System.currentTimeMillis());
                }
            }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        });
    }

    private void cancelPendingInvalidate(UUID uuid) {
        ScheduledFuture<?> pending = pendingInvalidates.remove(uuid);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void executeInvalidate(Player player) {
        var world = player.getWorld();
        if (world == null) return;
        world.execute(() -> {
            try {
                player.invalidateEquipmentNetwork();
            } catch (Throwable ignored) {}
        });
    }
}
