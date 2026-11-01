package lab4.domain;

import java.util.HashMap;
import java.util.Map;

public class UserProfile {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private Map<String, String> settings = new HashMap<>();
    private Map<String, Object> consents = new HashMap<>();
    private boolean isAnonymized = false;

    public UserProfile(String id, String fullName, String email, String phone) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Map<String, String> getSettings() { return settings; }
    public Map<String, Object> getConsents() { return consents; }
    public boolean isAnonymized() { return isAnonymized; }
    public void setAnonymized(boolean anonymized) { isAnonymized = anonymized; }
}