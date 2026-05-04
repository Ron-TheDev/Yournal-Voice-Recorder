package com.yournal.util;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

public class WaveformGenerator {
    private static final String TAG = "WaveformGenerator";
    private static final int TARGET_SAMPLES = 100;

    public static Single<List<Float>> generate(Context context, String uriString) {
        return Single.create(emitter -> {
            List<Float> amplitudes = new ArrayList<>();
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            ParcelFileDescriptor pfd = null;

            try {
                Uri uri = Uri.parse(uriString);
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd == null) {
                    emitter.onError(new Exception("Could not open file descriptor for URI: " + uriString));
                    return;
                }
                extractor.setDataSource(pfd.getFileDescriptor());

                int trackIndex = -1;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        trackIndex = i;
                        break;
                    }
                }

                if (trackIndex < 0) {
                    emitter.onError(new Exception("No audio track found in file"));
                    return;
                }

                extractor.selectTrack(trackIndex);
                MediaFormat format = extractor.getTrackFormat(trackIndex);
                String mime = format.getString(MediaFormat.KEY_MIME);
                long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;
                
                if (durationUs <= 0) {
                    // Fallback or error
                    emitter.onSuccess(new ArrayList<>());
                    return;
                }

                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null, null, 0);
                codec.start();

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;

                long windowSizeUs = durationUs / TARGET_SAMPLES;
                float currentMax = 0;
                long nextThresholdUs = windowSizeUs;

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        int inputIndex = codec.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            long presentationTimeUs = extractor.getSampleTime();

                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }

                    int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                    if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);

                        while (outputBuffer.remaining() >= 2) {
                            short sample = outputBuffer.getShort();
                            float abs = Math.abs(sample) / 32767f;
                            if (abs > currentMax) currentMax = abs;
                        }

                        if (info.presentationTimeUs >= nextThresholdUs || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            amplitudes.add(currentMax);
                            if (amplitudes.size() >= TARGET_SAMPLES) {
                                // We have enough samples for the view
                                sawOutputEOS = true;
                            } else {
                                currentMax = 0;
                                nextThresholdUs += windowSizeUs;
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                        }
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // ignore
                    }
                }

                // Fill with zeros if we didn't get enough samples (e.g. very short file)
                while (amplitudes.size() < TARGET_SAMPLES) {
                    amplitudes.add(0.0f);
                }

                emitter.onSuccess(amplitudes);

            } catch (Exception e) {
                Log.e(TAG, "Failed to generate waveform: " + e.getMessage(), e);
                emitter.onError(e);
            } finally {
                try {
                    if (codec != null) {
                        codec.stop();
                        codec.release();
                    }
                } catch (Exception ignored) {}
                try {
                    extractor.release();
                } catch (Exception ignored) {}
                try {
                    if (pfd != null) pfd.close();
                } catch (Exception ignored) {}
            }
        });
    }
}
