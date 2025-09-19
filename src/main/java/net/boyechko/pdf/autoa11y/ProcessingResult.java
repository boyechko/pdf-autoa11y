package net.boyechko.pdf.autoa11y;
    
public class ProcessingResult {
    private final boolean success;
    private final int issueCount;
    private final int changeCount;
    private final int warningCount;
    private final String errorMessage;

    private ProcessingResult(boolean success, int issueCount, int changeCount, int warningCount, String errorMessage) {
        this.success = success;
        this.issueCount = issueCount;
        this.changeCount = changeCount;
        this.warningCount = warningCount;
        this.errorMessage = errorMessage;
    }

    public static ProcessingResult success(int issueCount, int changeCount, int warningCount) {
        return new ProcessingResult(true, issueCount, changeCount, warningCount, null);
    }

    public static ProcessingResult error(String errorMessage) {
        return new ProcessingResult(false, 0, 0, 0, errorMessage);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public int getIssueCount() { return issueCount; }
    public int getChangeCount() { return changeCount; }
    public int getWarningCount() { return warningCount; }
    public String getErrorMessage() { return errorMessage; }
}