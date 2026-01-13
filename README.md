# HideHelmet

Self-only armor visibility toggles for Hytale servers. Each player can hide or show their own armor pieces without affecting inventory or what other players see.

Features
- Self-only visuals (no inventory changes, no impact on other players).
- Per-slot toggles: Head, Chest, Hands, Legs.
- Global toggle for all armor slots.
- Persistent state per player (saved to disk).

Commands
- /hidehelmet
  - Toggle head slot only.
  - Output: `HideHelmet: ON` or `HideHelmet: OFF`.
- /hidearmor
  - No args: show help + current status.
  - `status`: show current hidden slots.
  - `head|chest|hands|legs`: toggle a single slot.
  - `on head|chest|hands|legs`: force slot hidden.
  - `off head|chest|hands|legs`: force slot visible.
  - `all`: toggle all slots (hide all if any are visible; show all if all are hidden).
  - `on all`: hide all slots.
  - `off all`: show all slots.
  - Output examples:
    - `Hidden: Chest, Hands, Legs`
    - `HideArmor: Head, Legs`

Behavior and safety
- Self-only: only the player running the command sees their armor hidden.
- No permission checks by default (open access).
- Only equipment visuals are modified; inventory and hands are untouched.

Persistence
- Saved to `players.json` in the plugin data directory.
- Debounced saves to reduce disk writes.
- State is restored on server restart.

Deploy / install
1) Build the plugin jar.
2) Drop the jar into your server's mods/plugins folder.
3) Start the server to generate `players.json`.

Notes
- This mod is designed for player-side visuals only.
