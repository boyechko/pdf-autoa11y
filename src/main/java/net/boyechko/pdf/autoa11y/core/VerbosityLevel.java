/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.core;

/**
 * Defines the verbosity levels for output control.
 *
 * <p>Levels (from least to most verbose):
 *
 * <ul>
 *   <li>QUIET - Only errors and final status
 *   <li>NORMAL - Summary information (default)
 *   <li>VERBOSE - Detailed tag structure and processing steps
 *   <li>DEBUG - All information including debug logs
 * </ul>
 */
public enum VerbosityLevel {
    /** Only show errors and final status */
    QUIET(0),

    /** Show summary information (default) */
    NORMAL(1),

    /** Show detailed tag structure and processing steps */
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

    /**
     * Check if this verbosity level is at least as verbose as the specified level.
     *
     * @param other the level to compare against
     * @return true if this level is at least as verbose as other
     */
    public boolean isAtLeast(VerbosityLevel other) {
        return this.level >= other.level;
    }

    /**
     * Check if output should be shown for the specified level.
     *
     * @param requiredLevel the minimum level required to show the output
     * @return true if output should be shown
     */
    public boolean shouldShow(VerbosityLevel requiredLevel) {
        return this.level >= requiredLevel.level;
    }
}
