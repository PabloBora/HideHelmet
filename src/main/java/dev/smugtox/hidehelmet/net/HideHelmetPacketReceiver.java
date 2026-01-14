package dev.smugtox.hidehelmet.net;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.Equipment;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;

import dev.smugtox.hidehelmet.HideArmorState;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Intercepta packets saliendo hacia el cliente (viewer).
 * Solo modifica EntityUpdates -> Equipment del entity del propio jugador (selfNetworkId).
 */
public final class HideHelmetPacketReceiver implements IPacketReceiver {

    private final IPacketReceiver delegate;
    private final UUID viewerUuid;
    private final int selfNetworkId;

    public HideHelmetPacketReceiver(IPacketReceiver delegate, UUID viewerUuid, int selfNetworkId) {
        this.delegate = delegate;
        this.viewerUuid = viewerUuid;
        this.selfNetworkId = selfNetworkId;
    }

    @Override
    public void write(@Nonnull Packet packet) {
        delegate.write(maybeModify(packet));
    }

    @Override
    public void writeNoCache(@Nonnull Packet packet) {
        delegate.writeNoCache(maybeModify(packet));
    }

    private Packet maybeModify(Packet packet) {
        int mask = HideArmorState.getMask(viewerUuid);
        if (mask == 0) return packet;

        // Solo nos interesa EntityUpdates
        if (!(packet instanceof EntityUpdates eu)) return packet;

        if (eu.updates == null || eu.updates.length == 0) return packet;

        // Vamos a clonar solo si realmente necesitamos modificar algo
        boolean modified = false;

        EntityUpdate[] updatesCopy = null;

        for (int i = 0; i < eu.updates.length; i++) {
            EntityUpdate upd = eu.updates[i];
            if (upd == null) continue;

            // Solo el propio jugador
            if (upd.networkId != selfNetworkId) continue;

            if (upd.updates == null || upd.updates.length == 0) continue;

            EntityUpdate updCopy = null;

            for (int j = 0; j < upd.updates.length; j++) {
                ComponentUpdate cu = upd.updates[j];
                if (cu == null) continue;

                if (cu.type == ComponentUpdateType.Equipment && cu.equipment != null) {
                    String[] armorIds = cu.equipment.armorIds;
                    if (armorIds == null || armorIds.length == 0) continue;

                    boolean shouldHide = false;
                    int[] slots = new int[] {
                            HideArmorState.SLOT_HEAD,
                            HideArmorState.SLOT_CHEST,
                            HideArmorState.SLOT_HANDS,
                            HideArmorState.SLOT_LEGS
                    };

                    for (int slot : slots) {
                        if (!HideArmorState.isHidden(viewerUuid, slot)) continue;
                        if (slot >= 0 && slot < armorIds.length) {
                            shouldHide = true;
                            break;
                        }
                    }

                    if (!shouldHide) continue;
                    // Copias "lazy": clonamos hasta que realmente vamos a cambiar algo
                    if (!modified) {
                        updatesCopy = eu.updates.clone(); // shallow
                        modified = true;
                    }

                    if (updCopy == null) {
                        // clonamos el EntityUpdate una sola vez para no mutar el original
                        updCopy = new EntityUpdate();
                        updCopy.networkId = upd.networkId;
                        updCopy.removed = upd.removed; // no lo tocamos
                        updCopy.updates = upd.updates.clone(); // shallow de ComponentUpdate
                        updatesCopy[i] = updCopy;
                    }

                    // clonamos el ComponentUpdate actual
                    ComponentUpdate cuCopy = new ComponentUpdate();
                    cuCopy.type = cu.type;

                    // clonamos Equipment de forma segura
                    Equipment eqCopy = new Equipment();
                    eqCopy.rightHandItemId = cu.equipment.rightHandItemId;
                    eqCopy.leftHandItemId = cu.equipment.leftHandItemId;

                    eqCopy.armorIds = armorIds.clone();
                    for (int slot : slots) {
                        if (!HideArmorState.isHidden(viewerUuid, slot)) continue;
                        if (slot >= 0 && slot < eqCopy.armorIds.length) {
                            eqCopy.armorIds[slot] = "";
                        }
                    }

                    cuCopy.equipment = eqCopy;

                    // reinsertamos el cuCopy en la lista
                    updCopy.updates[j] = cuCopy;

                }
            }
        }

        if (!modified) return packet;

        EntityUpdates out = new EntityUpdates();
        out.removed = eu.removed;  // no lo tocamos
        out.updates = updatesCopy; // ya trae el self modificado
        return out;
    }
}
