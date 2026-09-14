package com.example.rummypulse.data;

/** Decides whether this client may open data guarded by the Firestore schema marker. */
public final class SchemaCompatibilityPolicy {

    private SchemaCompatibilityPolicy() {
    }

    /**
     * Exact matching is intentional: an older client must also stop if a future schema is newer
     * than the model it understands.
     */
    public static boolean canOpen(Long serverSchemaVersion) {
        return serverSchemaVersion != null
                && serverSchemaVersion == GameDataSchema.CURRENT_VERSION;
    }
}
