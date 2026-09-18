package com.example.rummypulse.ui.playerconsolidation;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.rummypulse.utils.ProfileAvatarBinder;
import com.example.rummypulse.utils.UserProfileIndex;

import java.util.Map;

final class ConsolidationPlayerAvatarBinder {

    private ConsolidationPlayerAvatarBinder() {
    }

    static void bind(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            ConsolidatedPlayerGroup group,
            @Nullable Map<String, String> photoUrlByUserId,
            @Nullable Map<String, Long> profileVersionByUserId,
            @Nullable String displayName) {
        String userId = resolveUserId(group);
        ProfileAvatarBinder.bindWithPhotoUrl(
                itemView,
                avatarImage,
                avatarInitial,
                displayName,
                resolvePhotoUrl(group, photoUrlByUserId),
                UserProfileIndex.profileVersionForUserId(userId, profileVersionByUserId),
                null,
                false,
                null,
                null);
    }

    @Nullable
    private static String resolveUserId(ConsolidatedPlayerGroup group) {
        if (group == null) {
            return null;
        }
        for (GamePlayerEntry member : group.getMembers()) {
            String userId = member.getUserId();
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
        }
        return null;
    }

    @Nullable
    private static String resolvePhotoUrl(
            ConsolidatedPlayerGroup group,
            @Nullable Map<String, String> photoUrlByUserId) {
        if (group == null || photoUrlByUserId == null || photoUrlByUserId.isEmpty()) {
            return null;
        }
        for (GamePlayerEntry member : group.getMembers()) {
            String userId = member.getUserId();
            if (userId == null || userId.isEmpty()) {
                continue;
            }
            String photoUrl = photoUrlByUserId.get(userId);
            if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                return photoUrl.trim();
            }
        }
        return null;
    }
}
