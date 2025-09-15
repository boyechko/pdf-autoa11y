package net.boyechko.a11y.pdf_normalizer;

public class OperationResult {
    private final boolean success;
    private final int changeCount;
    private final int warningCount;
    private final String message;

    private OperationResult(boolean success, int changeCount, int warningCount, String message) {
        this.success = success;
        this.changeCount = changeCount;
        this.warningCount = warningCount;
        this.message = message;
    }

    public static OperationResult success(String message) {
        return new OperationResult(true, 0, 0, message);
    }

    public static OperationResult changes(int changeCount, String message) {
        return new OperationResult(true, changeCount, 0, message);
    }

    public static OperationResult warnings(int warningCount, String message) {
        return new OperationResult(true, 0, warningCount, message);
    }

    public static OperationResult changesAndWarnings(int changeCount, int warningCount, String message) {
        return new OperationResult(true, changeCount, warningCount, message);
    }

    public static OperationResult error(String message) {
        return new OperationResult(false, 0, 0, message);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public int getChangeCount() { return changeCount; }
    public int getWarningCount() { return warningCount; }
    public String getMessage() { return message; }
}
