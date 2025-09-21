package net.boyechko.pdf.autoa11y;

public interface IssueFix {
    /** Lower numbers run earlier. Keep it simple: 0..100 is plenty. */
    int priority();

    /** Idempotent application of the fix. */
    void apply(ProcessingContext ctx) throws Exception;

    /** Human-readable for logs/UI. */
    default String describe() { return getClass().getSimpleName(); }

    /**
     * Check if this fix invalidates another fix. Called after this fix is applied
     * to determine if other pending fixes should be skipped.
     * @param otherFix The other fix to check for invalidation
     * @return true if the other fix should be skipped, false otherwise
     */
    default boolean invalidates(IssueFix otherFix) { return false; }
}
