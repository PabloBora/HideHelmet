package dev.smugtox.hidehelmet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HideArmorState {

    public static final int SLOT_HEAD = 0;
    public static final int SLOT_CHEST = 1;
    public static final int SLOT_HANDS = 2;
    public static final int SLOT_LEGS = 3;

    private static final ConcurrentHashMap<UUID, Integer> MASKS = new ConcurrentHashMap<>();
    private static volatile Runnable onChange;

    private HideArmorState() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static int getMask(UUID uuid) {
        Integer mask = MASKS.get(uuid);
        return mask == null ? 0 : mask;
    }

    public static void setMask(UUID uuid, int mask) {
        setMaskInternal(uuid, mask, true);
    }

    public static void setMaskSilently(UUID uuid, int mask) {
        setMaskInternal(uuid, mask, false);
    }

    public static boolean isHidden(UUID uuid, int slot) {
        int mask = getMask(uuid);
        return (mask & (1 << slot)) != 0;
    }

    public static int toggleSlot(UUID uuid, int slot) {
        int mask = getMask(uuid);
        int newMask = mask ^ (1 << slot);
        setMask(uuid, newMask);
        return newMask;
    }

    public static int setAll(UUID uuid, boolean hide) {
        int newMask = hide ? 15 : 0;
        setMask(uuid, newMask);
        return newMask;
    }

    public static Map<UUID, Integer> snapshot() {
        return new HashMap<>(MASKS);
    }

    public static String formatMask(int mask) {
        if (mask == 0) return "none";

        StringBuilder out = new StringBuilder();
        appendIfSet(out, mask, SLOT_HEAD, "Head");
        appendIfSet(out, mask, SLOT_CHEST, "Chest");
        appendIfSet(out, mask, SLOT_HANDS, "Hands");
        appendIfSet(out, mask, SLOT_LEGS, "Legs");
        return out.toString();
    }

    private static void setMaskInternal(UUID uuid, int mask, boolean notify) {
        int clamped = Math.max(0, Math.min(15, mask));
        if (clamped == 0) {
            MASKS.remove(uuid);
        } else {
            MASKS.put(uuid, clamped);
        }

        if (notify) {
            Runnable callback = onChange;
            if (callback != null) callback.run();
        }
    }

    private static void appendIfSet(StringBuilder out, int mask, int slot, String label) {
        if ((mask & (1 << slot)) == 0) return;
        if (out.length() > 0) out.append(", ");
        out.append(label);
    }
}
