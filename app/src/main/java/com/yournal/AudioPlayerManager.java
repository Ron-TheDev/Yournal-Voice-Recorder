package com.yournal;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

public class AudioPlayerManager {
    private static final String TAG = "AudioPlayerManager";
    private MediaPlayer mediaPlayer;
    private OnPlaybackListener listener;

    public interface OnPlaybackListener {
        void onProgressUpdate(int currentPosition, int duration);
        void onCompletion();
        void onError(String message);
    }

    public void setOnPlaybackListener(OnPlaybackListener listener) {
        this.listener = listener;
    }

    public void startPlaying(android.content.Context context, String filePath) {
        stopPlaying();
        
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setOnCompletionListener(mp -> {
                if (listener != null) listener.onCompletion();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Playback error what=" + what + " extra=" + extra);
                if (listener != null) listener.onError("Playback failed");
                stopPlaying();
                return true;
            });
            android.net.Uri uri = android.net.Uri.parse(filePath);
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "prepare() failed", e);
            stopPlaying();
            if (listener != null) listener.onError("Could not play audio file");
        }
    }

    public void pausePlaying() {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "pausePlaying failed", e);
        }
    }

    public void resumePlaying() {
        if (mediaPlayer == null) return;
        try {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "resumePlaying failed", e);
            if (listener != null) listener.onError("Could not resume playback");
        }
    }

    public void stopPlaying() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "stopPlaying ignored state issue", e);
            } finally {
                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            Log.w(TAG, "getCurrentPosition failed", e);
            return 0;
        }
    }

    public int getDuration() {
        if (mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            Log.w(TAG, "getDuration failed", e);
            return 0;
        }
    }
    
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(position);
            } catch (IllegalStateException e) {
                Log.w(TAG, "seekTo failed", e);
            }
        }
    }
}
