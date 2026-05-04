package com.yournal;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TranscriptionManager {
    public interface TranscriptionUpdateListener {
        void onPartialResult(String text);
        void onFinalResult(String text);
    }

    private static final String TAG = "TranscriptionManager";
    private static final float SAMPLE_RATE = 16000.0f;
    private static final String MODEL_ASSET_NAME = "vosk-model-en-us-0.22-lgraph";

    private final Context context;
    private static volatile Model model;
    private static volatile boolean isModelLoading = false;

    private Recognizer streamingRecognizer;
    private TranscriptionUpdateListener streamingListener;
    private String lastPartialSent = "";

    public static synchronized void init(Context context) {
        if (isModelLoading || model != null) return;
        isModelLoading = true;
        initVosk(context.getApplicationContext());
    }

    private static void initVosk(Context context) {
        Completable.fromAction(() -> {
            File modelDir = new File(context.getFilesDir(), MODEL_ASSET_NAME);
            Log.d(TAG, "Ensuring model exists at: " + modelDir.getAbsolutePath());

            if (!modelDir.exists() || !new File(modelDir, "am/final.mdl").exists()) {
                Log.d(TAG, "Model missing or incomplete. Copying from assets...");
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    throw new IOException("Could not create model directory: " + modelDir.getAbsolutePath());
                }
                copyAssetFolder(context.getAssets(), MODEL_ASSET_NAME, modelDir.getAbsolutePath());
                Log.d(TAG, "Asset copy process finished.");
            } else {
                Log.d(TAG, "Model folder already exists and contains am/final.mdl");
            }

            if (!new File(modelDir, "am/final.mdl").exists()) {
                Log.w(TAG, "CRITICAL: am/final.mdl still missing after copy. Check asset folder structure.");
                String[] list = modelDir.list();
                if (list != null) {
                    for (String name : list) {
                        Log.d(TAG, "Found in modelDir: " + name);
                    }
                }
            }

            Log.d(TAG, "Loading Vosk model into memory...");
            model = new Model(modelDir.getAbsolutePath());
            Log.d(TAG, "Vosk model loaded successfully");
        })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> isModelLoading = false,
                        error -> {
                            isModelLoading = false;
                            Log.e(TAG, "Failed to load Vosk model", error);
                        }
                );
    }

    public TranscriptionManager(Context context) {
        this.context = context.getApplicationContext();
        init(this.context);
    }

    private static void copyAssetFolder(AssetManager assets, String assetPath, String destPath) throws IOException {
        String[] files = assets.list(assetPath);
        if (files == null || files.length == 0) {
            return;
        }

        File destDir = new File(destPath);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Could not create asset destination directory: " + destPath);
        }

        for (String file : files) {
            String assetChild = assetPath + "/" + file;
            String destChild = destPath + "/" + file;

            String[] subFiles = assets.list(assetChild);
            if (subFiles != null && subFiles.length > 0) {
                copyAssetFolder(assets, assetChild, destChild);
            } else {
                copyAssetFile(assets, assetChild, destChild);
            }
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, String destPath) throws IOException {
        File dest = new File(destPath);
        if (dest.exists()) return;

        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    public Single<String> transcribe(Uri audioUri) {
        return Single.<String>create(emitter -> {
            if (model == null) {
                emitter.onError(new Exception("Vosk model not ready yet - initializing. Try again in a moment."));
                return;
            }

            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            Recognizer recognizer = null;

            try {
                extractor.setDataSource(context, audioUri, null);

                int audioTrackIndex = -1;
                MediaFormat audioFormat = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat candidate = extractor.getTrackFormat(i);
                    String mime = candidate.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        audioFormat = candidate;
                        break;
                    }
                }

                if (audioTrackIndex == -1 || audioFormat == null) {
                    emitter.onError(new Exception("No audio track found in file"));
                    return;
                }

                extractor.selectTrack(audioTrackIndex);
                String mime = audioFormat.getString(MediaFormat.KEY_MIME);
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(audioFormat, null, null, 0);
                codec.start();

                recognizer = new Recognizer(model, SAMPLE_RATE);
                recognizer.setWords(true);

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputEOS = false;
                StringBuilder result = new StringBuilder();
                int sourceRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

                while (true) {
                    if (!inputEOS) {
                        int inputIndex = codec.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                int sampleSize = extractor.readSampleData(inputBuffer, 0);
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputEOS = true;
                                } else {
                                    codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                    extractor.advance();
                                }
                            }
                        }
                    }

                    int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                    if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                        if (outputBuffer != null && info.size > 0) {
                            byte[] pcmData = new byte[info.size];
                            outputBuffer.get(pcmData);
                            byte[] resampled = com.yournal.util.AudioUtils.resampleTo16k(pcmData, sourceRate);
                            if (recognizer.acceptWaveForm(resampled, resampled.length)) {
                                String text = extractText(recognizer.getResult());
                                if (!text.isEmpty()) result.append(text).append(" ");
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    }
                }

                String finalText = extractText(recognizer.getFinalResult());
                if (!finalText.isEmpty()) result.append(finalText);

                String transcribed = result.toString().trim();
                Log.d(TAG, "Transcription complete: " + transcribed.length() + " chars");
                emitter.onSuccess(transcribed);
            } catch (Exception error) {
                Log.e(TAG, "Transcription failed", error);
                emitter.onError(error);
            } finally {
                if (recognizer != null) recognizer.close();
                if (codec != null) {
                    try { codec.stop(); } catch (Exception ignored) {}
                    codec.release();
                }
                extractor.release();
            }
        }).subscribeOn(Schedulers.io());
    }

    private String extractText(String json) {
        try {
            return new JSONObject(json).optString("text", "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean isModelLoading() {
        return isModelLoading;
    }

    public static Model getModel() {
        return model;
    }

    public void startStreaming(int sampleRate, TranscriptionUpdateListener listener) {
        this.streamingListener = listener;
        this.lastPartialSent = "";
    }

    public void feedStreaming(byte[] pcm, int channelCount, int actualSampleRate) {
        synchronized (this) {
            if (streamingListener == null || model == null) return;
        }

        byte[] processedPcm = pcm;
        if (channelCount == 2) {
            processedPcm = new byte[pcm.length / 2];
            for (int i = 0; i < processedPcm.length / 2; i++) {
                short left = (short) ((pcm[i * 4 + 1] << 8) | (pcm[i * 4] & 0xFF));
                short right = (short) ((pcm[i * 4 + 3] << 8) | (pcm[i * 4 + 2] & 0xFF));
                short mono = (short) ((left + right) / 2);
                processedPcm[i * 2] = (byte) (mono & 0xFF);
                processedPcm[i * 2 + 1] = (byte) ((mono >> 8) & 0xFF);
            }
        }

        byte[] resampled = com.yournal.util.AudioUtils.resampleTo16k(processedPcm, actualSampleRate);
        try {
            synchronized (this) {
                if (streamingListener == null) return;

                if (streamingRecognizer == null) {
                    streamingRecognizer = new Recognizer(model, SAMPLE_RATE);
                    streamingRecognizer.setWords(true);
                }

                if (streamingRecognizer.acceptWaveForm(resampled, resampled.length)) {
                    String text = extractText(streamingRecognizer.getResult());
                    if (!text.isEmpty()) {
                        streamingListener.onFinalResult(text);
                        lastPartialSent = "";
                    }
                } else {
                    String hypothesis = streamingRecognizer.getPartialResult();
                    try {
                        JSONObject json = new JSONObject(hypothesis);
                        String partial = json.optString("partial", "");
                        if (!partial.isEmpty() && !partial.equals(lastPartialSent)) {
                            lastPartialSent = partial;
                            streamingListener.onPartialResult(partial);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "Vosk streaming error", error);
        }
    }

    public void stopStreaming() {
        synchronized (this) {
            if (streamingRecognizer != null) {
                try {
                    String finalText = extractText(streamingRecognizer.getFinalResult());
                    if (!finalText.isEmpty() && streamingListener != null) {
                        streamingListener.onFinalResult(finalText);
                    }
                    streamingRecognizer.close();
                } catch (Exception ignored) {
                }
                streamingRecognizer = null;
            }
            streamingListener = null;
        }
    }
}
