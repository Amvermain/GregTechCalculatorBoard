package com.gtceu.calcboard.compat.createnewage;

import com.gtceu.calcboard.api.MachineAddon;
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
        if (collector == null) return;
        Set<String> seenIds = new HashSet<>();
        for (MachineAddon existing : collector) {
            seenIds.add(existing.getId());
        }

        try {
            TagKey<Item> magnetTag = getMagnetItemTag();
            TagKey<Block> customMagnetTag = getCustomMagnetBlockTag();

            if (ForgeRegistries.ITEMS != null) {
                for (Item item : ForgeRegistries.ITEMS) {
                    if (item == null) continue;
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;

                    ItemStack stack = new ItemStack(item);
                    Block block = (item instanceof BlockItem bi) ? bi.getBlock() : null;

                    boolean isMagnet = (magnetTag != null && stack.is(magnetTag));
                    if (!isMagnet && block != null && customMagnetTag != null) {
                        try {
                            isMagnet = block.defaultBlockState().is(customMagnetTag);
                        } catch (Throwable ignored) {}
                    }
                    if (!isMagnet && block != null) {
                        try {
                            for (Class<?> iface : block.getClass().getInterfaces()) {
                                if (iface.getName().contains("IMagneticBlock") || iface.getSimpleName().equals("IMagneticBlock")) {
                                    isMagnet = true;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (isMagnet) {
                        int force = extractMagneticForce(item, stack, block);
                        if (force > 0 && !seenIds.contains(id.toString())) {
                            String name = stack.getHoverName().getString();
                            if (name.isEmpty() || name.startsWith("item.") || name.startsWith("block.")) {
                                name = formatDisplayName(id.getPath());
                            }
                            CreateMagnetAddon addon = new CreateMagnetAddon(
                                    id.toString(),
                                    name,
                                    "",
                                    id,
                                    force
                            );
                            addon.setItemStackSample(stack);
                            addon.setDiscoverySource("create_new_age:magnet_spec");
                            collector.add(addon);
                            seenIds.add(id.toString());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static int extractMagneticForce(Item item, ItemStack stack, Block block) {
        // 1. Direct IMagneticBlock.getStrength() official method reflection
        if (block != null) {
            int force = invokeGetStrengthReflection(block);
            if (force > 0) return force;
        }
        if (item != null) {
            int force = invokeGetStrengthReflection(item);
            if (force > 0) return force;
        }

        // 2. Official Create: New Age block tags: create_new_age:magnets/strength_<N>
        if (block != null) {
            try {
                var state = block.defaultBlockState();
                var tagStream = state.getTags();
                for (TagKey<Block> tag : tagStream.toList()) {
                    ResourceLocation tagLoc = tag.location();
                    if (tagLoc != null && tagLoc.getNamespace().equals(MOD_ID)) {
                        String path = tagLoc.getPath();
                        if (path.startsWith("magnets/strength_")) {
                            try {
                                int parsed = Integer.parseInt(path.substring("magnets/strength_".length()));
                                if (parsed > 0) return parsed;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Deterministic NBT inspection
        if (stack != null && stack.hasTag()) {
            var tag = stack.getTag();
            if (tag != null) {
                if (tag.contains("Strength")) return tag.getInt("Strength");
                if (tag.contains("strength")) return tag.getInt("strength");
                if (tag.contains("MagneticForce")) return tag.getInt("MagneticForce");
                if (tag.contains("magnetic_force")) return tag.getInt("magnetic_force");
            }
        }

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
