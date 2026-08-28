package com.gtceu.calcboard.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CalcBoardJeiPlugin implements IModPlugin {

    public static final ResourceLocation PLUGIN_UID = ResourceLocation.tryParse("gtcalcboard:jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeViewerAdapter.setJeiRuntime(jeiRuntime);
        com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().bake(jeiRuntime);
    }
}
