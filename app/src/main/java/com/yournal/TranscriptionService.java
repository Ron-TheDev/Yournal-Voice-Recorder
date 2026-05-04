package com.yournal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yournal.model.YournalEntry;
import com.yournal.repository.YournalRepository;

public class TranscriptionService extends Service {
    private static final String TAG = "TranscriptionService";
    private static final String CHANNEL_ID = "TranscriptionChannel";
    private static final int NOTIFICATION_ID = 2;
    private static final int FINISHED_NOTIFICATION_ID = 3;

    public static final String ACTION_TRANSCRIBE = "ACTION_TRANSCRIBE";
    public static final String EXTRA_NOTE_ID = "EXTRA_NOTE_ID";
    public static final String EXTRA_FILE_PATH = "EXTRA_FILE_PATH";

    private YournalRepository yournalRepository;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        yournalRepository = new YournalRepository(getApplication());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TRANSCRIBE.equals(intent.getAction())) {
            int noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1);
            String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
            
            if (noteId != -1 && filePath != null) {
                startTranscription(noteId, filePath);
            }
        }
        return START_NOT_STICKY;
    }

    private void startTranscription(int noteId, String filePath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification());
        }

        TranscriptionManager tm = new TranscriptionManager(this);
        tm.transcribe(Uri.parse(filePath))
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe(text -> {
                saveResultAndFinish(noteId, text);
            }, throwable -> {
                Log.e(TAG, "Background transcription failed", throwable);
                stopForeground(true);
                stopSelf();
            });
    }

    private void saveResultAndFinish(int noteId, String text) {
        io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            YournalEntry entry = yournalRepository.getNoteByIdSync(noteId);
            if (entry != null) {
                entry.noteContent = text;
                yournalRepository.updateSync(entry);
            }
        }).subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
          .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
          .subscribe(() -> {
                if (!((YournalApplication) getApplication()).isAppInForeground()) {
                    showFinishedNotification();
                }
                stopForeground(true);
                stopSelf();
          }, throwable -> {
                Log.e(TAG, "Failed to save transcription result", throwable);
                stopForeground(true);
                stopSelf();
          });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Transcription Status",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Transcribing...")
                .setContentText("Your audio is being processed locally")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void showFinishedNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigation_target", "recorder");
        // We don't necessarily have the note ID handy for the notification tap, 
        // but we can just open the recordings list or use the ID if we want.
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Transcription Finished")
                .setContentText("The transcription for your recording is ready.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        notificationManager.notify(FINISHED_NOTIFICATION_ID, notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
