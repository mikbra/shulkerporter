"""Generates the per-colour shulker box overrides. Run from the project root:

    python3 tools/generate_shulker_overrides.py

- Datapack loot tables (src/main/resources/datapack): a broken box keeps the Shulkerporter /
  Shulkerpopper components, and its block data (marker, filter mode) is copied onto the item.
- Resource pack item definitions (resourcepack/assets/minecraft/items): Shulkerporters and
  Shulkerpoppers get our models; every other shulker box keeps its vanilla look.
"""
import json
from pathlib import Path

COLOURS = ["", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
           "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
POPPER_MODES = ["type", "exact"]

LOOT_DIR = Path("src/main/resources/datapack/data/minecraft/loot_table/blocks")
ITEMS_DIR = Path("resourcepack/assets/minecraft/items")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def loot_table(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "entries": [{
                "type": "minecraft:item",
                "modifier": [
                    {
                        "type": "minecraft:copy_components",
                        "include": [
                            "minecraft:custom_name",
                            "minecraft:container",
                            "minecraft:lock",
                            "minecraft:container_loot",
                            "minecraft:custom_data",
                            "minecraft:custom_model_data",
                            "minecraft:item_name",
                            "minecraft:lore",
                            "minecraft:enchantment_glint_override",
                        ],
                        "source": "block_entity",
                    },
                    {
                        # The plugin's block data, which /shulkerpopper mode may have changed
                        # since the box was placed.
                        "type": "minecraft:copy_custom_data",
                        "source": "block_entity",
                        "ops": [{"source": "PublicBukkitValues", "target": "PublicBukkitValues", "op": "merge"}],
                    },
                ],
                "name": f"minecraft:{name}",
            }],
            "rolls": 1,
        }],
        "random_sequence": f"minecraft:blocks/{name}",
    }


def model(name):
    return {"type": "minecraft:model", "model": f"shulkerporter:block/{name}"}


def item_definition(colour, name):
    vanilla = {
        "type": "minecraft:special",
        "base": f"minecraft:item/{name}",
        "model": {"type": "minecraft:shulker_box", "texture": "minecraft:shulker" + (f"_{colour}" if colour else "")},
        "transformation": {
            "left_rotation": [1.0, 0.0, 0.0, 0.0],
            "right_rotation": [-0.0, -0.0, -0.0, 1.0],
            "scale": [0.9995, 0.9995, 0.9995],
            "translation": [0.5, 1.4995, 0.5],
        },
    }
    cases = [{"when": f"shulkerpopper_{mode}", "model": {"type": "minecraft:composite", "models": [
        model("popper_base"), model(f"popper_lid_{mode}"), model(f"popper_intake_{mode}")]}} for mode in POPPER_MODES]
    if colour == "black":
        cases.insert(0, {"when": "shulkerporter", "model": {"type": "minecraft:composite", "models": [
            model("porter_base"), model("porter_lid")]}})
    return {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                      "cases": cases, "fallback": vanilla}}


for colour in COLOURS:
    name = f"{colour}_shulker_box" if colour else "shulker_box"
    write(LOOT_DIR / f"{name}.json", loot_table(name))
    write(ITEMS_DIR / f"{name}.json", item_definition(colour, name))
print(f"Wrote {len(COLOURS)} loot tables and item definitions")
