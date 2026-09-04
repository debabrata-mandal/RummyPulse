package com.example.rummypulse.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.rummypulse.MinimumVersionActivity;
import com.example.rummypulse.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Inflates every layout that moved from android:tint to app:tint on the device under test.
 *
 * <p>app:tint is only honoured when AppCompat swaps ImageView for AppCompatImageView during
 * inflation, so this asserts that swap actually happens and that the tints resolve. Running it on
 * minSdk hardware also catches drawable attributes the older resource parser rejects.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LayoutInflationCompatTest {

    /** Total app:tint attributes declared across the layouts below. */
    private static final int EXPECTED_TINTED_VIEWS = 56;

    private static final int[] LAYOUTS = {
            R.layout.activity_join_game,
            R.layout.content_game_view_mode,
            R.layout.dialog_action_confirmation,
            R.layout.dialog_announcements,
            R.layout.dialog_app_info,
            R.layout.dialog_confirm_add_player,
            R.layout.dialog_correct_player_picker,
            R.layout.dialog_create_game,
            R.layout.dialog_delete_player,
            R.layout.dialog_edit_game_economics,
            R.layout.dialog_enter_round_score,
            R.layout.dialog_exit_edit_mode,
            R.layout.dialog_link_players,
            R.layout.dialog_map_player,
            R.layout.dialog_pick_past_round,
            R.layout.dialog_pin_input,
            R.layout.dialog_players_list,
            R.layout.dialog_qr_code,
            R.layout.dialog_rebuild_report_month,
            R.layout.dialog_transfer_access_pin,
            R.layout.dialog_transfer_mapping,
            R.layout.dialog_unlink_players,
            R.layout.dialog_update_available,
            R.layout.item_balance_adjustment,
            R.layout.item_correct_player,
            R.layout.item_dashboard_game,
            R.layout.item_map_user,
            R.layout.item_player_card,
            // Not retinted, but guards the gradient angle that crashed pre-API 29 inflation.
            R.layout.fragment_dashboard,
    };

    @Test
    public void allRetintedLayoutsInflateWithAppCompatImageViews() {
        List<String> failures = new ArrayList<>();
        int[] tinted = new int[1];

        // Any AppCompatActivity gives us the real AppCompat inflater; this one has no Firebase
        // or auth dependencies, so it starts cleanly under test.
        try (ActivityScenario<MinimumVersionActivity> scenario =
                     ActivityScenario.launch(MinimumVersionActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout container = new FrameLayout(activity);
                for (int layout : LAYOUTS) {
                    String name = activity.getResources().getResourceEntryName(layout);
                    View root;
                    try {
                        root = activity.getLayoutInflater().inflate(layout, container, false);
                    } catch (Throwable t) {
                        failures.add(name + ": inflation failed -> " + t);
                        continue;
                    }
                    inspect(root, name, failures, tinted);
                }
            });
        }

        if (!failures.isEmpty()) {
            fail("Layout compatibility problems:\n" + String.join("\n", failures));
        }
        assertTrue(
                "Expected at least " + EXPECTED_TINTED_VIEWS + " tinted views, found " + tinted[0],
                tinted[0] >= EXPECTED_TINTED_VIEWS);
    }

    private void inspect(View view, String layout, List<String> failures, int[] tinted) {
        if (view instanceof ImageView) {
            // A bare ImageView/ImageButton means AppCompat did not swap the class during
            // inflation, which is the only way app:tint silently stops working. Library
            // subclasses created in code (e.g. the SwipeRefreshLayout spinner) are not from XML.
            Class<?> type = view.getClass();
            if (type == ImageView.class || type == ImageButton.class) {
                failures.add(layout + ": " + type.getName()
                        + " was not swapped for an AppCompat view, so app:tint would be ignored");
            }
            if (((ImageView) view).getImageTintList() != null) {
                tinted[0]++;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                inspect(group.getChildAt(i), layout, failures, tinted);
            }
        }
    }
}
