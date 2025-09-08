# PrisonShakedown

An actually useful shakedown system for prison servers. Point it at a player or a region, and it will sweep the cell for contraband, clean it up, and handle rewards/punishments like a pro. Integrates directly with AdvancedRegionMarket (ARM), plays nice with WorldGuard, and speaks MiniMessage.

## Highlights
- ARM-native: resolves the real ARM regions (owned, rented, or on the member list) – no guesswork
- Clean sweep: scans crops and container inventories for contraband and removes them
- Fair play: cooldowns so players aren’t shaken down every 3 minutes
- Guard access: temporarily adds a configurable group to the region during the search
- Reward/punish hooks: run your own console commands with placeholders
- Adventure/MiniMessage output that doesn’t look like it’s from 2012
- Reload command for quick config tweaks
- Structured, colored debug logging you can toggle in `config.yml`

## Requirements
- Paper 1.21.8+
- WorldGuard 7.0.9+
- AdvancedRegionMarket 3.5.5 (hard dependency)
- Optional: LuckPerms (for your guard group), your economy plugin of choice (Essentials, CMI, CoinsEngine, TokenManager, etc.), Citizens (soft)

## Install
1. Drop this jar into your server’s `plugins/` folder.
2. Ensure the required dependencies are installed (see above).
3. Start the server once to generate `config.yml`.
4. Adjust the config to match your contraband, rewards, punishments, and guard group.
5. Use `/shakedown reload` to apply changes quickly.

## Commands
- `/shakedown <regionId|player|reload>`
  - `player`: runs a shakedown on the first ARM region that player owns, rents, or is a member of.
  - `regionId`: runs a shakedown on a specific ARM region; maps to the owner/renter/member and requires them online.
  - `reload`: reloads the plugin’s configuration.

## Permissions
- `shakedown.use` – use the command
- `shakedown.admin` – bypass cooldown and use `/shakedown reload`

## Configuration (snippet)
```yaml
contraband:
  materials:
    - SUGAR_CANE
    - BAMBOO
    - CACTUS
  custom-model-data: []

rewards:
  success:
    commands:
      # Pick the command(s) that match your economy plugin. Examples:
      # - "eco give {guard} 10"          # EssentialsX Economy
      # - "cmi money give {guard} 10"    # CMI Economy
      # - "et give {guard} 10"           # CoinsEngine
      # - "tokens add {guard} 10"        # TokenManager
      - "eco give {guard} 10"

punishments:
  contraband-found:
    commands:
      - "jail {player} 5m"
  no-contraband:
    commands:
      - "message {guard} Thanks for keeping the prison clean!"

shakedown:
  cooldown: 48h
  region-group: guards   # temporarily granted build/container access on the region

# Debug logging options
debug:
  enabled: false         # master switch for debug output
  verbose: false         # extra detail (e.g., stack traces)
  console: true          # also mirror to console logger
```

### Placeholders you can use in commands
- `{player}` – the prisoner / region’s primary user
- `{guard}` – the staff member running the shakedown

## How it works (short version)
- When you run `/shakedown playerName`, the plugin resolves the player’s ARM region (owner → renter → first member). For `/shakedown regionId`, it finds the ARM region across worlds and resolves the primary user the same way.
- The guard’s `shakedown.region-group` is temporarily added to the WorldGuard region.
- The region is scanned: crops that are contraband are removed, containers are cleaned of contraband items.
- Results are reported, configured punishment/reward commands run, and cooldown is recorded.
- The guard group is removed from the region, even if errors occur.

## AdvancedRegionMarket integration
- Uses ARM’s API directly: `AdvancedRegionMarket.getInstance().getRegionManager()`
- Finds regions by exact `regionId` + world name and by owner UUID
- Includes rented and member regions for convenience (so staff don’t have to ask players if they bought vs rented)

## Tips
- Make your region IDs predictable (helps staff when targeting by ID)
- Keep your contraband list focused – fewer false positives, happier players
- Consider a separate guard permission group with limited build/container access for searches

## Troubleshooting
- “No ARM region owned by player”
  - Make sure the player actually owns, rents, or is a member of an ARM region
  - Confirm the region ID spelling and world name (if using the regionId path)
  - Enable debug logging and check console for ARM lookups
- “Nothing gets removed”
  - Are your contraband materials/custom-model-data configured correctly?
  - Is your region huge? Try a test in a small region to verify logic first

## FAQ
- Does this support non-ARM regions?
  - No. The plugin is designed specifically around ARM-managed regions, so ownership and targeting make sense.
- How do I pay guards / hook into my economy?
  - Use console commands in `rewards.success.commands` (and/or in punishments). Works with any plugin that exposes commands (Essentials `/eco`, CMI `/cmi money`, CoinsEngine `/et`, TokenManager `/tokens`, etc.).
- Can I hot-reload?
  - Yep: `/shakedown reload` refreshes the config and in-memory handlers.

## Credits
- Author: Nenf
- ARM by alex9849, WorldGuard by EngineHub, Paper by the Paper team

*Psy4SrWarden*
