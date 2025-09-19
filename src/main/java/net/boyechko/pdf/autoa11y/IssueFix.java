package net.boyechko.pdf.autoa11y;

public interface IssueFix {
    /** Lower numbers run earlier. Keep it simple: 0..100 is plenty. */
    int priority();

    /** Idempotent application of the fix. */
    void apply(ProcessingContext ctx) throws Exception;

    /** Human-readable for logs/UI. */
    default String describe() { return getClass().getSimpleName(); }
}
