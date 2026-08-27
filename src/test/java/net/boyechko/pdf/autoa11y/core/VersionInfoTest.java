// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class VersionInfoTest {

    @Test
    void defaultInstanceLoadsSuccessfully() {
        VersionInfo info = VersionInfo.current();

        assertNotNull(info.version());
        assertNotNull(info.commit());
        assertNotNull(info.fullCommit());
        assertNotNull(info.buildTime());
        assertNotNull(info.displayVersion());
        assertNotNull(info.formatVersionMessage());
        assertTrue(info.formatVersionMessage().contains("pdf-autoa11y"));
        assertTrue(info.formatVersionMessage().contains("License AGPLv3+"));
    }

    @Test
    void nullOrEmptyPropertiesReturnDefaults() {
        VersionInfo nullInfo = new VersionInfo(null);
        assertEquals("dev", nullInfo.version());
        assertEquals("unknown", nullInfo.commit());
        assertEquals("unknown", nullInfo.fullCommit());
        assertEquals("unknown", nullInfo.buildTime());
        assertFalse(nullInfo.isDirty());
        assertEquals("dev", nullInfo.displayVersion());
        assertTrue(nullInfo.formatVersionMessage().startsWith("pdf-autoa11y dev\n"));

        VersionInfo emptyInfo = new VersionInfo(new Properties());
        assertEquals("dev", emptyInfo.version());
        assertEquals("unknown", emptyInfo.commit());
    }

    @Test
    void exactReleaseTagFormatsDisplayVersionWithCommitInParentheses() {
        Properties props = new Properties();
        props.setProperty("git.closest.tag.name", "v0.4.0");
        props.setProperty("git.commit.id.describe", "v0.4.0");
        props.setProperty("git.commit.id.abbrev", "c983a1e");
        props.setProperty("git.commit.id", "c983a1e591af7313ed1aee7974cf2f126562dd12");
        props.setProperty("git.build.time", "2026-08-21T14:00:00-07:00");
        props.setProperty("git.dirty", "false");

        VersionInfo info = new VersionInfo(props);
        assertEquals("0.4.0", info.version());
        assertEquals("c983a1e", info.commit());
        assertEquals("c983a1e591af7313ed1aee7974cf2f126562dd12", info.fullCommit());
        assertEquals("2026-08-21T14:00:00-07:00", info.buildTime());
        assertFalse(info.isDirty());
        assertEquals("0.4.0 (c983a1e)", info.displayVersion());
        assertTrue(
                info.formatVersionMessage()
                        .startsWith("pdf-autoa11y 0.4.0 (c983a1e)\nCopyright (C) 2026"));
    }

    @Test
    void postReleaseDescribeStringPreservesFullGitDescribe() {
        Properties props = new Properties();
        props.setProperty("git.closest.tag.name", "v0.4.0");
        props.setProperty("git.commit.id.describe", "v0.4.0-79-gc983a1e");
        props.setProperty("git.commit.id.abbrev", "c983a1e");
        props.setProperty("git.dirty", "false");

        VersionInfo info = new VersionInfo(props);
        assertEquals("0.4.0-79-gc983a1e", info.version());
        assertEquals("0.4.0-79-gc983a1e", info.displayVersion());
        assertFalse(info.isDirty());
    }

    @Test
    void dirtyPostReleaseDescribeMarksDirty() {
        Properties props = new Properties();
        props.setProperty("git.closest.tag.name", "v0.4.0");
        props.setProperty("git.commit.id.describe", "v0.4.0-79-gc983a1e-dirty");
        props.setProperty("git.commit.id.abbrev", "c983a1e");
        props.setProperty("git.dirty", "true");

        VersionInfo info = new VersionInfo(props);
        assertEquals("0.4.0-79-gc983a1e-dirty", info.version());
        assertTrue(info.isDirty());
    }

    @Test
    void fallbackFromClosestTagWhenDescribeMissing() {
        Properties props = new Properties();
        props.setProperty("git.closest.tag.name", "v0.5.0");
        props.setProperty("git.closest.tag.commit.count", "3");
        props.setProperty("git.commit.id.abbrev", "1a2b3c4");
        props.setProperty("git.dirty", "true");

        VersionInfo info = new VersionInfo(props);
        assertEquals("0.5.0-3-g1a2b3c4-dirty", info.version());
        assertTrue(info.isDirty());
    }

    @Test
    void fallbackFromClosestTagAtZeroCommits() {
        Properties props = new Properties();
        props.setProperty("git.closest.tag.name", "v0.5.0");
        props.setProperty("git.closest.tag.commit.count", "0");
        props.setProperty("git.commit.id.abbrev", "1a2b3c4");
        props.setProperty("git.dirty", "false");

        VersionInfo info = new VersionInfo(props);
        assertEquals("0.5.0", info.version());
        assertEquals("0.5.0 (1a2b3c4)", info.displayVersion());
    }

    @Test
    void fallbackFromBuildVersionWhenNoGitTags() {
        Properties props = new Properties();
        props.setProperty("git.build.version", "2.0.0");

        VersionInfo info = new VersionInfo(props);
        assertEquals("2.0.0", info.version());
    }
}
