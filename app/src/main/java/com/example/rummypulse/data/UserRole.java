package com.example.rummypulse.data;

/**
 * Enum representing user roles in the application
 */
public enum UserRole {
    REGULAR_USER("regular_user"),
    ADMIN_USER("admin_user");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Get UserRole from string value
     * @param value String representation of the role
     * @return UserRole enum or REGULAR_USER as default
     */
    public static UserRole fromString(String value) {
        if (value != null) {
            for (UserRole role : UserRole.values()) {
                if (role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
        }
        return REGULAR_USER; // Default role
    }

    @Override
    public String toString() {
        return value;
    }
}
