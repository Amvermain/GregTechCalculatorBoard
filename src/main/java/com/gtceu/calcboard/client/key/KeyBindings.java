package com.gtceu.calcboard.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.gtcalcboard";

    public static final KeyMapping OPEN_BOARD = new KeyMapping(
        "key.gtcalcboard.open_board",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        CATEGORY
    );

    public static final KeyMapping ADD_RECIPE_TO_BOARD = new KeyMapping(
        "key.gtcalcboard.add_recipe_to_board",
        KeyConflictContext.GUI,
        KeyModifier.SHIFT,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_A,
        CATEGORY
    );
}
