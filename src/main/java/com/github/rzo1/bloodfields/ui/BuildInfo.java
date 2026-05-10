package com.github.rzo1.bloodfields.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {

    public static final String VERSION;
    public static final String COMMIT;

    static {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/git.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
        String version = p.getProperty("git.build.version");
        if (version == null || version.isEmpty()) {
            version = "0.1.0-SNAPSHOT";
        }
        String commit = p.getProperty("git.commit.id.abbrev");
        if (commit == null || commit.isEmpty()) {
            commit = "dev";
        }
        VERSION = version;
        COMMIT = commit;
    }

    private BuildInfo() {
    }

    public static String displayString() {
        return "v" + VERSION + " (" + COMMIT + ")";
    }
}
