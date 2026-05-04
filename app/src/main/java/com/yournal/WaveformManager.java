package com.yournal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WaveformManager {
    private static final String TAG = "WaveformManager";
    private static final int AMPLITUDE_SAMPLE_INTERVAL_MS = 100;

    private int currentAmplitude = 0;
    private final List<Float> collectedAmplitudes = Collections.synchronizedList(new ArrayList<>());
    private long lastAmplitudeSampleTime = 0;
    private WaveformUpdateListener listener;

    public interface WaveformUpdateListener {
        void onAmplitudeUpdate(int amplitude);
    }

    public void setListener(WaveformUpdateListener listener) {
        this.listener = listener;
    }

    public void reset() {
        currentAmplitude = 0;
        collectedAmplitudes.clear();
        lastAmplitudeSampleTime = 0;
    }

    public void processBuffer(byte[] buffer) {
        int maxAmp = 0;
        for (int i = 0; i < buffer.length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            int abs = Math.abs(sample);
            if (abs > maxAmp) maxAmp = abs;
        }
        currentAmplitude = maxAmp;

        if (listener != null) {
            listener.onAmplitudeUpdate(maxAmp);
        }

        long now = System.currentTimeMillis();
        if (now - lastAmplitudeSampleTime >= AMPLITUDE_SAMPLE_INTERVAL_MS) {
            collectedAmplitudes.add(maxAmp / 32767f);
            lastAmplitudeSampleTime = now;
        }
    }

    public int getCurrentAmplitude() {
        return currentAmplitude;
    }

    public List<Float> getCollectedAmplitudes() {
        synchronized (collectedAmplitudes) {
            return new ArrayList<>(collectedAmplitudes);
        }
    }
}
