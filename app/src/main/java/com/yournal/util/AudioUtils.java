package com.yournal.util;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioUtils {
    private static final String TAG = "AudioUtils";

    public static int getBestSupportedSampleRate() {
        int[] candidates = {48000, 44100, 32000, 16000, 8000};
        for (int rate : candidates) {
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                // Double check if we can actually create the AudioRecord
                AudioRecord testRecord = null;
                try {
                    testRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "Best supported sample rate found: " + rate);
                        testRecord.release();
                        return rate; // first working = best
                    }
                    testRecord.release();
                } catch (Exception ignored) {
                }
            }
        }
        return 16000; // safe fallback
    }

    /**
     * Resamples PCM 16-bit mono data from sourceRate to 16000Hz using simple linear interpolation / decimation.
     * This is a basic implementation suitable for speech recognition.
     */
    public static byte[] resampleTo16k(byte[] sourceData, int sourceRate) {
        if (sourceRate == 16000) {
            return sourceData;
        }

        int sourceSamples = sourceData.length / 2;
        double ratio = (double) sourceRate / 16000.0;
        int targetSamples = (int) (sourceSamples / ratio);
        byte[] targetData = new byte[targetSamples * 2];

        for (int i = 0; i < targetSamples; i++) {
            double sourcePos = i * ratio;
            int index = (int) sourcePos;
            double fraction = sourcePos - index;

            if (index + 1 < sourceSamples) {
                short s0 = getShort(sourceData, index * 2);
                short s1 = getShort(sourceData, (index + 1) * 2);
                short interpolated = (short) (s0 + (s1 - s0) * fraction);
                setShort(targetData, i * 2, interpolated);
            } else if (index < sourceSamples) {
                short s0 = getShort(sourceData, index * 2);
                setShort(targetData, i * 2, s0);
            }
        }

        return targetData;
    }

    private static short getShort(byte[] data, int index) {
        return (short) ((data[index] & 0xFF) | (data[index + 1] << 8));
    }

    private static void setShort(byte[] data, int index, short value) {
        data[index] = (byte) (value & 0xFF);
        data[index + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public static java.util.List<android.media.AudioDeviceInfo> getAvailableMicrophones(android.content.Context context) {
        android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(android.content.Context.AUDIO_SERVICE);
        java.util.List<android.media.AudioDeviceInfo> mics = new java.util.ArrayList<>();
        if (audioManager != null) {
            android.media.AudioDeviceInfo[] devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS);
            for (android.media.AudioDeviceInfo device : devices) {
                if (device.isSource()) {
                    mics.add(device);
                }
            }
        }
        return mics;
    }

    public static String getMicLabel(android.media.AudioDeviceInfo device) {
        int id = device.getId();
        int type = device.getType();
        CharSequence name = "";
        String address = "";
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            name = device.getProductName();
            address = device.getAddress(); // Can contain "bottom", "back", "top"
        }

        String typeName;
        switch (type) {
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC:
                typeName = "Built-in Mic";
                if (address != null && !address.isEmpty()) {
                    typeName += " (" + address + ")";
                }
                break;
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
                typeName = "Wired Headset Mic";
                break;
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                typeName = "Bluetooth Mic";
                break;
            case android.media.AudioDeviceInfo.TYPE_USB_DEVICE:
            case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
                typeName = "USB Mic";
                break;
            default:
                typeName = "Other (" + type + ")";
                break;
        }

        String label = "ID " + id + ": " + typeName;
        if (name != null && name.length() > 0 && !name.toString().equals("null")) {
            label += " - " + name;
        }
        return label;
    }
}
