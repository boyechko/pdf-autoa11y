// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkTest {

    @Test
    void nullIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri(null));
    }

    @Test
    void blankStringIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri(""));
        assertFalse(Link.isValidWebUri("   "));
    }

    @Test
    void stringWithoutSchemeIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("example.com"));
    }

    @Test
    void nonHttpSchemeIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("ftp://example.com"));
        assertFalse(Link.isValidWebUri("mailto:user@example.com"));
        assertFalse(Link.isValidWebUri("file:///etc/hosts"));
    }

    @Test
    void missingHostIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://"));
    }

    @Test
    void hostWithoutDotIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://localhost"));
        assertFalse(Link.isValidWebUri("https://example"));
    }

    @Test
    void numericTldIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://practice.10"));
        assertFalse(Link.isValidWebUri("https://foo.1"));
    }

    @Test
    void singleCharacterTldIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://example.c"));
    }

    @Test
    void mixedAlphanumericTldIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://foo.c0m"));
    }

    @Test
    void syntacticallyInvalidUriIsNotValidWebUri() {
        assertFalse(Link.isValidWebUri("https://exa mple.com"));
        assertFalse(Link.isValidWebUri("http://[not a uri"));
    }

    @Test
    void basicHttpsUrlIsValidWebUri() {
        assertTrue(Link.isValidWebUri("https://example.com"));
    }

    @Test
    void basicHttpUrlIsValidWebUri() {
        assertTrue(Link.isValidWebUri("http://example.com"));
    }

    @Test
    void uppercaseSchemeIsValidWebUri() {
        assertTrue(Link.isValidWebUri("HTTPS://example.com"));
    }

    @Test
    void urlWithPathAndQueryIsValidWebUri() {
        assertTrue(Link.isValidWebUri("https://example.com/path?q=1&r=2"));
    }

    @Test
    void urlWithPortIsValidWebUri() {
        assertTrue(Link.isValidWebUri("https://example.com:8080/path"));
    }

    @Test
    void multiLabelHostIsValidWebUri() {
        assertTrue(Link.isValidWebUri("https://sub.example.co.uk"));
    }
}
