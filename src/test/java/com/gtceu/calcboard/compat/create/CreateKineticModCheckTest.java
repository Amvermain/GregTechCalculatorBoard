package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CreateKineticModCheckTest {

    @BeforeEach
    public void setUp() {
        ModCompatHelper.clearTestOverrides();
    }

    @AfterEach
    public void tearDown() {
        ModCompatHelper.clearTestOverrides();
    }

    @Test
    public void testModCompatHelperCreateOverrides() {
        ModCompatHelper.setTestOverride("create", false);
        ModCompatHelper.setTestOverride("createaddition", false);
        ModCompatHelper.setTestOverride("create_new_age", false);
        ModCompatHelper.setTestOverride("createdieselgenerators", false);

        Assertions.assertFalse(ModCompatHelper.isCreateLoaded());
        Assertions.assertFalse(ModCompatHelper.isCreateAdditionsLoaded());
        Assertions.assertFalse(ModCompatHelper.isCreateNewAgeLoaded());
        Assertions.assertFalse(ModCompatHelper.isCreateDieselGeneratorsLoaded());

        ModCompatHelper.setTestOverride("create", true);
        ModCompatHelper.setTestOverride("createaddition", true);
        ModCompatHelper.setTestOverride("create_new_age", true);
        ModCompatHelper.setTestOverride("createdieselgenerators", true);

        Assertions.assertTrue(ModCompatHelper.isCreateLoaded());
        Assertions.assertTrue(ModCompatHelper.isCreateAdditionsLoaded());
        Assertions.assertTrue(ModCompatHelper.isCreateNewAgeLoaded());
        Assertions.assertTrue(ModCompatHelper.isCreateDieselGeneratorsLoaded());
    }
}
