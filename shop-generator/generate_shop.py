"""
Génère les 4 catégories EconomyShopGUI utilisées par les marchands MerchantTalk.

    python generate_shop.py <dossier_de_sortie>

Crée <sortie>/sections/*.yml et <sortie>/shops/*.yml.
Prix « économie survie » : 1 diamant = 100 $. Revente = 25 % du prix d'achat
(empêche de gagner de l'argent en achetant puis en craftant/revendant).
"""
import os
import sys

SELL_RATIO = 0.25

CATEGORIES = [
    {
        "name": "Minerais",
        "title": "&8» &b&lMinerais &8«",
        "icon": "DIAMOND",
        "items": [
            ("COAL", 4), ("CHARCOAL", 4), ("RAW_COPPER", 6), ("COPPER_INGOT", 7),
            ("RAW_IRON", 18), ("IRON_INGOT", 20), ("RAW_GOLD", 28), ("GOLD_INGOT", 30),
            ("REDSTONE", 3), ("LAPIS_LAZULI", 6), ("QUARTZ", 5), ("AMETHYST_SHARD", 8),
            ("GLOWSTONE_DUST", 3), ("FLINT", 2), ("DIAMOND", 100), ("EMERALD", 60),
            ("NETHERITE_SCRAP", 600), ("NETHERITE_INGOT", 2600),
            ("COAL_BLOCK", 36), ("COPPER_BLOCK", 63), ("IRON_BLOCK", 180), ("GOLD_BLOCK", 270),
            ("REDSTONE_BLOCK", 27), ("LAPIS_BLOCK", 54), ("QUARTZ_BLOCK", 20),
            ("DIAMOND_BLOCK", 900), ("EMERALD_BLOCK", 540), ("OBSIDIAN", 15),
        ],
    },
    {
        "name": "Construction",
        "title": "&8» &6&lBlocs de construction &8«",
        "icon": "BRICKS",
        "items": [
            # Pierres
            ("STONE", 0.5), ("COBBLESTONE", 0.3), ("STONE_BRICKS", 0.8), ("SMOOTH_STONE", 0.8),
            ("DEEPSLATE", 0.5), ("COBBLED_DEEPSLATE", 0.4), ("DEEPSLATE_BRICKS", 1), ("TUFF", 0.5),
            ("GRANITE", 0.5), ("DIORITE", 0.5), ("ANDESITE", 0.5), ("CALCITE", 0.8),
            ("POLISHED_GRANITE", 0.7), ("POLISHED_DIORITE", 0.7), ("POLISHED_ANDESITE", 0.7), ("BLACKSTONE", 0.6),
            # Terre, sable, divers
            ("DIRT", 0.2), ("GRASS_BLOCK", 1), ("SAND", 0.4), ("RED_SAND", 0.5),
            ("GRAVEL", 0.4), ("SANDSTONE", 1), ("CLAY", 4), ("BRICKS", 3),
            ("MUD_BRICKS", 1.5), ("GLASS", 1.5), ("NETHERRACK", 0.2), ("NETHER_BRICKS", 2),
            ("END_STONE", 1), ("PURPUR_BLOCK", 2), ("PRISMARINE", 3), ("TERRACOTTA", 1),
            # Bois
            ("OAK_LOG", 2), ("SPRUCE_LOG", 2), ("BIRCH_LOG", 2), ("JUNGLE_LOG", 2),
            ("ACACIA_LOG", 2), ("DARK_OAK_LOG", 2), ("MANGROVE_LOG", 2), ("CHERRY_LOG", 2),
            ("PALE_OAK_LOG", 2), ("CRIMSON_STEM", 2), ("WARPED_STEM", 2),
            ("OAK_PLANKS", 0.6), ("SPRUCE_PLANKS", 0.6), ("BIRCH_PLANKS", 0.6), ("JUNGLE_PLANKS", 0.6),
            ("ACACIA_PLANKS", 0.6), ("DARK_OAK_PLANKS", 0.6), ("MANGROVE_PLANKS", 0.6), ("CHERRY_PLANKS", 0.6),
            ("PALE_OAK_PLANKS", 0.6), ("CRIMSON_PLANKS", 0.6), ("WARPED_PLANKS", 0.6),
        ] + [(f"{c}_WOOL", 2) for c in (
            "WHITE", "LIGHT_GRAY", "GRAY", "BLACK", "BROWN", "RED", "ORANGE", "YELLOW",
            "LIME", "GREEN", "CYAN", "LIGHT_BLUE", "BLUE", "PURPLE", "MAGENTA", "PINK")]
          + [(f"{c}_CONCRETE", 1.5) for c in (
            "WHITE", "LIGHT_GRAY", "GRAY", "BLACK", "BROWN", "RED", "ORANGE", "YELLOW",
            "LIME", "GREEN", "CYAN", "LIGHT_BLUE", "BLUE", "PURPLE", "MAGENTA", "PINK")],
    },
    {
        "name": "Nourriture",
        "title": "&8» &a&lNourriture & ferme &8«",
        "icon": "BREAD",
        "items": [
            # Plats
            ("BREAD", 3), ("BAKED_POTATO", 3), ("COOKED_BEEF", 5), ("COOKED_PORKCHOP", 5),
            ("COOKED_CHICKEN", 4), ("COOKED_MUTTON", 4), ("COOKED_COD", 3), ("COOKED_SALMON", 4),
            ("APPLE", 2), ("PUMPKIN_PIE", 5), ("COOKIE", 1), ("CAKE", 15),
            ("MUSHROOM_STEW", 4), ("GOLDEN_CARROT", 10), ("GOLDEN_APPLE", 150), ("SWEET_BERRIES", 1),
            # Viandes crues
            ("BEEF", 3), ("PORKCHOP", 3), ("CHICKEN", 2), ("MUTTON", 2), ("COD", 2), ("SALMON", 2),
            # Récoltes et graines
            ("WHEAT", 1), ("CARROT", 1), ("POTATO", 1), ("BEETROOT", 1),
            ("WHEAT_SEEDS", 0.3), ("BEETROOT_SEEDS", 0.3), ("PUMPKIN_SEEDS", 0.5), ("MELON_SEEDS", 0.5),
            ("PUMPKIN", 2), ("MELON", 3), ("MELON_SLICE", 0.5), ("SUGAR_CANE", 1),
            ("CACTUS", 1), ("BAMBOO", 0.3), ("COCOA_BEANS", 1), ("HAY_BLOCK", 9),
            # Produits d'élevage
            ("EGG", 1), ("MILK_BUCKET", 25), ("LEATHER", 4), ("FEATHER", 1), ("STRING", 1), ("BONE_MEAL", 0.5),
            # Pousses d'arbres
            ("OAK_SAPLING", 2), ("SPRUCE_SAPLING", 2), ("BIRCH_SAPLING", 2), ("JUNGLE_SAPLING", 2),
            ("ACACIA_SAPLING", 2), ("DARK_OAK_SAPLING", 2), ("CHERRY_SAPLING", 2), ("PALE_OAK_SAPLING", 2),
        ],
    },
    {
        "name": "Utilitaires",
        "title": "&8» &e&lUtilitaires &8«",
        "icon": "LANTERN",
        "items": [
            # Base
            ("TORCH", 0.5), ("LANTERN", 4), ("LADDER", 1), ("SCAFFOLDING", 1),
            ("CRAFTING_TABLE", 2), ("CHEST", 4), ("BARREL", 4), ("FURNACE", 3),
            ("SMOKER", 10), ("BLAST_FURNACE", 50), ("CAMPFIRE", 6), ("WHITE_BED", 8),
            ("BOOK", 4), ("PAPER", 1), ("BOOKSHELF", 12), ("ENCHANTING_TABLE", 300),
            ("ANVIL", 500), ("ENDER_CHEST", 250), ("EXPERIENCE_BOTTLE", 10), ("ENDER_PEARL", 15),
            # Outils
            ("IRON_PICKAXE", 65), ("IRON_AXE", 65), ("IRON_SHOVEL", 25), ("DIAMOND_PICKAXE", 320),
            ("SHEARS", 40), ("FLINT_AND_STEEL", 22), ("FISHING_ROD", 8), ("BUCKET", 60),
            ("WATER_BUCKET", 62), ("LAVA_BUCKET", 70), ("COMPASS", 90), ("CLOCK", 130),
            ("SPYGLASS", 30), ("NAME_TAG", 50), ("LEAD", 10), ("SADDLE", 80),
            # Combat
            ("BOW", 15), ("ARROW", 0.5), ("SHIELD", 25),
            # Transport et redstone
            ("RAIL", 3), ("POWERED_RAIL", 35), ("MINECART", 100), ("OAK_BOAT", 3),
            ("HOPPER", 110), ("PISTON", 25), ("STICKY_PISTON", 35), ("OBSERVER", 15),
            ("REPEATER", 5), ("COMPARATOR", 12), ("LEVER", 1), ("REDSTONE_TORCH", 2),
        ],
    },
]

