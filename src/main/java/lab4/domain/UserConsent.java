package lab4.domain;

import java.time.Instant;

public class UserConsent {
    private String userId;
    private String purpose;
    private boolean isGranted;
    private String policyVersion;
    private Instant updatedAt;

    public UserConsent(String userId, String purpose, boolean isGranted, String policyVersion) {
        this.userId = userId;
        this.purpose = purpose;
        this.isGranted = isGranted;
        this.policyVersion = policyVersion;
        this.updatedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getPurpose() { return purpose; }
    public boolean isGranted() { return isGranted; }
    public void setGranted(boolean granted) { isGranted = granted; this.updatedAt = Instant.now(); }
    public String getPolicyVersion() { return policyVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}