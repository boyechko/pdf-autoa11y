// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

/** Defines the verbosity levels for output control. */
public enum VerbosityLevel {
    /** Only show errors and final status */
    QUIET(0),

    /** Show summary information (default) */
    NORMAL(1),

    /** Show detailed processing steps */
    VERBOSE(2),

    /** Show all information including debug logs */
    DEBUG(3);

    private final int level;

    VerbosityLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(VerbosityLevel other) {
        return this.level >= other.level;
    }

    public boolean shouldShow(VerbosityLevel requiredLevel) {
        return this.level >= requiredLevel.level;
    }
}