PER_PAGE = 28  # 4 lignes de 7 cases (bordure vitrée autour)
LAYOUT = [
    "0 0 0 0 0 0 0 0 0",
    "0 1 1 1 1 1 1 1 0",
    "0 1 1 1 1 1 1 1 0",
    "0 1 1 1 1 1 1 1 0",
    "0 1 1 1 1 1 1 1 0",
    "0 0 0 0 0 0 0 0 0",  # ligne de la barre de navigation
]


def fmt(v):
    v = round(v, 2)
    return str(int(v)) if v == int(v) else f"{v:.2f}".rstrip("0")


def section_yml(cat, slot):
    lines = [
        "# Catégorie générée pour MerchantTalk : ouverte uniquement par son marchand.",
        "enable: true",
        f"title: '{cat['title']}'",
        f"slot: {slot}",
        "# Cachée du menu principal : seul le PNJ y donne accès.",
        "hidden: true",
        "item:",
        f"  displayname: '{cat['title']}'",
        f"  material: {cat['icon']}",
        "fill-item:",
        "  material: GRAY_STAINED_GLASS_PANE",
        "  displayname: ' '",
        "item-layout:",
    ]
    lines += [f"  - '{row}'" for row in LAYOUT]
    lines += [
        "# Barre du bas : pages précédente/suivante et bouton fermer (pas de retour au menu principal).",
        "nav-bar:",
        "  mode: SELF",
        "  fill-item:",
        "    material: GRAY_STAINED_GLASS_PANE",
        "    displayname: ' '",
        "  items:",
        "    previous:",
        "      material: RED_STAINED_GLASS_PANE",
        "      name: '%translations-previous-page%'",
        "      action: PAGE_BACK",
        "      paginated:",
        "        enabled: true",
        "        orElse:",
        "          material: GRAY_STAINED_GLASS_PANE",
        "          name: ' '",
        "      slot: 4",
        "    page:",
        "      material: NETHER_STAR",
        "      name: '%translations-current-page%'",
        "      slot: 5",
        "    next:",
        "      material: LIME_STAINED_GLASS_PANE",
        "      name: '%translations-next-page%'",
        "      action: PAGE_NEXT",
        "      paginated:",
        "        enabled: true",
        "        orElse:",
        "          material: GRAY_STAINED_GLASS_PANE",
        "          name: ' '",
        "      slot: 6",
        "    close:",
        "      material: BARRIER",
        "      name: '&cFermer'",
        "      action: CLOSE",
        "      slot: 9",
    ]
    return "\n".join(lines) + "\n"


