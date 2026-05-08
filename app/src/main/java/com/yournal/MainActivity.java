package com.yournal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.yournal.databinding.ActivityMainBinding;
import com.yournal.model.YournalEntry;
import com.yournal.repository.SettingsRepository;
import com.yournal.repository.YournalRepository;
import com.yournal.util.HapticHelper;
import com.yournal.util.MotionConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private SettingsRepository settingsRepository;
    private YournalRepository yournalRepository;
    private HapticHelper hapticHelper;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private int currentAccentColor = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsRepository = SettingsRepository.getInstance(this);
        Integer themeMode = settingsRepository.getThemeMode().first(2).blockingGet();
        applyThemeImmediately(themeMode);

        Integer color = settingsRepository.getAccentColor().first(0).blockingGet();
        if (color == 0) {
            DynamicColors.applyToActivityIfAvailable(this);
        }

        super.onCreate(savedInstanceState);

        yournalRepository = new YournalRepository(getApplication());
        hapticHelper = new HapticHelper(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.getRoot().setVisibility(View.INVISIBLE);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = binding.bottomNavigation;

            bottomNav.setOnItemSelectedListener(item -> {
                int targetId = item.getItemId();
                NavDestination currentDestination = navController.getCurrentDestination();
                if (currentDestination != null && currentDestination.getId() == targetId) {
                    return false;
                }

                if (hapticHelper != null) {
                    hapticHelper.vibrateSelection();
                }

                int currentId = currentDestination != null ? currentDestination.getId() : R.id.navigation_home;
                navController.navigate(targetId, null, MotionConfig.buildTopLevelNavOptions(currentId, targetId));
                return true;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                boolean immersive = id == R.id.navigation_recorder || id == R.id.navigation_note_detail;

                if (id == R.id.navigation_home || id == R.id.navigation_recycle_bin || id == R.id.navigation_settings) {
                    if (bottomNav.getSelectedItemId() != id) {
                        bottomNav.getMenu().findItem(id).setChecked(true);
                    }
                    
                    if (id == R.id.navigation_home) {
                        // Stop any background playback when returning to home
                        Intent stopIntent = new Intent(MainActivity.this, RecordingService.class);
                        stopIntent.setAction(RecordingService.ACTION_PLAYBACK_STOP);
                        startService(stopIntent);
                    }
                }

                if (immersive) {
                    bottomNav.setVisibility(View.GONE);
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//                        androidx.core.view.WindowInsetsControllerCompat insetsController =
//                                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
//                        if (insetsController != null) {
//                            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars());
//                            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
//                            insetsController.setSystemBarsBehavior(
//                                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
//                        }
//                    } else {
//                        getWindow().getDecorView().setSystemUiVisibility(
//                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
//                    }
                } else {
                    bottomNav.setVisibility(View.VISIBLE);
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//                        androidx.core.view.WindowInsetsControllerCompat insetsController =
//                                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
//                        if (insetsController != null) {
//                            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars()
//                                    | androidx.core.view.WindowInsetsCompat.Type.navigationBars());
//                        }
//                    } else {
//                        getWindow().getDecorView().setSystemUiVisibility(
//                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
//                    }
                }
            });
        }

        checkBiometrics();
        setupScreenshotBlocking();
        observeTheme();
        observeAccentColor();
        TranscriptionManager.init(getApplicationContext());

        if (color != 0) {
            currentAccentColor = color;
            applyCustomAccentColor(color);
        }

        handleNavigationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigationIntent(intent);
    }

    private void handleNavigationIntent(android.content.Intent intent) {
        if (intent == null) return;

        String shortcut = intent.getStringExtra("shortcut");
        String target = intent.getStringExtra("navigation_target");
        int noteId = intent.getIntExtra("note_id", -1);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;
        NavController navController = navHostFragment.getNavController();

        if ("new_note".equals(shortcut)) {
            navController.navigate(R.id.navigation_note_detail);
        } else if ("new_record".equals(shortcut) || "recorder".equals(target)) {
            Bundle args = null;
            if (noteId != -1) {
                args = new Bundle();
                args.putInt("note_id", noteId);
            }
            if (navController.getCurrentDestination() == null
                    || navController.getCurrentDestination().getId() != R.id.navigation_recorder
                    || noteId != -1) {
                navController.navigate(R.id.navigation_recorder, args);
            }
        } else if (android.content.Intent.ACTION_SEND.equals(intent.getAction())) {
            handleShareIntent(intent, navController);
        }
    }

    private void handleShareIntent(android.content.Intent intent, NavController navController) {
        String type = intent.getType();
        if ("text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT);
            if (sharedText != null) {
                YournalEntry newNote = new YournalEntry();
                newNote.noteTitle = "Shared Note " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date());
                newNote.noteContent = sharedText;
                newNote.dateCreated = System.currentTimeMillis();
                newNote.noteType = "note";

                YournalRepository.databaseWriteExecutor.execute(() -> {
                    long id = yournalRepository.insert(newNote);
                    runOnUiThread(() -> {
                        Bundle args = new Bundle();
                        args.putInt("note_id", (int) id);
                        navController.navigate(R.id.navigation_note_detail, args);
                    });
                });
            }
        } else if (type != null && type.startsWith("audio/")) {
            android.net.Uri audioUri = intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM);
            if (audioUri != null) {
                importSharedAudio(audioUri, navController);
            }
        }
    }

    private void importSharedAudio(android.net.Uri uri, NavController navController) {
        android.widget.Toast.makeText(this, "Importing...", android.widget.Toast.LENGTH_SHORT).show();
        YournalRepository.databaseWriteExecutor.execute(() -> {
            try {
                String fileName = "imported_" + System.currentTimeMillis() + ".m4a";
                File destFile = new File(getExternalFilesDir(null), fileName);

                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while (in != null && (len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }

                YournalEntry newRecording = new YournalEntry();
                newRecording.noteTitle = "Imported Audio " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date());
                newRecording.filePath = destFile.getAbsolutePath();
                newRecording.dateCreated = System.currentTimeMillis();
                newRecording.noteType = "recording";
                newRecording.amplitudes = new java.util.ArrayList<>(); // Amplitudes generated dynamically in RecorderFragment

                long id = yournalRepository.insert(newRecording);
                runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putInt("note_id", (int) id);
                    navController.navigate(R.id.navigation_recorder, args);
                });
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to import shared audio", e);
            }
        });
    }

    private void applyThemeImmediately(int mode) {
        int nightMode;
        switch (mode) {
            case 0:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case 1:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    private void observeAccentColor() {
        disposables.add(settingsRepository.getAccentColor()
                .distinctUntilChanged()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    boolean isDynamic = color == 0;
                    boolean wasDynamic = currentAccentColor == 0;

                    if (currentAccentColor != -1 && isDynamic != wasDynamic) {
                        currentAccentColor = color;
                        recreate();
                        return;
                    }

                    currentAccentColor = color;
                    if (!isDynamic) {
                        applyCustomAccentColor(color);
                    }
                }));
    }

    private void applyCustomAccentColor(int color) {
        boolean isDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int secondaryColor = com.yournal.util.ColorThemeUtils.getSecondaryColor(color, isDark);
        int colorSecondaryContainer = secondaryColor;

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };

        int[] colors = new int[]{
                color,
                colorSecondaryContainer
        };

        android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(states, colors);
        binding.bottomNavigation.setItemIconTintList(colorStateList);
        binding.bottomNavigation.setItemTextColor(colorStateList);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            binding.bottomNavigation.setItemRippleColor(android.content.res.ColorStateList.valueOf(color).withAlpha(30));
        }
    }

    private void observeTheme() {
        disposables.add(settingsRepository.getThemeMode()
                .distinctUntilChanged()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(mode -> {
                    int nightMode;
                    switch (mode) {
                        case 0:
                            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                            break;
                        case 1:
                            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                            break;
                        case 2:
                        default:
                            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                            break;
                    }
                    if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                        AppCompatDelegate.setDefaultNightMode(nightMode);
                    }
                }));
    }

    private void setupScreenshotBlocking() {
        disposables.add(settingsRepository.getPreventScreenshots()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(prevent -> {
                    if (prevent) {
                        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                    } else {
                        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                    }
                }));
    }

    private void checkBiometrics() {
        disposables.add(settingsRepository.getBiometrics()
                .firstOrError()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(enabled -> {
                    if (enabled && isBiometricAvailable()) {
                        showBiometricPrompt();
                    } else {
                        binding.getRoot().setVisibility(View.VISIBLE);
                    }
                }, throwable -> binding.getRoot().setVisibility(View.VISIBLE)));
    }

    private boolean isBiometricAvailable() {
        BiometricManager manager = BiometricManager.from(this);
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricPrompt() {
        binding.getRoot().setVisibility(View.INVISIBLE);

        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                finish();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                binding.getRoot().setVisibility(View.VISIBLE);
            }
        };

        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this), callback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Yournal")
                .setSubtitle("Authenticate to access your notes")
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
        if (hapticHelper != null) {
            hapticHelper.release();
            hapticHelper = null;
        }
    }
}
