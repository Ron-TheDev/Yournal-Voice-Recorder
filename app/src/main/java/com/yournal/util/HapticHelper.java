package com.yournal.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HapticHelper {

    private final Vibrator vibrator;
    private final com.yournal.repository.SettingsRepository settingsRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private volatile boolean hapticEnabled = true;

    public HapticHelper(Context context) {
        this.settingsRepository = com.yournal.repository.SettingsRepository.getInstance(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        disposables.add(settingsRepository.getHapticFeedback()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> hapticEnabled = value, throwable -> hapticEnabled = true));
    }

    private boolean isEnabled() {
        return hapticEnabled;
    }

    public void vibrateStart() {
        if (!isEnabled()) return;
        // Short vibration
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    public void vibratePause() {
        if (!isEnabled()) return;
        // Soft double pulse
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] pattern = {0, 40, 100, 40};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(new long[]{0, 40, 100, 40}, -1);
            }
        }
    }

    public void vibrateStop() {
        if (!isEnabled()) return;
        // Firm confirmation
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(100);
            }
        }
    }

    public void vibrateSelection() {
        if (!isEnabled()) return;
        // Light tap for selection
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        }
    }

    public void release() {
        disposables.clear();
    }
}
