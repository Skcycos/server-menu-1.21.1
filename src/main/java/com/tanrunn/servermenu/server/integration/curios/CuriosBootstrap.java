package com.tanrunn.servermenu.server.integration.curios;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Curios 可选集成入口；公共启动类不直接引用 Curios API。 */
public final class CuriosBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(CuriosBootstrap.class);
    private static final String REGISTRATION_CLASS =
            "com.tanrunn.servermenu.server.integration.curios.CuriosFlightRingRegistration";
    private static boolean bootstrapped;

    private CuriosBootstrap() {
    }

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        if (!ModList.get().isLoaded("curios")) {
            LOGGER.info("[ServerMenu] curios not installed; flight ring integration stays unavailable");
            return;
        }
        try {
            Class<?> registration = Class.forName(REGISTRATION_CLASS, false,
                    CuriosBootstrap.class.getClassLoader());
            registration.getMethod("register").invoke(null);
            LOGGER.info("[ServerMenu] registered flight ring Curios integration");
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[ServerMenu] cannot register flight ring Curios integration", e);
        }
    }
}
