package com.yournal;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {

    @Test
    public void launchesAndShowsHomeDestination() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.nav_host_fragment));
                androidx.navigation.fragment.NavHostFragment navHostFragment =
                        (androidx.navigation.fragment.NavHostFragment) activity.getSupportFragmentManager()
                                .findFragmentById(R.id.nav_host_fragment);
                assertNotNull(navHostFragment);
                assertNotNull(navHostFragment.getNavController().getCurrentDestination());
                assertEquals(R.id.navigation_home, navHostFragment.getNavController().getCurrentDestination().getId());
            });
        }
    }
}
