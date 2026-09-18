package com.example.rummypulse.utils;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.bumptech.glide.signature.ObjectKey;

import java.util.Map;

public final class ProfileAvatarBinder {

    private ProfileAvatarBinder() {
    }

    public static void bind(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String userId,
            @Nullable String displayName,
            @Nullable Map<String, String> photoUrlByUserId) {
        bind(itemView, avatarImage, avatarInitial, userId, displayName, photoUrlByUserId, null, null);
    }

    public static void bind(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String userId,
            @Nullable String displayName,
            @Nullable Map<String, String> photoUrlByUserId,
            @Nullable ColorStateList avatarInitialTint) {
        bind(itemView, avatarImage, avatarInitial, userId, displayName, photoUrlByUserId, null, avatarInitialTint);
    }

    public static void bind(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String userId,
            @Nullable String displayName,
            @Nullable Map<String, String> photoUrlByUserId,
            @Nullable Map<String, Long> profileVersionByUserId,
            @Nullable ColorStateList avatarInitialTint) {
        if (avatarInitial == null) {
            return;
        }
        avatarInitial.setText(DisplayNameUtils.initials(displayName));
        if (avatarInitialTint != null) {
            avatarInitial.setBackgroundTintList(avatarInitialTint);
        }

        String photoUrl = resolvePhotoUrl(userId, photoUrlByUserId);
        if (photoUrl == null || avatarImage == null) {
            showInitialAvatar(avatarImage, avatarInitial);
            return;
        }

        long profileVersion = UserProfileIndex.profileVersionForUserId(userId, profileVersionByUserId);
        loadPhoto(avatarImage, avatarInitial, photoUrl, profileVersion, false, null, null);
    }

    public static void bindWithPhotoUrl(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String displayName,
            @Nullable String photoUrl,
            @Nullable ColorStateList avatarInitialTint) {
        bindWithPhotoUrl(
                itemView,
                avatarImage,
                avatarInitial,
                displayName,
                photoUrl,
                0L,
                avatarInitialTint,
                false,
                null,
                null);
    }

    public static void bindWithPhotoUrl(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String displayName,
            @Nullable String photoUrl,
            @Nullable ColorStateList avatarInitialTint,
            boolean hideInitialsWhileLoading,
            @Nullable Runnable onPhotoReady,
            @Nullable Runnable onInitialsFallback) {
        bindWithPhotoUrl(
                itemView,
                avatarImage,
                avatarInitial,
                displayName,
                photoUrl,
                0L,
                avatarInitialTint,
                hideInitialsWhileLoading,
                onPhotoReady,
                onInitialsFallback);
    }

    public static void bindWithPhotoUrl(
            View itemView,
            ImageView avatarImage,
            TextView avatarInitial,
            @Nullable String displayName,
            @Nullable String photoUrl,
            long profileVersion,
            @Nullable ColorStateList avatarInitialTint,
            boolean hideInitialsWhileLoading,
            @Nullable Runnable onPhotoReady,
            @Nullable Runnable onInitialsFallback) {
        if (avatarInitial == null) {
            return;
        }
        avatarInitial.setText(DisplayNameUtils.initials(displayName));
        if (avatarInitialTint != null) {
            avatarInitial.setBackgroundTintList(avatarInitialTint);
        }

        if (photoUrl == null || avatarImage == null) {
            showInitialAvatar(avatarImage, avatarInitial);
            if (onInitialsFallback != null) {
                onInitialsFallback.run();
            }
            return;
        }

        loadPhoto(
                avatarImage,
                avatarInitial,
                photoUrl,
                profileVersion,
                hideInitialsWhileLoading,
                onPhotoReady,
                onInitialsFallback);
    }

    private static void loadPhoto(
            ImageView avatarImage,
            TextView avatarInitial,
            String photoUrl,
            long profileVersion,
            boolean hideInitialsWhileLoading,
            @Nullable Runnable onPhotoReady,
            @Nullable Runnable onInitialsFallback) {
        Glide.with(avatarImage).clear(avatarImage);
        avatarInitial.setVisibility(hideInitialsWhileLoading ? View.INVISIBLE : View.VISIBLE);
        avatarImage.setVisibility(View.INVISIBLE);
        ObjectKey signature = profileVersion > 0L
                ? new ObjectKey(profileVersion)
                : new ObjectKey(photoUrl.trim());
        Glide.with(avatarImage)
                .load(photoUrl.trim())
                .apply(new RequestOptions()
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(signature)
                        .timeout(10000))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource) {
                        showInitialAvatar(avatarImage, avatarInitial);
                        if (onInitialsFallback != null) {
                            onInitialsFallback.run();
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource dataSource,
                            boolean isFirstResource) {
                        avatarInitial.setVisibility(View.GONE);
                        avatarImage.setVisibility(View.VISIBLE);
                        if (onPhotoReady != null) {
                            onPhotoReady.run();
                        }
                        return false;
                    }
                })
                .into(avatarImage);
    }

    @Nullable
    private static String resolvePhotoUrl(
            @Nullable String userId,
            @Nullable Map<String, String> photoUrlByUserId) {
        if (userId == null || userId.isEmpty() || photoUrlByUserId == null || photoUrlByUserId.isEmpty()) {
            return null;
        }
        String photoUrl = photoUrlByUserId.get(userId);
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            return null;
        }
        return photoUrl.trim();
    }

    private static void showInitialAvatar(@Nullable ImageView avatarImage, TextView avatarInitial) {
        if (avatarImage != null) {
            avatarImage.setVisibility(View.GONE);
        }
        avatarInitial.setVisibility(View.VISIBLE);
    }
}
