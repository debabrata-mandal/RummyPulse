package com.example.rummypulse.data;

/**
 * Status values stored in {@code gameViewApprovals_v2} documents.
 */
public enum GameViewApprovalStatus {
    REQUESTED("requested"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String firestoreValue;

    GameViewApprovalStatus(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    public String getFirestoreValue() {
        return firestoreValue;
    }

    public static GameViewApprovalStatus fromFirestore(String value) {
        if (value == null) {
            return REQUESTED;
        }
        for (GameViewApprovalStatus status : values()) {
            if (status.firestoreValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return REQUESTED;
    }
}
