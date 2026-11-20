# Auto Pickaxe Swapper (Fabric, Minecraft 26.1.2, client-side)

When a pickaxe in your hotbar breaks, the mod opens your inventory, moves the best replacement
pickaxe into the empty slot, checks it worked, and closes the inventory.
Diamond is preferred first, then netherite, iron, copper, stone, golden, wooden (editable in the config file).

If no pickaxe is left anywhere in your inventory it presses your chosen key, then posts to a Discord webhook.

## Controls (Options > Controls > "Auto Pickaxe Swapper")
| Setting | Default |
|---|---|
| Toggle Auto Pickaxe Swapper | K |
| Open Auto Pickaxe settings | O |
| Key to press when out of pickaxes | unbound - pick it here |

## Build the .jar with GitHub
1. Create a new GitHub repo and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> **build** -> wait for the green check (or **Run workflow**).
3. Open the finished run -> **Artifacts** -> download **AutoPickaxeSwapper-jar** -> unzip -> put `AutoPickaxeSwapper-1.0.0.jar` in `.minecraft/mods` with Fabric API.

Config file: `.minecraft/config/autopickaxe.json` (contains your webhook URL - never commit it).