def shop_yml(cat):
    items = cat["items"]
    lines = ["pages:"]
    for p in range(0, len(items), PER_PAGE):
        lines += [f"  page{p // PER_PAGE + 1}:", "    gui-rows: 6", "    items:"]
        for material, buy in items[p:p + PER_PAGE]:
            lines += [
                f"      {material.lower()}:",
                f"        material: {material}",
                f"        buy: {fmt(buy)}",
                f"        sell: {fmt(buy * SELL_RATIO)}",
            ]
    return "\n".join(lines) + "\n"


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "EconomyShopGUI"
    os.makedirs(os.path.join(out, "sections"), exist_ok=True)
    os.makedirs(os.path.join(out, "shops"), exist_ok=True)
    for i, cat in enumerate(CATEGORIES):
        seen = set()
        for material, _ in cat["items"]:
            assert material not in seen, f"doublon {material} dans {cat['name']}"
            seen.add(material)
        with open(os.path.join(out, "sections", cat["name"] + ".yml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(section_yml(cat, 11 + i * 2))
        with open(os.path.join(out, "shops", cat["name"] + ".yml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(shop_yml(cat))
        print(f"{cat['name']}: {len(cat['items'])} objets, {(len(cat['items']) - 1) // PER_PAGE + 1} page(s)")


if __name__ == "__main__":
    main()
