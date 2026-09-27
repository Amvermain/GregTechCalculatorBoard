package com.gtceu.calcboard.compat.createnewage;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Deductively discovers Create: New Age magnets from official IMagneticBlock interface reflection
 * and official block tags in strict compliance with Rule 5.
 */
public class CreateNewAgeAddonCrawler {

    public static final String MOD_ID = "create_new_age";

    public static TagKey<Item> getMagnetItemTag() {
        try {
            return TagKey.create(Registries.ITEM, ResourceLocation.tryParse("create_new_age:magnet"));
        } catch (Throwable t) {
            return null;
        }
    }

    public static TagKey<Block> getCustomMagnetBlockTag() {
        try {
            return TagKey.create(Registries.BLOCK, ResourceLocation.tryParse("create_new_age:custom_magnet"));
        } catch (Throwable t) {
            return null;
        }
    }

    public static void discoverMagnets(List<MachineAddon> collector) {
        if (collector == null || ForgeRegistries.ITEMS == null) return;
        Set<String> seenIds = collectExistingIds(collector);
        TagKey<Item> magnetTag = getMagnetItemTag();
        TagKey<Block> customMagnetTag = getCustomMagnetBlockTag();

        try {
            for (Item item : ForgeRegistries.ITEMS) {
                processItem(item, collector, seenIds, magnetTag, customMagnetTag);
            }
        } catch (Throwable ignored) {}
    }

    private static Set<String> collectExistingIds(List<MachineAddon> collector) {
        Set<String> seenIds = new HashSet<>();
        for (MachineAddon existing : collector) {
            seenIds.add(existing.getId());
        }
        return seenIds;
    }

    private static void processItem(Item item, List<MachineAddon> collector, Set<String> seenIds,
                                    TagKey<Item> magnetTag, TagKey<Block> customMagnetTag) {
        if (item == null) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || seenIds.contains(id.toString())) return;

        ItemStack stack = new ItemStack(item);
        Block block = (item instanceof BlockItem bi) ? bi.getBlock() : null;

        if (!isMagnetItemOrBlock(stack, block, magnetTag, customMagnetTag)) return;

        int force = extractMagneticForce(item, stack, block);
        if (force <= 0) return;

        CreateMagnetAddon addon = createMagnetAddon(id, stack, force);
        collector.add(addon);
        seenIds.add(id.toString());
    }

    private static boolean isMagnetItemOrBlock(ItemStack stack, Block block, TagKey<Item> magnetTag, TagKey<Block> customMagnetTag) {
        if (magnetTag != null && stack.is(magnetTag)) return true;
        if (block == null) return false;
        if (customMagnetTag != null && block.defaultBlockState().is(customMagnetTag)) return true;
        return implementsMagneticBlock(block.getClass());
    }

    private static boolean implementsMagneticBlock(Class<?> blockClass) {
        for (Class<?> iface : blockClass.getInterfaces()) {
            if ("IMagneticBlock".equals(iface.getSimpleName()) || iface.getName().endsWith(".IMagneticBlock")) {
                return true;
            }
        }
        return false;
    }

    private static CreateMagnetAddon createMagnetAddon(ResourceLocation id, ItemStack stack, int force) {
        String name = resolveDisplayName(stack, id);
        CreateMagnetAddon addon = new CreateMagnetAddon(id.toString(), name, "", id, force);
        addon.setItemStackSample(stack);
        addon.setDiscoverySource("create_new_age:magnet_spec");
        return addon;
    }

    private static String resolveDisplayName(ItemStack stack, ResourceLocation id) {
        String name = stack.getHoverName().getString();
        if (name.isEmpty() || name.startsWith("item.") || name.startsWith("block.")) {
            return formatDisplayName(id.getPath());
        }
        return name;
    }

    public static int extractMagneticForce(Item item, ItemStack stack, Block block) {
        if (block != null) {
            int force = invokeGetStrengthReflection(block);
            if (force > 0) return force;
        }
        if (item != null) {
            int force = invokeGetStrengthReflection(item);
            if (force > 0) return force;
        }

        int tagForce = extractForceFromBlockTags(block);
        if (tagForce > 0) return tagForce;

        return extractForceFromNbt(stack);
    }

    private static int extractForceFromBlockTags(Block block) {
        if (block == null) return 0;
        try {
            for (TagKey<Block> tag : block.defaultBlockState().getTags().toList()) {
                int force = parseForceFromTag(tag);
                if (force > 0) return force;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static int parseForceFromTag(TagKey<Block> tag) {
        ResourceLocation tagLoc = tag.location();
        if (tagLoc == null || !MOD_ID.equals(tagLoc.getNamespace())) return 0;
        String path = tagLoc.getPath();
        if (!path.startsWith("magnets/strength_")) return 0;
        try {
            return Math.max(0, Integer.parseInt(path.substring("magnets/strength_".length())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int extractForceFromNbt(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0;
        var tag = stack.getTag();
        if (tag == null) return 0;
        if (tag.contains("Strength")) return tag.getInt("Strength");
        if (tag.contains("strength")) return tag.getInt("strength");
        if (tag.contains("MagneticForce")) return tag.getInt("MagneticForce");
        if (tag.contains("magnetic_force")) return tag.getInt("magnetic_force");
        return 0;
    }

    private static int invokeGetStrengthReflection(Object obj) {
        if (obj == null) return 0;
        Class<?> cls = obj.getClass();
        String[] methodNames = {"getStrength", "getMagneticForce", "getForce", "getMagnetForce", "strength", "force"};
        for (String mName : methodNames) {
            try {
                Method m = cls.getMethod(mName);
                Object res = m.invoke(obj);
                if (res instanceof Number num && num.intValue() > 0) {
                    return num.intValue();
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static String formatDisplayName(String path) {
        if (path == null || path.isEmpty()) return "Magnet";
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}

