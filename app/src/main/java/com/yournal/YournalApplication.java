package com.yournal;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class YournalApplication extends Application {
    private int activityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply Material You dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this);
        
        // Initialize Vosk transcription model early
        TranscriptionManager.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityStarted(android.app.Activity a) { activityCount++; }
            @Override public void onActivityResumed(android.app.Activity a) {}
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) { activityCount--; }
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
    }

    public boolean isAppInForeground() {
        return activityCount > 0;
    }
}
