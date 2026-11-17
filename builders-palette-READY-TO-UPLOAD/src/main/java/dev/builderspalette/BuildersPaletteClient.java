package dev.builderspalette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Builder's Palette (client-only). Look at a block (or, while sneaking, hold one) and a small panel next to the
 * crosshair lists blocks that match its colour:
 *   - for dyed blocks (wool, concrete, terracotta, ...): the other dyed types in the same colour
 *   - natural look-alikes from a hand-made colour-family table
 *   - for natural blocks: the dyed blocks that match its family
 * Stairs, slabs, walls, fences, doors, buttons etc. use their base block's family.
 *
 * It is a lookup table, so it knows no modded blocks (they simply show nothing). Text only; no mixins.
 */
public class BuildersPaletteClient implements ClientModInitializer {

    record Line(String text, int rgb) {}
    record Result(String title, int swatch, List<Line> lines) {}
    record Family(String label, int rgb, String[] blocks) {}

    // ------------------------------------------------------------------ the table

    private static final String[] DYES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    /** Block types that come in all 16 dye colours. */
    private static final Set<String> DYE_TYPES = Set.of(
            "wool", "concrete", "concrete_powder", "terracotta", "glazed_terracotta",
            "stained_glass", "stained_glass_pane", "carpet", "shulker_box", "bed", "candle", "banner");

    /** The dyed types shown as "same colour" suggestions, in this order. */
    private static final String[] SHOWN_TYPES = {
            "wool", "concrete", "terracotta", "glazed_terracotta", "stained_glass", "concrete_powder" };

    private static final int[] DYE_RGB = {
            0xF0F0F0, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21 };

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
    private static final Map<String, String> BLOCK_TO_FAMILY = new HashMap<>();

    private static void family(String key, String label, int rgb, String... blocks) {
        FAMILIES.put(key, new Family(label, rgb, blocks));
        for (String b : blocks) BLOCK_TO_FAMILY.putIfAbsent(b, key);
    }

