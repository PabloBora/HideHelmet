# HideHelmet / HideArmor v1.0.0

Self-only armor visibility toggles for Hytale servers.  
Lets each player hide or show their **own armor pieces only in their own view**, without changing inventory, stats, or what other players see.

---

## What it does

- Masks outgoing `EntityUpdates → Equipment.armorIds` **for the local player only**.
- Does **not** modify inventory, durability, stats, or actual equipped items.
- Does **not** affect how other players see you.

---

## Supported slots

- Head (helmet)
- Chest
- Hands
- Legs

> **Note:** `Hands` refers to the arms/gauntlets armor slot — **not** right/left hand tools.

---

## Commands

### `/hidehelmet`
Toggle **Head** slot only.

Output:
- `HideHelmet: ON`
- `HideHelmet: OFF`

### `/hidearmor`
| Usage | Description |
|-------|-------------|
| `/hidearmor` | Show help + current status |
| `/hidearmor status` | Show hidden slots |
| `/hidearmor <slot>` | Toggle slot (`head`, `chest`, `hands`, `legs`) |
| `/hidearmor on <slot>` | Force hide slot |
| `/hidearmor off <slot>` | Force show slot |
| `/hidearmor all` | Toggle all slots |
| `/hidearmor on all` | Hide all slots |
| `/hidearmor off all` | Show all slots |

**Examples:**
```
Hidden: Chest, Hands, Legs
HideArmor: Head, Legs
```

---

## Persistence

- Saves per-player state to `players.json` in the plugin data directory.
- Restores state on server restart.
- Debounced saves to reduce disk writes.

---

## Permissions / OP

- By default, **no permission checks** are enforced (any player can use it).
- If your server requires OP/admin-only usage, restrict commands via your server's permission system.

---

## Install

1. Download the `.jar` from the **Assets** section below.
2. Drop it into your server's plugins/mods folder.
3. Restart the server.  
   A `players.json` file will be created after the first save.

---

## Notes / Limitations

- **Self-only by design**: this is a client-view convenience feature, **not** a disguise or cosmetic system.
- Only armor visuals are modified; tools in hands are untouched.
