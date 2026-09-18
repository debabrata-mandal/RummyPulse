package com.example.rummypulse.utils;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

/** Shared Glide loading for profile photos with profileVersion cache busting. */
public final class ProfileAvatarLoader {

    private ProfileAvatarLoader() {
    }

    public static void loadCircle(ImageView imageView, @Nullable String photoUrl, long profileVersion) {
        load(imageView, photoUrl, profileVersion, new RequestOptions().circleCrop());
    }

    public static void loadCenterCrop(ImageView imageView, @Nullable String photoUrl, long profileVersion) {
        load(imageView, photoUrl, profileVersion, new RequestOptions().centerCrop());
    }

    private static void load(
            ImageView imageView,
            @Nullable String photoUrl,
            long profileVersion,
            RequestOptions transformOptions) {
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            return;
        }
        RequestOptions options = transformOptions
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .signature(new ObjectKey(profileVersion > 0L ? profileVersion : photoUrl))
                .timeout(10000);
        Glide.with(imageView)
                .load(photoUrl.trim())
                .apply(options)
                .into(imageView);
    }
}