    static {
        family("white", "White tones", 0xF0F0F0, "snow_block", "quartz_block", "smooth_quartz", "quartz_bricks",
                "quartz_pillar", "chiseled_quartz_block", "calcite", "bone_block", "diorite", "polished_diorite",
                "pale_oak_planks", "pale_oak_log");
        family("light_gray", "Light gray tones", 0x9D9D97, "stone", "smooth_stone", "stone_bricks",
                "cracked_stone_bricks", "chiseled_stone_bricks", "andesite", "polished_andesite", "gravel", "iron_block");
        family("gray", "Gray tones", 0x474F52, "cobblestone", "deepslate", "cobbled_deepslate", "polished_deepslate",
                "deepslate_bricks", "deepslate_tiles", "tuff", "polished_tuff", "tuff_bricks", "basalt",
                "smooth_basalt", "polished_basalt", "chiseled_deepslate");
        family("black", "Black tones", 0x1D1D21, "blackstone", "polished_blackstone", "polished_blackstone_bricks",
                "chiseled_polished_blackstone", "gilded_blackstone", "obsidian", "coal_block", "netherite_block",
                "sculk");
        family("brown", "Brown tones", 0x835432, "dark_oak_planks", "dark_oak_log", "spruce_planks", "spruce_log",
                "dirt", "coarse_dirt", "podzol", "mud", "mud_bricks", "packed_mud", "soul_sand", "soul_soil",
                "rooted_dirt", "brown_mushroom_block");
        family("red", "Red tones", 0xB02E26, "bricks", "red_nether_bricks", "nether_bricks", "nether_wart_block",
                "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", "red_mushroom_block", "redstone_block",
                "netherrack", "crimson_planks", "crimson_stem", "mangrove_planks", "mangrove_log", "granite",
                "polished_granite", "red_sand");
        family("orange", "Orange tones", 0xF9801D, "acacia_planks", "acacia_log", "terracotta", "copper_block",
                "cut_copper", "raw_copper_block", "pumpkin", "carved_pumpkin", "jack_o_lantern", "honey_block",
                "honeycomb_block", "magma_block", "shroomlight");
        family("yellow", "Yellow tones", 0xFED83D, "hay_block", "gold_block", "raw_gold_block", "sponge",
                "glowstone", "birch_planks", "birch_log", "bamboo_planks", "bamboo_block", "bamboo_mosaic");
        family("tan", "Sand and cream tones", 0xE0D2A0, "sand", "sandstone", "smooth_sandstone", "cut_sandstone",
                "chiseled_sandstone", "end_stone", "end_stone_bricks", "oak_planks", "oak_log", "jungle_planks",
                "jungle_log");
        family("lime", "Lime tones", 0x80C71F, "slime_block", "melon", "moss_block", "moss_carpet",
                "verdant_froglight");
        family("green", "Green tones", 0x5E7C16, "cactus", "emerald_block", "dried_kelp_block",
                "mossy_cobblestone", "mossy_stone_bricks", "grass_block");
        family("cyan", "Cyan tones", 0x169C9C, "warped_planks", "warped_stem", "warped_wart_block", "prismarine",
                "prismarine_bricks", "dark_prismarine", "oxidized_copper", "weathered_copper",
                "oxidized_cut_copper", "weathered_cut_copper");
        family("light_blue", "Light blue tones", 0x3AB3DA, "blue_ice", "ice", "packed_ice", "diamond_block");
        family("blue", "Blue tones", 0x3C44AA, "lapis_block", "tube_coral_block");
        family("purple", "Purple tones", 0x8932B8, "purpur_block", "purpur_pillar", "amethyst_block",
                "budding_amethyst", "crying_obsidian", "mycelium");
        family("magenta", "Magenta tones", 0xC74EBD, "bubble_coral_block");
        family("pink", "Pink tones", 0xF38BAA, "cherry_planks", "cherry_log", "cherry_leaves", "pink_petals",
                "brain_coral_block", "pearlescent_froglight");
    }

    private static final String[] SUFFIXES = {
            "_fence_gate", "_pressure_plate", "_hanging_sign", "_wall_sign", "_trapdoor", "_stairs", "_button",
            "_fence", "_slab", "_wall", "_door", "_sign" };

    // ------------------------------------------------------------------ state

