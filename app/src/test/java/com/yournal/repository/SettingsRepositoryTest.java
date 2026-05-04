package com.yournal.repository;

import androidx.test.core.app.ApplicationProvider;

import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsRepositoryTest {

    @Test
    public void defaultsMatchExpectedValues() {
        SettingsRepository repository = newRepository();

        assertEquals(Integer.valueOf(2), repository.getThemeMode().blockingFirst());
        assertEquals(Integer.valueOf(128), repository.getRecordingBitrate().blockingFirst());
        assertEquals("m4a", repository.getRecordingFormat().blockingFirst());
        assertTrue(repository.getAutoTranscribe().blockingFirst());
        assertFalse(repository.getBiometrics().blockingFirst());
        assertFalse(repository.getPreventScreenshots().blockingFirst());
        assertFalse(repository.getRecordStereo().blockingFirst());
        assertFalse(repository.getBluetoothMic().blockingFirst());
        assertFalse(repository.getBlockCalls().blockingFirst());
        assertEquals("", repository.getStorageUri().blockingFirst());
        assertTrue(repository.getLiveNotification().blockingFirst());
        assertEquals(Integer.valueOf(0), repository.getAccentColor().blockingFirst());
        assertTrue(repository.getHapticFeedback().blockingFirst());
        assertTrue(repository.getNoiseCancellation().blockingFirst());
        assertEquals("", repository.getPreferredMicId().blockingFirst());
        assertEquals("vosk", repository.getTranscriptionModel().blockingFirst());
    }

    @Test
    public void singlePreferenceWritePersists() {
        SettingsRepository repository = newRepository();

        repository.setThemeMode(1).blockingGet();
        assertEquals(Integer.valueOf(1), repository.getThemeMode().blockingFirst());
    }

    @Test
    public void transcriptionModelIsStoredAsVoskByDefaultAndCanBeReset() {
        SettingsRepository repository = newRepository();

        repository.setTranscriptionModel("vosk").blockingGet();
        assertEquals("vosk", repository.getTranscriptionModel().blockingFirst());
    }

    private SettingsRepository newRepository() {
        String name = "settings_test_" + UUID.randomUUID();
        return new SettingsRepository(new RxPreferenceDataStoreBuilder(
                ApplicationProvider.getApplicationContext(),
                name).build());
    }
}
