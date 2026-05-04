package com.yournal.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.view.ViewCompat;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.FragmentNavigator;

import com.google.android.material.transition.MaterialContainerTransform;
import com.yournal.R;

public final class MotionConfig {

    public static final String ARG_MOTION_ACCENT_COLOR = "motion_accent_color";

    private static final String TRANSITION_NEW_NOTE = "yournal:new_note";
    private static final String TRANSITION_NEW_RECORDING = "yournal:new_recording";
    private static final String TRANSITION_NOTE_PREFIX = "yournal:note:";
    private static final String TRANSITION_RECORDING_PREFIX = "yournal:recording:";

    private MotionConfig() {
    }

    public static int getDurationMs(Context context) {
        return context.getResources().getInteger(R.integer.motion_duration_ms);
    }

    public static String newNoteTransitionName() {
        return TRANSITION_NEW_NOTE;
    }

    public static String newRecordingTransitionName() {
        return TRANSITION_NEW_RECORDING;
    }

    public static String noteTransitionName(int noteId) {
        return TRANSITION_NOTE_PREFIX + noteId;
    }

    public static String recordingTransitionName(int noteId) {
        return TRANSITION_RECORDING_PREFIX + noteId;
    }

    public static void assignTransitionName(View view, String transitionName) {
        ViewCompat.setTransitionName(view, transitionName);
    }

    public static FragmentNavigator.Extras sharedElementExtras(View view, String transitionName) {
        return new FragmentNavigator.Extras.Builder()
                .addSharedElement(view, transitionName)
                .build();
    }

    public static int getTopLevelIndex(int destinationId) {
        if (destinationId == R.id.navigation_home) return 0;
        if (destinationId == R.id.navigation_recycle_bin) return 1;
        if (destinationId == R.id.navigation_settings) return 2;
        return -1;
    }

    public static boolean isForwardNavigation(int currentDestinationId, int targetDestinationId) {
        if (currentDestinationId == R.id.navigation_settings
                && targetDestinationId == R.id.navigation_recycle_bin) {
            return false;
        }
        int currentIndex = getTopLevelIndex(currentDestinationId);
        int targetIndex = getTopLevelIndex(targetDestinationId);
        if (currentIndex == -1 || targetIndex == -1) {
            return true;
        }
        return targetIndex > currentIndex;
    }

    public static NavOptions buildTopLevelNavOptions(int currentDestinationId, int targetDestinationId) {
        boolean forward = isForwardNavigation(currentDestinationId, targetDestinationId);
        int enterAnim = forward ? R.anim.motion_slide_in_forward : R.anim.motion_slide_in_backward;
        int exitAnim = forward ? R.anim.motion_slide_out_forward : R.anim.motion_slide_out_backward;
        int popEnterAnim = forward ? R.anim.motion_slide_in_backward : R.anim.motion_slide_in_forward;
        int popExitAnim = forward ? R.anim.motion_slide_out_backward : R.anim.motion_slide_out_forward;

        return new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(R.id.navigation_home, false, true)
                .setEnterAnim(enterAnim)
                .setExitAnim(exitAnim)
                .setPopEnterAnim(popEnterAnim)
                .setPopExitAnim(popExitAnim)
                .build();
    }

    public static MaterialContainerTransform createContainerTransform(
            Context context,
            boolean entering,
            @ColorInt int accentColor,
            @ColorInt int surfaceColor) {
        MaterialContainerTransform transform = new MaterialContainerTransform(context, entering);
        transform.setDuration(getDurationMs(context));
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setDrawingViewId(android.R.id.content);
        transform.setContainerColor(accentColor);
        transform.setStartContainerColor(accentColor);
        transform.setEndContainerColor(surfaceColor);
        return transform;
    }
}
