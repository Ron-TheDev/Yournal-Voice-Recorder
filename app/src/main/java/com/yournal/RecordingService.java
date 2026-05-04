package com.yournal;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "RecordingChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_PLAYBACK_PAUSE = "ACTION_PLAYBACK_PAUSE";
    public static final String ACTION_PLAYBACK_RESUME = "ACTION_PLAYBACK_RESUME";
    public static final String ACTION_PLAYBACK_STOP = "ACTION_PLAYBACK_STOP";

    private final IBinder binder = new LocalBinder();

    private AudioRecorderManager recorder;
    private AudioPlayerManager player;
    private com.yournal.repository.SettingsRepository settings;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables =
            new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private volatile boolean liveNotificationEnabled = true;

    private boolean isPlaybackMode = false;
    private int playbackNoteId = -1;

    private long recordingStartTime = 0;
    private long pausedDuration = 0;
    private long lastPauseTime = 0;

    private String lastSnippet = "";

    public class LocalBinder extends Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    // =========================
    // Lifecycle
    // =========================

    @Override
    public void onCreate() {
        super.onCreate();

        recorder = new AudioRecorderManager(this);
        player = new AudioPlayerManager();
        settings = com.yournal.repository.SettingsRepository.getInstance(this);
        disposables.add(settings.getLiveNotification()
                .distinctUntilChanged()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe(value -> liveNotificationEnabled = value,
                        throwable -> Log.w(TAG, "Failed to observe live notification setting", throwable)));

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

        switch (intent.getAction()) {
            case ACTION_PAUSE: handlePause(); break;
            case ACTION_RESUME: handleResume(); break;
            case ACTION_STOP: stopRecording(); break;
            case ACTION_PLAYBACK_PAUSE: pausePlayback(); break;
            case ACTION_PLAYBACK_RESUME: resumePlayback(); break;
            case ACTION_PLAYBACK_STOP: stopPlayback(); break;
        }

        return START_NOT_STICKY;
    }

    // =========================
    // Recording
    // =========================

    public void startRecording(@Nullable String path) {
        if (recorder.isRecording()) return;

        resetState();

        if (path == null) recorder.startRecording();
        else recorder.startRecording(path);

        recordingStartTime = System.currentTimeMillis();

        startForegroundService("Recording...");
    }

    public void startRecording(java.io.FileDescriptor fd, String displayName) {
        if (recorder.isRecording()) return;

        resetState();
        recorder.startRecording(fd, displayName);
        recordingStartTime = System.currentTimeMillis();
        startForegroundService("Recording...");
    }

    public void setTranscriptionListener(TranscriptionManager.TranscriptionUpdateListener listener) {
        recorder.setTranscriptionListener(listener);
    }

    public java.util.List<Float> getCollectedAmplitudes() {
        return recorder.getCollectedAmplitudes();
    }

    public String getCurrentFilepath() {
        return recorder.getCurrentFilepath();
    }

    public void pauseRecording() {
        if (!recorder.isRecording() || recorder.isPaused()) return;

        lastPauseTime = System.currentTimeMillis();
        recorder.pauseRecording();

        updateNotification("Paused");
    }

    public void resumeRecording() {
        if (!recorder.isRecording() || !recorder.isPaused()) return;

        pausedDuration += System.currentTimeMillis() - lastPauseTime;
        recorder.resumeRecording();

        updateNotification("Recording...");
    }

    public void stopRecording() {
        recorder.stopRecording();
        resetState();

        stopForeground(true);
        stopSelf();
    }

    public void stopRecordingForSave() {
        recorder.stopRecording();
        resetState();

        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopForeground(true);
        } else {
            new Handler(Looper.getMainLooper()).post(() -> stopForeground(true));
        }
    }

    public void stopIfIdle() {
        if (!recorder.isRecording() && !isPlaybackMode) {
            stopSelf();
        }
    }

    // =========================
    // Playback
    // =========================

    public void startPlayback(int noteId, String filePath, String title) {
        isPlaybackMode = true;
        playbackNoteId = noteId;

        player.startPlaying(this, filePath);

        player.setOnPlaybackListener(new AudioPlayerManager.OnPlaybackListener() {
            @Override public void onProgressUpdate(int c, int d) {}
            @Override public void onCompletion() {
                player.seekTo(0);
                updateNotification("Playback Finished");
            }
            @Override public void onError(String msg) { stopPlayback(); }
        });

        startForegroundService("Playing: " + title);
    }

    public void pausePlayback() {
        player.pausePlaying();
        updateNotification("Playback Paused");
    }

    public void resumePlayback() {
        player.resumePlaying();
        updateNotification("Playing...");
    }

    public void stopPlayback() {
        player.stopPlaying();

        isPlaybackMode = false;
        playbackNoteId = -1;

        stopForeground(true);
        stopSelf();
    }

    // =========================
    // Notification
    // =========================

    private void startForegroundService(String text) {
        Notification notification = buildNotification(text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String text) {

        boolean isPaused = !isPlaybackMode && recorder.isPaused();
        boolean isPlaying = isPlaybackMode && player.isPlaying();

        String pauseLabel = isPlaybackMode
                ? (isPlaying ? "Pause" : "Resume")
                : (isPaused ? "Resume" : "Pause");

        String pauseAction = isPlaybackMode
                ? (isPlaying ? ACTION_PLAYBACK_PAUSE : ACTION_PLAYBACK_RESUME)
                : (isPaused ? ACTION_RESUME : ACTION_PAUSE);

        Intent pauseIntent = new Intent(this, RecordingService.class).setAction(pauseAction);
        PendingIntent pPause = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, RecordingService.class)
                .setAction(isPlaybackMode ? ACTION_PLAYBACK_STOP : ACTION_STOP);

        PendingIntent pStop = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent openApp = new Intent(this, MainActivity.class);
        openApp.putExtra("navigation_target", "recorder");
        if (playbackNoteId != -1) openApp.putExtra("note_id", playbackNoteId);

        PendingIntent pApp = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int priority = liveNotificationEnabled ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Yournal")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pApp)
                .addAction(android.R.drawable.ic_media_pause, pauseLabel, pPause)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStop)
                .setOngoing(true)
                .setPriority(priority)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(liveNotificationEnabled
                        ? NotificationCompat.VISIBILITY_PUBLIC
                        : NotificationCompat.VISIBILITY_SECRET)
                .setSilent(!liveNotificationEnabled);

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setSound(null, null);
        channel.setShowBadge(false);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    // =========================
    // Helpers
    // =========================

    private void handlePause() {
        if (isPlaybackMode) pausePlayback();
        else pauseRecording();
    }

    private void handleResume() {
        if (isPlaybackMode) resumePlayback();
        else resumeRecording();
    }

    private void resetState() {
        isPlaybackMode = false;
        playbackNoteId = -1;

        recordingStartTime = 0;
        pausedDuration = 0;
        lastPauseTime = 0;
        lastSnippet = "";
    }

    // =========================
    // Getters
    // =========================

    public boolean isRecording() { return recorder.isRecording(); }
    public boolean isPaused() { return recorder.isPaused(); }
    public boolean isPlaybackMode() { return isPlaybackMode; }

    public long getRecordingStartTime() { return recordingStartTime; }
    public long getPausedDuration() { return pausedDuration; }

    public int getAmplitude() { return recorder.getAmplitude(); }

    public AudioPlayerManager getPlayerManager() { return player; }

    // =========================
    // Binding
    // =========================

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (recorder.isRecording()) recorder.stopRecording();
        recorder.release();
        disposables.clear();
        super.onDestroy();
    }
}