    private static String currentPath = null;
    private static Result currentResult = null;
    private static final Map<String, Result> CACHE = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(BuildersPaletteClient::tick);
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("builderspalette", "palette"),
                (graphics, tickCounter) -> draw(graphics));
    }

    // ------------------------------------------------------------------ what are we looking at?

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) {
            currentPath = null;
            currentResult = null;
            return;
        }

        String path = null;
        HitResult hit = player.pick(5.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            String id = level.getBlockState(blockHit.getBlockPos()).getBlock().getDescriptionId();
            path = id.substring(id.lastIndexOf('.') + 1);
        } else if (player.isShiftKeyDown()) {
            // Not looking at a block: while sneaking, show the palette for the item in your hand.
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                String id = held.getItem().getDescriptionId();
                path = id.substring(id.lastIndexOf('.') + 1);
            }
        }

        if (path == null) {
            currentPath = null;
            currentResult = null;
            return;
        }
        if (path.equals(currentPath)) return;
        currentPath = path;
        currentResult = CACHE.computeIfAbsent(path, p -> lookup(p));
    }

    // ------------------------------------------------------------------ lookup

    private static Result lookup(String p) {
        // Dyed block? (red_wool, light_blue_concrete, ...)
        for (int i = 0; i < DYES.length; i++) {
            String color = DYES[i];
            if (p.startsWith(color + "_")) {
                String type = p.substring(color.length() + 1);
                if (DYE_TYPES.contains(type)) return dyed(p, color, type, DYE_RGB[i]);
            }
        }

        // Natural block (stairs, slabs and so on use their base block).
        String matched = null;
        for (String candidate : candidates(p)) {
            if (BLOCK_TO_FAMILY.containsKey(candidate)) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) return null;
        return natural(p, matched, BLOCK_TO_FAMILY.get(matched));
    }

    private static List<String> candidates(String p) {
        String base = p;
        if (base.startsWith("waxed_")) base = base.substring(6);
        if (base.startsWith("stripped_")) base = base.substring(9);
        for (String suffix : SUFFIXES) {
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }
        List<String> list = new ArrayList<>();
        list.add(p);
        list.add(base);
        list.add(base + "s");
        list.add(base + "_planks");
        list.add(base + "_block");
        list.add(base + "_bricks");
        list.add(base.replace("_wood", "_log"));
        return list;
    }

    private static Result dyed(String p, String color, String type, int rgb) {
        List<Line> lines = new ArrayList<>();

        List<String> same = new ArrayList<>();
        for (String t : SHOWN_TYPES) {
            if (!t.equals(type)) same.add(typeName(t));
        }
        lines.add(new Line("Same colour: " + String.join(", ", same), 0xE8E8E8));

        List<String> alike = new ArrayList<>();
        Family fam = FAMILIES.get(color);
        if (fam != null) {
            for (String b : fam.blocks()) {
                if (alike.size() >= 6) break;
                alike.add(title(b));
            }
        }
        if (!alike.isEmpty()) lines.add(new Line("Natural look-alikes: " + String.join(", ", alike), 0xFFD060));

        return new Result(title(p), rgb, lines);
    }

    private static Result natural(String p, String matched, String familyKey) {
        Family fam = FAMILIES.get(familyKey);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(fam.label(), 0xAAAAAA));

        List<String> similar = new ArrayList<>();
        for (String b : fam.blocks()) {
            if (similar.size() >= 6) break;
            if (!b.equals(matched)) similar.add(title(b));
        }
        if (!similar.isEmpty()) lines.add(new Line("Similar: " + String.join(", ", similar), 0xFFD060));

        // Dyed blocks that match, if this family is one of the 16 dye colours.
        boolean isDye = false;
        for (String d : DYES) if (d.equals(familyKey)) isDye = true;
        if (isDye) {
            List<String> dyedNames = new ArrayList<>();
            for (int i = 0; i < 3; i++) dyedNames.add(title(familyKey + "_" + SHOWN_TYPES[i]));
            lines.add(new Line("Dyed to match: " + String.join(", ", dyedNames), 0xE8E8E8));
        }
        return new Result(title(p), fam.rgb(), lines);
    }

    private static String typeName(String type) {
        return switch (type) {
            case "stained_glass" -> "Glass";
            case "glazed_terracotta" -> "Glazed Terracotta";
            case "concrete_powder" -> "Concrete Powder";
            default -> title(type);
        };
    }

    private static String title(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ display

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Result r = currentResult;
        if (r == null || mc.player == null) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int textWidth = mc.font.width(r.title()) + 12;
        for (Line l : r.lines()) textWidth = Math.max(textWidth, mc.font.width(l.text()));
        int boxW = textWidth + 10;
        int boxH = (r.lines().size() + 1) * 11 + 6;
        int x0 = w / 2 + 14;
        int y0 = h / 2 - 14;

        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0xB0000000);
        g.fill(x0, y0, x0 + boxW, y0 + 1, 0xFF000000 | r.swatch());

        int y = y0 + 4;
        g.fill(x0 + 5, y + 1, x0 + 13, y + 9, 0xFF000000 | r.swatch());   // colour swatch
        g.fill(x0 + 5, y + 1, x0 + 13, y + 2, 0x60FFFFFF);
        g.text(mc.font, r.title(), x0 + 17, y, 0xFFFFFFFF, true);
        y += 11;
        for (Line l : r.lines()) {
            g.text(mc.font, l.text(), x0 + 5, y, 0xFF000000 | l.rgb(), true);
            y += 11;
        }
    }
}
