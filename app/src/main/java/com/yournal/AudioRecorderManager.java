package com.yournal;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioDeviceInfo;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.yournal.util.AudioUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AudioRecorderManager {
    private static final String TAG = "AudioRecorderManager";

    private static final int VOSK_SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG_MONO   = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_STEREO  = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final Context context;
    private final com.yournal.repository.SettingsRepository settingsRepository;

    private AudioRecord audioRecord;
    private NoiseSuppressor noiseSuppressor;
    private MediaCodec encoder;
    private MediaMuxer muxer;
    private int audioTrackIndex = -1;

    // 1 = mono, 2 = stereo
    private int channelCount = 1;
    private int currentChannelConfig = CHANNEL_CONFIG_MONO;

    private Thread captureThread;
    private Thread processingThread;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isPaused    = new AtomicBoolean(false);
    private boolean isTranscriptionOnly = false;

    private final LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(200);

    private String currentFilepath;
    private int actualSampleRate = VOSK_SAMPLE_RATE;
    private AudioFocusRequest audioFocusRequest;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object resourceLock = new Object();

    private final WaveformManager waveformManager = new WaveformManager();
    private final TranscriptionManager transcriptionManager;

    private boolean autoTranscribeEnabled = true;
    private final CompositeDisposable settingsSubscriptions = new CompositeDisposable();
    private volatile boolean preferBluetoothMic = false;
    private volatile boolean blockCallsEnabled = false;
    private volatile String preferredMicId = "";
    private volatile boolean recordStereoEnabled = false;
    private volatile boolean noiseCancellationEnabled = true;
    private volatile int recordingBitrateKbps = 128;
    private volatile boolean autoTranscribeSetting = true;

    // BroadcastReceiver for SCO audio state
    private final android.content.BroadcastReceiver scoReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                Log.d(TAG, "SCO audio connected.");
                handleAudioDeviceChange();
            }
        }
    };

    private final android.media.AudioDeviceCallback audioDeviceCallback = new android.media.AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            handleAudioDeviceChange();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            handleAudioDeviceChange();
        }
    };

    private void handleAudioDeviceChange() {
        if (!isRecording.get() || audioRecord == null) return;
        applyPreferredMic();
    }

    public AudioRecorderManager(Context context) {
        this.context = context;
        this.settingsRepository = com.yournal.repository.SettingsRepository.getInstance(context);
        this.transcriptionManager = new TranscriptionManager(context);
        observeSettings();
    }

    private void observeSettings() {
        settingsSubscriptions.add(settingsRepository.getBluetoothMic()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> {
                            preferBluetoothMic = value;
                            if (isRecording.get()) {
                                if (preferBluetoothMic) {
                                    startBluetoothScoIfNeeded();
                                } else {
                                    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                                    if (audioManager != null) {
                                        audioManager.stopBluetoothSco();
                                        audioManager.setBluetoothScoOn(false);
                                    }
                                }
                            }
                            mainHandler.post(this::handleAudioDeviceChange);
                        },
                        throwable -> Log.w(TAG, "Failed to observe bluetooth mic setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getBlockCalls()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> blockCallsEnabled = value,
                        throwable -> Log.w(TAG, "Failed to observe block calls setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getPreferredMicId()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> preferredMicId = value == null ? "" : value,
                        throwable -> Log.w(TAG, "Failed to observe preferred mic setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getRecordStereo()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> recordStereoEnabled = value,
                        throwable -> Log.w(TAG, "Failed to observe stereo setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getNoiseCancellation()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> noiseCancellationEnabled = value,
                        throwable -> Log.w(TAG, "Failed to observe noise cancellation setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getRecordingBitrate()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> recordingBitrateKbps = value != null && value > 0 ? value : 128,
                        throwable -> Log.w(TAG, "Failed to observe bitrate setting", throwable)));

        settingsSubscriptions.add(settingsRepository.getAutoTranscribe()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .subscribe(value -> autoTranscribeSetting = value,
                        throwable -> Log.w(TAG, "Failed to observe auto-transcribe setting", throwable)));

    }

    public void setTranscriptionListener(TranscriptionManager.TranscriptionUpdateListener listener) {
        this.transcriptionManager.startStreaming(VOSK_SAMPLE_RATE, listener);
    }

    public void setWaveformListener(WaveformManager.WaveformUpdateListener listener) {
        this.waveformManager.setListener(listener);
    }

    // =============================
    // Start / Stop
    // =============================

    public void startRecording() {
        if (isRecording.get()) stopRecording();
        java.io.File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        java.io.File audioFile = new java.io.File(dir, "recording_" + System.currentTimeMillis() + ".m4a");
        startRecording(audioFile.getAbsolutePath());
    }

    public void startRecording(String outputFilePath) {
        if (isRecording.get()) stopRecording();
        isTranscriptionOnly = false;
        currentFilepath = outputFilePath;
        waveformManager.reset();
        try {
            requestAudioFocus();
            actualSampleRate = AudioUtils.getBestSupportedSampleRate();
            startBluetoothScoIfNeeded();
            initAudioRecord();
            initEncoder(outputFilePath);
            autoTranscribeEnabled = autoTranscribeSetting;
            if (autoTranscribeEnabled) initTranscriptionModel();
            startThreads();
        } catch (Exception e) {
            Log.e(TAG, "startRecording failed", e);
            cleanup();
        }
    }

    public void startRecording(java.io.FileDescriptor fd, String displayName) {
        if (isRecording.get()) stopRecording();
        isTranscriptionOnly = false;
        currentFilepath = displayName;
        waveformManager.reset();
        try {
            requestAudioFocus();
            actualSampleRate = AudioUtils.getBestSupportedSampleRate();
            startBluetoothScoIfNeeded();
            initAudioRecord();
            initEncoderFd(fd);
            autoTranscribeEnabled = autoTranscribeSetting;
            if (autoTranscribeEnabled) initTranscriptionModel();
            startThreads();
        } catch (Exception e) {
            Log.e(TAG, "startRecording(FD) failed", e);
            cleanup();
        }
    }

    public void startTranscriptionOnly() {
        if (isRecording.get()) stopRecording();
        isTranscriptionOnly = true;
        currentFilepath = null;
        waveformManager.reset();
        try {
            requestAudioFocus();
            actualSampleRate = AudioUtils.getBestSupportedSampleRate();
            startBluetoothScoIfNeeded();
            initAudioRecord();
            autoTranscribeEnabled = true;
            initTranscriptionModel();
            startThreads();
        } catch (Exception e) {
            Log.e(TAG, "startTranscriptionOnly failed", e);
            cleanup();
        }
    }

    private void startThreads() {
        isRecording.set(true);
        isPaused.set(false);
        registerAudioCallbacks();
        captureThread = new Thread(this::captureLoop, "AudioCaptureThread");
        processingThread = new Thread(this::processingLoop, "AudioProcessingThread");
        captureThread.start();
        processingThread.start();
    }

    // =============================
    // AudioRecord initialization
    // =============================

    private void initAudioRecord() {
        boolean isStereo = recordStereoEnabled;

        if (isStereo) {
            initAudioRecordStereo();
        } else {
            initAudioRecordMono();
        }

        applyPreferredMic();
        applyNoiseSuppressor();
        audioRecord.startRecording();
    }

    private void initAudioRecordStereo() {
        // Try hardware-unprocessed stereo (API 29+) for cleanest capture; fall back to MIC.
        int minBuf = AudioRecord.getMinBufferSize(actualSampleRate, CHANNEL_CONFIG_STEREO, AUDIO_FORMAT);

        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf <= 0) {
            Log.w(TAG, "Hardware stereo not supported on this device — falling back to mono.");
            initAudioRecordMono();
            return;
        }

        int bufferSize = Math.max(minBuf * 2, actualSampleRate * 2 * 2 / 10); // ~100 ms

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                //Some phones only expose stereo mic outputs while recording videos
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.CAMCORDER,
                        actualSampleRate,
                        CHANNEL_CONFIG_STEREO,
                        AUDIO_FORMAT,
                        bufferSize
                );
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "Stereo UNPROCESSED AudioRecord initialized at " + actualSampleRate + " Hz");
                    channelCount = 2;
                    currentChannelConfig = CHANNEL_CONFIG_STEREO;
                    return;
                }
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.w(TAG, "UNPROCESSED source failed, trying MIC for stereo", e);
                if (audioRecord != null) { audioRecord.release(); audioRecord = null; }
            }
        }

        // Fall back to standard MIC + CHANNEL_IN_STEREO
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    actualSampleRate,
                    CHANNEL_CONFIG_STEREO,
                    AUDIO_FORMAT,
                    bufferSize
            );
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "Stereo MIC AudioRecord initialized at " + actualSampleRate + " Hz");
                channelCount = 2;
                currentChannelConfig = CHANNEL_CONFIG_STEREO;
                return;
            }
            audioRecord.release();
            audioRecord = null;
        } catch (Exception e) {
            Log.w(TAG, "Stereo MIC AudioRecord failed, falling back to mono", e);
            if (audioRecord != null) { audioRecord.release(); audioRecord = null; }
        }

        // Final fallback: mono
        Log.w(TAG, "Could not open stereo AudioRecord — falling back to mono.");
        initAudioRecordMono();
    }

    private void initAudioRecordMono() {
        channelCount = 1;
        currentChannelConfig = CHANNEL_CONFIG_MONO;
        int minBuf = AudioRecord.getMinBufferSize(actualSampleRate, CHANNEL_CONFIG_MONO, AUDIO_FORMAT);
        if (minBuf <= 0) minBuf = 8192;
        int bufferSize = Math.max(minBuf * 2, actualSampleRate * 2 / 10);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                actualSampleRate,
                CHANNEL_CONFIG_MONO,
                AUDIO_FORMAT,
                bufferSize
        );
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new RuntimeException("Mono AudioRecord initialization failed");
        }
        Log.d(TAG, "Mono MIC AudioRecord initialized at " + actualSampleRate + " Hz");
    }

    private void applyPreferredMic() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        if (preferBluetoothMic && (preferredMicId.isEmpty() || preferredMicId.equals("Default"))) {
            List<AudioDeviceInfo> mics = AudioUtils.getAvailableMicrophones(context);
            for (AudioDeviceInfo mic : mics) {
                if (mic.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    boolean success = audioRecord.setPreferredDevice(mic);
                    Log.d(TAG, "Preferred device: Bluetooth SCO id=" + mic.getId() + ", success=" + success);
                    return;
                }
            }
            audioRecord.setPreferredDevice(null);
            Log.d(TAG, "Bluetooth mic requested, but none found. Reverting to default.");
        } else if (!preferredMicId.isEmpty() && !preferredMicId.equals("Default")) {
            List<AudioDeviceInfo> mics = AudioUtils.getAvailableMicrophones(context);
            for (AudioDeviceInfo mic : mics) {
                if (String.valueOf(mic.getId()).equals(preferredMicId)) {
                    boolean success = audioRecord.setPreferredDevice(mic);
                    Log.d(TAG, "setPreferredDevice(" + mic.getId() + ") success=" + success);
                    return;
                }
            }
        } else {
            audioRecord.setPreferredDevice(null);
            Log.d(TAG, "Using default microphone device.");
        }
    }

    private void applyNoiseSuppressor() {
        if (!noiseCancellationEnabled) return;
        if (!NoiseSuppressor.isAvailable()) return;
        // Note: NoiseSuppressor may be incompatible with UNPROCESSED source — ignore failure gracefully
        try {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
            if (noiseSuppressor != null) {
                noiseSuppressor.setEnabled(true);
                Log.d(TAG, "NoiseSuppressor enabled");
            }
        } catch (Exception e) {
            Log.w(TAG, "NoiseSuppressor.create failed (may be expected with UNPROCESSED source)", e);
        }
    }

    // =============================
    // Audio Focus (best practices)
    // =============================

    private void requestAudioFocus() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // USAGE_MEDIA + CONTENT_TYPE_SPEECH is the correct pairing for voice recording.
            // AUDIOFOCUS_GAIN tells the system we need sustained focus (stops music) without
            // being overly exclusive — the previous TRANSIENT_EXCLUSIVE was too aggressive.
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        // Only pause on a full loss (e.g. phone call). Survive transient ducks.
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS && !blockCallsEnabled) {
                            pauseRecording();
                        }
                    })
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            //noinspection deprecation
            audioManager.requestAudioFocus(
                    focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS && !blockCallsEnabled) {
                            pauseRecording();
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
    }

    private void startBluetoothScoIfNeeded() {
        if (!preferBluetoothMic) return;
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.startBluetoothSco();
            audioManager.setBluetoothScoOn(true);
        }
    }

    private void registerAudioCallbacks() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
            }
            context.registerReceiver(scoReceiver,
                    new android.content.IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
        }
    }

    // =============================
    // Encoder setup
    // =============================

    private void initEncoder(String path) throws IOException {
        setupEncoderConfiguration();
        muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private void initEncoderFd(java.io.FileDescriptor fd) throws IOException {
        setupEncoderConfiguration();
        muxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private void setupEncoderConfiguration() throws IOException {
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat fmt = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, actualSampleRate, channelCount);
        int bitrate = recordingBitrateKbps * 1000;
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536);
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
    }

    // =============================
    // Transcription model init
    // =============================

    private void initTranscriptionModel() {
        TranscriptionManager.init(context);
    }

    // =============================
    // Capture loop
    // =============================

    private void captureLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        int minBuf = AudioRecord.getMinBufferSize(actualSampleRate, currentChannelConfig, AUDIO_FORMAT);
        // ~50 ms chunks
        int bufferSize = Math.max(minBuf, actualSampleRate * channelCount * 2 / 20);
        if (minBuf <= 0) bufferSize = 8192 * channelCount;

        while (isRecording.get()) {
            if (isPaused.get()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                continue;
            }

            AudioRecord localRecord;
            synchronized (resourceLock) {
                localRecord = audioRecord;
            }

            if (localRecord == null) break;

            byte[] buffer = new byte[bufferSize];
            int bytesRead = localRecord.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                if (!audioQueue.offer(data)) {
                    Log.w(TAG, "Audio queue full, dropping buffer");
                }
            }
        }
    }

    // =============================
    // Processing loop
    // =============================

    private void processingLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean muxerStarted = false;
        long presentationTimeUs = 0;
        final long bytesPerSample = 2; // 16-bit PCM

        while (isRecording.get() || !audioQueue.isEmpty()) {
            byte[] buffer = audioQueue.poll();
            if (buffer == null) {
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                continue;
            }

            // Waveform: process full buffer (works for both mono and interleaved stereo)
            waveformManager.processBuffer(buffer);

            // Transcription: always needs mono PCM.
            // For stereo: extract left channel (even-indexed samples in interleaved L/R).
            if (autoTranscribeEnabled) {
                byte[] monoBuffer = channelCount == 2 ? extractLeftChannel(buffer) : buffer;
                transcriptionManager.feedStreaming(monoBuffer, 1, actualSampleRate);
            }

            // Encoder: send full (possibly stereo) buffer
            if (!isTranscriptionOnly) {
                MediaCodec localEncoder;
                MediaMuxer localMuxer;
                synchronized (resourceLock) {
                    localEncoder = encoder;
                    localMuxer = muxer;
                }

                if (localEncoder != null) {
                    try {
                        int inputIndex = localEncoder.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inputBuffer = localEncoder.getInputBuffer(inputIndex);
                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                inputBuffer.put(buffer);
                                localEncoder.queueInputBuffer(inputIndex, 0, buffer.length, presentationTimeUs, 0);
                                presentationTimeUs += (buffer.length / (bytesPerSample * channelCount))
                                        * (1_000_000L / actualSampleRate);
                            }
                        }
                        // Drain encoder → muxer
                        while (true) {
                            int outputIndex = localEncoder.dequeueOutputBuffer(info, 0);
                            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                if (!muxerStarted && localMuxer != null) {
                                    audioTrackIndex = localMuxer.addTrack(localEncoder.getOutputFormat());
                                    localMuxer.start();
                                    muxerStarted = true;
                                }
                                break;
                            }
                            if (outputIndex >= 0) {
                                ByteBuffer outputBuffer = localEncoder.getOutputBuffer(outputIndex);
                                if (muxerStarted && localMuxer != null && outputBuffer != null
                                        && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    localMuxer.writeSampleData(audioTrackIndex, outputBuffer, info);
                                }
                                localEncoder.releaseOutputBuffer(outputIndex, false);
                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Encoder/Muxer error in processing loop", e);
                    }
                }
            }
        }

        if (!isTranscriptionOnly) {
            drainEncoderEnd();
        }
    }

    /**
     * Extracts the left (first) channel from interleaved stereo PCM 16-bit data.
     * Input: [L0_lo, L0_hi, R0_lo, R0_hi, L1_lo, L1_hi, ...]
     * Output: [L0_lo, L0_hi, L1_lo, L1_hi, ...]
     */
    private byte[] extractLeftChannel(byte[] stereo) {
        byte[] mono = new byte[stereo.length / 2];
        for (int i = 0, j = 0; i < stereo.length; i += 4, j += 2) {
            mono[j]     = stereo[i];     // Left lo byte
            mono[j + 1] = stereo[i + 1]; // Left hi byte
        }
        return mono;
    }

    private void drainEncoderEnd() {
        MediaCodec localEncoder;
        MediaMuxer localMuxer;
        synchronized (resourceLock) {
            localEncoder = encoder;
            localMuxer = muxer;
        }

        if (localEncoder == null) return;
        try {
            int inputIndex = localEncoder.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                localEncoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long timeout = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < timeout) {
                int outputIndex = localEncoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    if (localMuxer != null && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        ByteBuffer outputBuffer = localEncoder.getOutputBuffer(outputIndex);
                        if (outputBuffer != null) {
                            localMuxer.writeSampleData(audioTrackIndex, outputBuffer, info);
                        }
                    }
                    localEncoder.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "drainEncoderEnd failed", e);
        }
    }

    // =============================
    // Pause / Resume / Stop
    // =============================

    public void pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true);
        }
    }

    public void resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            isPaused.set(false);
        }
    }

    public void stopRecording() {
        if (!isRecording.get()) return;
        isRecording.set(false);
        transcriptionManager.stopStreaming();
        if (captureThread != null) {
            try { captureThread.join(2000); } catch (InterruptedException ignored) {}
            captureThread = null;
        }
        if (processingThread != null) {
            try { processingThread.join(2000); } catch (InterruptedException ignored) {}
            processingThread = null;
        }
        cleanup();
    }

    public void release() {
        settingsSubscriptions.clear();
        transcriptionManager.stopStreaming();
    }

    private void cleanup() {
        abandonAudioFocus();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
                }
                context.unregisterReceiver(scoReceiver);
            } catch (Exception ignored) {}
        }

        synchronized (resourceLock) {
            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                audioRecord.release();
                audioRecord = null;
            }
            if (noiseSuppressor != null) {
                try { noiseSuppressor.release(); } catch (Exception ignored) {}
                noiseSuppressor = null;
            }
            if (encoder != null) {
                try { encoder.stop(); } catch (Exception ignored) {}
                encoder.release();
                encoder = null;
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception ignored) {
                }
                muxer = null;
            }
        }
        audioQueue.clear();
    }

    // =============================
    // Getters
    // =============================

    public int getAmplitude() { return waveformManager.getCurrentAmplitude(); }
    public java.util.List<Float> getCollectedAmplitudes() { return waveformManager.getCollectedAmplitudes(); }
    public boolean isRecording() { return isRecording.get(); }
    public boolean isPaused() { return isPaused.get(); }
    public String getCurrentFilepath() { return currentFilepath; }
}
