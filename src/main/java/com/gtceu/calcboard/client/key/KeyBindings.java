package com.gtceu.calcboard.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.gtcalcboard";

    public static final KeyMapping OPEN_BOARD = new KeyMapping(
        "key.gtcalcboard.open_board",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        CATEGORY
    );

    public static final KeyMapping ADD_RECIPE = new KeyMapping(
        "key.gtcalcboard.add_recipe",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_A,
        CATEGORY
    );
}
