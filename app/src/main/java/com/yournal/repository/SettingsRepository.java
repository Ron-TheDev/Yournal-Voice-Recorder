package com.yournal.repository;

import android.content.Context;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class SettingsRepository {
    private static SettingsRepository instance;
    private final RxDataStore<Preferences> dataStore;

    public static synchronized SettingsRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsRepository(context.getApplicationContext());
        }
        return instance;
    }

    private SettingsRepository(Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(context, "yournal_settings").build();
    }

    SettingsRepository(RxDataStore<Preferences> dataStore) {
        this.dataStore = dataStore;
    }

    static void resetInstanceForTests() {
        instance = null;
    }

    // Theme modes: 0: Light, 1: Dark, 2: System
    private final Preferences.Key<Integer> THEME_MODE = PreferencesKeys.intKey("theme_mode");
    private final Preferences.Key<Integer> RECORDING_BITRATE = PreferencesKeys.intKey("recording_bitrate"); // in kbps
    private final Preferences.Key<String> RECORDING_FORMAT = PreferencesKeys.stringKey("recording_format"); // currently "m4a" only
    private final Preferences.Key<Boolean> AUTO_TRANSCRIBE = PreferencesKeys.booleanKey("auto_transcribe");
    private final Preferences.Key<Boolean> BIOMETRICS = PreferencesKeys.booleanKey("biometrics_required");
    private final Preferences.Key<Boolean> PREVENT_SCREENSHOTS = PreferencesKeys.booleanKey("prevent_screenshots");
    
    // Audio Preference Keys
    private final Preferences.Key<Boolean> RECORD_STEREO = PreferencesKeys.booleanKey("record_stereo");
    private final Preferences.Key<Boolean> BLUETOOTH_MIC = PreferencesKeys.booleanKey("bluetooth_mic");
    private final Preferences.Key<Boolean> BLOCK_CALLS = PreferencesKeys.booleanKey("block_calls");
    private final Preferences.Key<String> STORAGE_URI = PreferencesKeys.stringKey("recording_storage_uri");
    private final Preferences.Key<Boolean> LIVE_NOTIFICATION = PreferencesKeys.booleanKey("live_notification");
    private final Preferences.Key<Integer> ACCENT_COLOR = PreferencesKeys.intKey("accent_color"); // 0 for Dynamic, or Color Int
    private final Preferences.Key<Boolean> HAPTIC_FEEDBACK = PreferencesKeys.booleanKey("haptic_feedback");
    private final Preferences.Key<Boolean> NOISE_CANCELLATION = PreferencesKeys.booleanKey("noise_cancellation");
    private final Preferences.Key<String> PREFERRED_MIC_ID = PreferencesKeys.stringKey("preferred_mic_id");
    private final Preferences.Key<String> TRANSCRIPTION_MODEL = PreferencesKeys.stringKey("transcription_model");

    // Read streams
    public Flowable<Integer> getThemeMode() {
        return dataStore.data().map(prefs -> prefs.contains(THEME_MODE) ? prefs.get(THEME_MODE) : 2);
    }
    
    public Flowable<Integer> getRecordingBitrate() {
        return dataStore.data().map(prefs -> prefs.contains(RECORDING_BITRATE) ? prefs.get(RECORDING_BITRATE) : 128);
    }

    public Flowable<String> getRecordingFormat() {
        return dataStore.data().map(prefs -> prefs.contains(RECORDING_FORMAT) ? prefs.get(RECORDING_FORMAT) : "m4a");
    }

    public Flowable<Boolean> getAutoTranscribe() {
        return dataStore.data().map(prefs -> prefs.contains(AUTO_TRANSCRIBE) ? prefs.get(AUTO_TRANSCRIBE) : true);
    }
    
    public Flowable<Boolean> getBiometrics() {
        return dataStore.data().map(prefs -> prefs.contains(BIOMETRICS) ? prefs.get(BIOMETRICS) : false);
    }
    
    public Flowable<Boolean> getPreventScreenshots() {
        return dataStore.data().map(prefs -> prefs.contains(PREVENT_SCREENSHOTS) ? prefs.get(PREVENT_SCREENSHOTS) : false);
    }
    
    public Flowable<Boolean> getRecordStereo() {
        return dataStore.data().map(prefs -> prefs.contains(RECORD_STEREO) ? prefs.get(RECORD_STEREO) : false);
    }
    
    public Flowable<Boolean> getBluetoothMic() {
        return dataStore.data().map(prefs -> prefs.contains(BLUETOOTH_MIC) ? prefs.get(BLUETOOTH_MIC) : false);
    }
    
    public Flowable<Boolean> getBlockCalls() {
        return dataStore.data().map(prefs -> prefs.contains(BLOCK_CALLS) ? prefs.get(BLOCK_CALLS) : false);
    }

    public Flowable<String> getStorageUri() {
        return dataStore.data().map(prefs -> prefs.contains(STORAGE_URI) ? prefs.get(STORAGE_URI) : "");
    }

    public Flowable<Boolean> getLiveNotification() {
        return dataStore.data().map(prefs -> prefs.contains(LIVE_NOTIFICATION) ? prefs.get(LIVE_NOTIFICATION) : true);
    }

    public Flowable<Integer> getAccentColor() {
        return dataStore.data().map(prefs -> prefs.contains(ACCENT_COLOR) ? prefs.get(ACCENT_COLOR) : 0);
    }

    public Flowable<Boolean> getHapticFeedback() {
        return dataStore.data().map(prefs -> prefs.contains(HAPTIC_FEEDBACK) ? prefs.get(HAPTIC_FEEDBACK) : true);
    }

    public Flowable<Boolean> getNoiseCancellation() {
        return dataStore.data().map(prefs -> prefs.contains(NOISE_CANCELLATION) ? prefs.get(NOISE_CANCELLATION) : true);
    }

    public Flowable<String> getPreferredMicId() {
        return dataStore.data().map(prefs -> prefs.contains(PREFERRED_MIC_ID) ? prefs.get(PREFERRED_MIC_ID) : "");
    }

    public Flowable<String> getTranscriptionModel() {
        return dataStore.data().map(prefs -> prefs.contains(TRANSCRIPTION_MODEL) ? prefs.get(TRANSCRIPTION_MODEL) : "vosk");
    }

    // Write operations
    public Single<Preferences> setThemeMode(int mode) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(THEME_MODE, mode);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setRecordingBitrate(int bitrate) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(RECORDING_BITRATE, bitrate);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setRecordingFormat(String format) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(RECORDING_FORMAT, format);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setAutoTranscribe(boolean enabled) {
        return updateBooleanPreference(AUTO_TRANSCRIBE, enabled);
    }
    
    public Single<Preferences> setBiometrics(boolean enabled) {
        return updateBooleanPreference(BIOMETRICS, enabled);
    }
    
    public Single<Preferences> setPreventScreenshots(boolean enabled) {
        return updateBooleanPreference(PREVENT_SCREENSHOTS, enabled);
    }
    
    public Single<Preferences> setRecordStereo(boolean enabled) {
        return updateBooleanPreference(RECORD_STEREO, enabled);
    }

    public Single<Preferences> setBluetoothMic(boolean enabled) {
        return updateBooleanPreference(BLUETOOTH_MIC, enabled);
    }

    public Single<Preferences> setBlockCalls(boolean enabled) {
        return updateBooleanPreference(BLOCK_CALLS, enabled);
    }

    public Single<Preferences> setStorageUri(String uri) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(STORAGE_URI, uri);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setLiveNotification(boolean enabled) {
        return updateBooleanPreference(LIVE_NOTIFICATION, enabled);
    }

    public Single<Preferences> setAccentColor(int color) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(ACCENT_COLOR, color);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setHapticFeedback(boolean enabled) {
        return updateBooleanPreference(HAPTIC_FEEDBACK, enabled);
    }

    public Single<Preferences> setNoiseCancellation(boolean enabled) {
        return updateBooleanPreference(NOISE_CANCELLATION, enabled);
    }

    public Single<Preferences> setPreferredMicId(String id) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREFERRED_MIC_ID, id);
            return Single.just(mutablePreferences);
        });
    }

    public Single<Preferences> setTranscriptionModel(String model) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(TRANSCRIPTION_MODEL, model);
            return Single.just(mutablePreferences);
        });
    }

    private Single<Preferences> updateBooleanPreference(Preferences.Key<Boolean> key, boolean value) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(key, value);
            return Single.just(mutablePreferences);
        });
    }
}
