package com.mmm.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Logging must not initialize the client or its rendering integrations. */
public final class MmmLog {
    public static final Logger LOGGER = LogManager.getLogger("MMM");
    private MmmLog() {}
}
