// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Provides runtime version, commit, and build information derived from Git metadata. */
public final class VersionInfo {
    private static final String DEFAULT_PROPERTIES_RESOURCE = "/git.properties";
    private static final VersionInfo INSTANCE = loadDefault();

    private final String version;
    private final String releaseVersion;
    private final String commit;
    private final String fullCommit;
    private final String buildTime;
    private final boolean dirty;

    public VersionInfo(Properties props) {
        if (props == null || props.isEmpty()) {
            this.version = "dev";
            this.releaseVersion = "dev";
            this.commit = "unknown";
            this.fullCommit = "unknown";
            this.buildTime = "unknown";
            this.dirty = false;
            return;
        }

        this.fullCommit = props.getProperty("git.commit.id", "unknown");
        this.commit =
                props.getProperty(
                        "git.commit.id.abbrev",
                        fullCommit.length() >= 7 ? fullCommit.substring(0, 7) : "unknown");
        this.buildTime = props.getProperty("git.build.time", "unknown");
        this.dirty = Boolean.parseBoolean(props.getProperty("git.dirty", "false"));

        String describe = props.getProperty("git.commit.id.describe");
        if (describe != null && !describe.isBlank()) {
            this.version = stripLeadingV(describe.trim());
        } else {
            String tag = props.getProperty("git.closest.tag.name");
            if (tag != null && !tag.isBlank()) {
                String cleanTag = stripLeadingV(tag.trim());
                String count = props.getProperty("git.closest.tag.commit.count");
                if (count != null
                        && !count.isBlank()
                        && !"0".equals(count.trim())
                        && !"unknown".equals(commit)) {
                    this.version =
                            cleanTag + "-" + count.trim() + "-g" + commit + (dirty ? "-dirty" : "");
                } else {
                    this.version = cleanTag + (dirty ? "-dirty" : "");
                }
            } else {
                String pom = pomVersion(props);
                this.version = (pom != null) ? pom : "dev";
            }
        }
        this.releaseVersion = resolveReleaseVersion(props);
    }

    /**
     * Returns the clean release version behind this build, without the commit count and dirty
     * marker {@link #version()} carries: the nearest release tag, else the pom's own version.
     */
    private static String resolveReleaseVersion(Properties props) {
        String tag = props.getProperty("git.closest.tag.name");
        if (tag != null && !tag.isBlank()) {
            return stripLeadingV(tag.trim());
        }
        String pom = pomVersion(props);
        return (pom != null) ? pom : "dev";
    }

    /** Returns the pom's own version, or null when it is absent or still a SNAPSHOT. */
    private static String pomVersion(Properties props) {
        String buildVer = props.getProperty("git.build.version");
        if (buildVer == null || buildVer.isBlank() || buildVer.trim().endsWith("-SNAPSHOT")) {
            return null;
        }
        return stripLeadingV(buildVer.trim());
    }

    private static VersionInfo loadDefault() {
        try (InputStream is = VersionInfo.class.getResourceAsStream(DEFAULT_PROPERTIES_RESOURCE)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return new VersionInfo(props);
            }
        } catch (IOException ignored) {
        }
        return new VersionInfo(new Properties());
    }

    private static String stripLeadingV(String s) {
        if (s.startsWith("v") || s.startsWith("V")) {
            return s.substring(1);
        }
        return s;
    }

    /**
     * Returns the semantic version or git-describe version string (e.g. "0.4.0" or
     * "0.4.0-79-gc983a1e").
     */
    public String version() {
        return version;
    }

    /**
     * Returns the release version alone (e.g. "0.5.0"), for places that want to name the release
     * rather than pin down the exact build. Returns "dev" when no release can be identified.
     */
    public String releaseVersion() {
        return releaseVersion;
    }

    /** Returns the abbreviated Git commit hash (e.g. "c983a1e"), or "unknown". */
    public String commit() {
        return commit;
    }

    /** Returns the full Git commit hash, or "unknown". */
    public String fullCommit() {
        return fullCommit;
    }

    /** Returns the build timestamp, or "unknown". */
    public String buildTime() {
        return buildTime;
    }

    /** Returns true if the build was made on a dirty Git worktree. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Returns the display version, appending short commit hash in parentheses if not already
     * embedded.
     */
    public String displayVersion() {
        if (!"unknown".equals(commit)
                && !version.contains(commit)
                && !"dev".equals(version)
                && !"unknown".equals(version)) {
            return version + " (" + commit + ")";
        }
        return version;
    }

    /** Returns standard GNU/AGPL formatted version and license message. */
    public String formatVersionMessage() {
        return "pdf-autoa11y "
                + displayVersion()
                + "\n"
                + "Copyright (C) 2026 Richard Boyechko\n"
                + "License AGPLv3+: GNU AGPL version 3 or later"
                + " <https://gnu.org/licenses/agpl.html>\n"
                + "This is free software: you are free to change and redistribute it.\n"
                + "There is NO WARRANTY, to the extent permitted by law.";
    }

    /** Returns version information loaded from the application's bundled Git metadata. */
    public static VersionInfo current() {
        return INSTANCE;
    }
}
