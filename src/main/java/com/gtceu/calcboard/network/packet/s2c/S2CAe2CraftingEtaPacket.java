package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client packet transmitting computed AE2 autocrafting plan precision ETA and bottleneck metrics.
 */
public class S2CAe2CraftingEtaPacket {

    private final Ae2PlanEvaluationResult result;

    public S2CAe2CraftingEtaPacket(Ae2PlanEvaluationResult result) {
        this.result = result != null ? result : Ae2PlanEvaluationResult.EMPTY;
    }

    public S2CAe2CraftingEtaPacket(FriendlyByteBuf buf) {
        this.result = Ae2PlanEvaluationResult.readBuffer(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        result.writeBuffer(buf);
    }

    public Ae2PlanEvaluationResult getResult() {
        return result;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAe2CraftingEta(this)));
        ctx.setPacketHandled(true);
    }
}
