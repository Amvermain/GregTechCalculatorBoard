package com.gtceu.calcboard.api.model.role;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MachineRoleNBTValidationTest {

    @Test
    @DisplayName("RFC-058: deserializeRoleNBT sanitizes NaN and Infinity to 1.0")
    public void testNanAndInfinitySanitization() {
        MachineNodeRole role = new MachineNodeRole();

        CompoundTag nanTag = new CompoundTag();
        nanTag.putDouble("machineCount", Double.NaN);
        role.deserializeRoleNBT(nanTag);
        Assertions.assertTrue(Double.isFinite(role.getMachineCount()));
        Assertions.assertEquals(1.0, role.getMachineCount(), 1e-6);

        CompoundTag infTag = new CompoundTag();
        infTag.putDouble("machineCount", Double.POSITIVE_INFINITY);
        role.deserializeRoleNBT(infTag);
        Assertions.assertTrue(Double.isFinite(role.getMachineCount()));
        Assertions.assertEquals(1.0, role.getMachineCount(), 1e-6);
    }

    @Test
    @DisplayName("RFC-058: deserializeRoleNBT clamps non-positive machine count to 0.01")
    public void testNonPositiveCountClamping() {
        MachineNodeRole role = new MachineNodeRole();

        CompoundTag zeroTag = new CompoundTag();
        zeroTag.putDouble("machineCount", 0.0);
        role.deserializeRoleNBT(zeroTag);
        Assertions.assertEquals(0.01, role.getMachineCount(), 1e-6);

        CompoundTag negTag = new CompoundTag();
        negTag.putDouble("machineCount", -10.5);
        role.deserializeRoleNBT(negTag);
        Assertions.assertEquals(0.01, role.getMachineCount(), 1e-6);

        CompoundTag validTag = new CompoundTag();
        validTag.putDouble("machineCount", 4.25);
        role.deserializeRoleNBT(validTag);
        Assertions.assertEquals(4.25, role.getMachineCount(), 1e-6);
    }

    @Test
    @DisplayName("RFC-058: deserializeRoleNBT clamps parallel to at least 1 and customParallel to at least 0")
    public void testParallelClamping() {
        MachineNodeRole role = new MachineNodeRole();

        CompoundTag tag = new CompoundTag();
        tag.putInt("parallel", -5);
        tag.putInt("customParallel", -3);
        role.deserializeRoleNBT(tag);

        Assertions.assertEquals(1, role.getParallel());
        Assertions.assertEquals(0, role.getCustomParallel());

        CompoundTag zeroTag = new CompoundTag();
        zeroTag.putInt("parallel", 0);
        zeroTag.putInt("customParallel", 0);
        role.deserializeRoleNBT(zeroTag);

        Assertions.assertEquals(1, role.getParallel());
        Assertions.assertEquals(0, role.getCustomParallel());

        CompoundTag validTag = new CompoundTag();
        validTag.putInt("parallel", 8);
        validTag.putInt("customParallel", 16);
        role.deserializeRoleNBT(validTag);

        Assertions.assertEquals(8, role.getParallel());
        Assertions.assertEquals(16, role.getCustomParallel());
    }
}
