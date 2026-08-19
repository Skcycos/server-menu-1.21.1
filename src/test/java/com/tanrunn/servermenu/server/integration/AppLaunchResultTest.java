package com.tanrunn.servermenu.server.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppLaunchResultTest {

    @Test
    void successFactorySemantics() {
        AppLaunchResult result = AppLaunchResult.ok();
        assertTrue(result.success());
        assertEquals("", result.userMessage());
        assertFalse(result.error());
    }

    @Test
    void failureFactorySemantics() {
        AppLaunchResult result = AppLaunchResult.failure("出错了");
        assertFalse(result.success());
        assertEquals("出错了", result.userMessage());
        assertTrue(result.error());
    }

    @Test
    void failureFactorySanitizesNullMessage() {
        AppLaunchResult result = AppLaunchResult.failure(null);
        assertFalse(result.success());
        assertEquals("", result.userMessage());
    }
}
