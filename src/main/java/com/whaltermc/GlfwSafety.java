package com.whaltermc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlfwSafety {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android/glfw");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private GlfwSafety() {}

    public static void missing(String function, Throwable cause) {
        if (REPORTED.add(function)) {
            LOGGER.warn("Launcher GLFW is missing or cannot run {} ({}); using a default result",
                    function, cause.toString());
        }
    }
}
