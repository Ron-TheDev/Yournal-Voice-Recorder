package com.yournal;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.yournal.databinding.ItemAttachmentBinding;
import com.yournal.model.NoteAttachment;
import com.yournal.util.AttachmentMarkdown;
import com.yournal.util.AttachmentMarkdown.PreviewSegment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

public final class MarkdownPreviewRenderer {

    private final Context appContext;
    private final Markwon markwon;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private String playingUriString;
    private boolean preparingPlayback = false;
    private LinearLayout currentContainer;

    private final Runnable playbackTicker = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                return;
            }
            refreshCurrentAudioCard();
            mainHandler.postDelayed(this, 200);
        }
    };

    public MarkdownPreviewRenderer(@NonNull Context context, @NonNull Markwon markwon) {
        this.appContext = context.getApplicationContext();
        this.markwon = markwon;
    }

    public void render(@NonNull LinearLayout container, String markdown) {
        currentContainer = container;
        container.removeAllViews();

        List<PreviewSegment> segments = AttachmentMarkdown.splitIntoPreviewSegments(markdown);
        if (segments.isEmpty() && markdown != null && !markdown.isEmpty()) {
            segments = new ArrayList<>();
            segments.add(PreviewSegment.text(markdown));
        }

        for (PreviewSegment segment : segments) {
            if (segment.isAttachment()) {
                container.addView(createAttachmentView(container, segment.attachment));
            } else {
                container.addView(createTextView(container, segment.markdownText));
            }
        }

        if (segments.isEmpty()) {
            container.addView(createTextView(container, markdown == null ? "" : markdown));
        }
    }

    public void release() {
        mainHandler.removeCallbacksAndMessages(null);
        stopPlayback();
        executor.shutdownNow();
        currentContainer = null;
    }

    private TextView createTextView(LinearLayout parent, String markdownText) {
        TextView textView = new TextView(parent.getContext());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        textView.setTextColor(resolveThemeColor(parent.getContext(), android.R.attr.textColorPrimary, android.graphics.Color.BLACK));
        textView.setTextSize(16f);
        textView.setLineSpacing(4f, 1f);
        markwon.setMarkdown(textView, markdownText == null ? "" : markdownText);
        return textView;
    }

    private View createAttachmentView(LinearLayout parent, NoteAttachment attachment) {
        ItemAttachmentBinding binding = ItemAttachmentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        bindAttachment(binding, attachment);
        return binding.getRoot();
    }

    private void bindAttachment(ItemAttachmentBinding binding, NoteAttachment attachment) {
        binding.getRoot().setTag(attachment.uriString);
        binding.ivAttachmentThumbnail.setTag(attachment.uriString);
        binding.tvAttachmentName.setText(attachment.displayName != null ? attachment.displayName : "Attachment");
        binding.tvAttachmentMeta.setText(buildMetaLabel(attachment));

        boolean audio = NoteAttachment.TYPE_AUDIO.equals(attachment.attachmentType)
                || NoteAttachment.TYPE_RECORDING.equals(attachment.attachmentType);

        binding.audioControls.setVisibility(audio ? View.VISIBLE : View.GONE);
        binding.ivAttachmentOverlay.setVisibility(NoteAttachment.TYPE_VIDEO.equals(attachment.attachmentType) ? View.VISIBLE : View.GONE);
        binding.btnOpenAttachment.setVisibility(audio ? View.GONE : View.VISIBLE);

        if (audio) {
            binding.ivAttachmentThumbnail.setImageResource(resolveFallbackIcon(attachment));
            updateAudioBinding(binding, attachment);
            binding.btnAudioPlayPause.setOnClickListener(v -> {
                togglePlayback(attachment);
            });
            binding.seekAudioProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && isCurrentItemPlaying(attachment)) {
                        mediaPlayer.seekTo(progress);
                        binding.tvAttachmentDuration.setText(formatTime(progress) + " / " + formatTime(mediaPlayer.getDuration()));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (isCurrentItemPlaying(attachment)) {
                        mediaPlayer.seekTo(seekBar.getProgress());
                    }
                }
            });
            binding.getRoot().setOnClickListener(v -> {
                togglePlayback(attachment);
            });
        } else {
            binding.btnAudioPlayPause.setOnClickListener(null);
            binding.seekAudioProgress.setOnSeekBarChangeListener(null);
            binding.seekAudioProgress.setProgress(0);
            binding.tvAttachmentDuration.setText("");
            binding.getRoot().setOnClickListener(v -> AttachmentMarkdown.openAttachment(v.getContext(), attachment));
            binding.btnOpenAttachment.setOnClickListener(v -> AttachmentMarkdown.openAttachment(v.getContext(), attachment));
            loadThumbnailAsync(attachment, binding.ivAttachmentThumbnail);
        }
    }

    private void updateAudioBinding(ItemAttachmentBinding binding, NoteAttachment attachment) {
        binding.btnAudioPlayPause.setImageResource(
                isCurrentItemPlaying(attachment) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        if (isCurrentItemPlaying(attachment)) {
            int duration = mediaPlayer.getDuration();
            int position = mediaPlayer.getCurrentPosition();
            binding.seekAudioProgress.setMax(Math.max(1, duration));
            binding.seekAudioProgress.setProgress(Math.min(position, duration));
            binding.tvAttachmentDuration.setText(formatTime(position) + " / " + formatTime(duration));
        } else {
            binding.seekAudioProgress.setMax(1);
            binding.seekAudioProgress.setProgress(0);
            binding.tvAttachmentDuration.setText("0:00 / 0:00");
        }
    }

    private boolean isCurrentItemPlaying(NoteAttachment attachment) {
        return mediaPlayer != null
                && mediaPlayer.isPlaying()
                && attachment != null
                && attachment.uriString != null
                && attachment.uriString.equals(playingUriString);
    }

    private void togglePlayback(NoteAttachment attachment) {
        if (attachment == null || attachment.uriString == null) return;
        if (preparingPlayback) return;

        if (mediaPlayer != null && attachment.uriString.equals(playingUriString)) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.start();
                scheduleTicker();
            }
            refreshCurrentAudioCard();
            return;
        }

        stopPlayback();
        mediaPlayer = new MediaPlayer();
        playingUriString = attachment.uriString;
        preparingPlayback = true;

        try {
            mediaPlayer.setDataSource(appContext, AttachmentMarkdown.toPlayableUri(attachment));
            mediaPlayer.setOnPreparedListener(mp -> {
                preparingPlayback = false;
                mp.start();
                refreshCurrentAudioCard();
                scheduleTicker();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
                refreshCurrentAudioCard();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopPlayback();
                refreshCurrentAudioCard();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            stopPlayback();
            refreshCurrentAudioCard();
        }
    }

    private void refreshCurrentAudioCard() {
        if (currentContainer == null) return;
        for (int i = 0; i < currentContainer.getChildCount(); i++) {
            View child = currentContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag != null && tag.equals(playingUriString)) {
                bindAudioState(child);
                break;
            }
        }
    }

    private void bindAudioState(View root) {
        ImageView thumbnail = root.findViewById(R.id.iv_attachment_thumbnail);
        View playPause = root.findViewById(R.id.btn_audio_play_pause);
        SeekBar seekBar = root.findViewById(R.id.seek_audio_progress);
        TextView duration = root.findViewById(R.id.tv_attachment_duration);
        if (thumbnail == null || playPause == null || seekBar == null || duration == null) return;
        boolean active = isCurrentItemActiveByTag(root);
        ((ImageButton) playPause).setImageResource(
                isCurrentItemPlaying(root) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        if (active) {
            int durationMs = mediaPlayer.getDuration();
            int position = mediaPlayer.getCurrentPosition();
            seekBar.setMax(Math.max(1, durationMs));
            seekBar.setProgress(Math.min(position, durationMs));
            duration.setText(formatTime(position) + " / " + formatTime(durationMs));
        } else {
            seekBar.setMax(1);
            seekBar.setProgress(0);
            duration.setText("0:00 / 0:00");
        }
    }

    private boolean isCurrentItemActiveByTag(View root) {
        Object tag = root.getTag();
        return tag != null && tag.equals(playingUriString) && mediaPlayer != null;
    }

    private boolean isCurrentItemPlaying(View root) {
        Object tag = root.getTag();
        return tag != null && tag.equals(playingUriString) && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void stopPlayback() {
        mainHandler.removeCallbacks(playbackTicker);
        preparingPlayback = false;
        playingUriString = null;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void scheduleTicker() {
        mainHandler.removeCallbacks(playbackTicker);
        mainHandler.post(playbackTicker);
    }

    private void loadThumbnailAsync(NoteAttachment attachment, ImageView target) {
        if (attachment == null || target == null || attachment.uriString == null) return;
        final String uriKey = attachment.uriString;
        target.setImageResource(resolveFallbackIcon(attachment));
        executor.submit(() -> {
            Bitmap bitmap = loadThumbnail(attachment);
            if (bitmap == null) return;
            mainHandler.post(() -> {
                Object tag = target.getTag();
                if (uriKey.equals(tag)) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap loadThumbnail(NoteAttachment attachment) {
        try {
            android.net.Uri uri = AttachmentMarkdown.toPlayableUri(attachment);
            ContentResolver resolver = appContext.getContentResolver();

            if (NoteAttachment.TYPE_IMAGE.equals(attachment.attachmentType)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return resolver.loadThumbnail(uri, new android.util.Size(256, 256), null);
                }
                return decodeImageThumbnail(uri);
            }

            if (NoteAttachment.TYPE_VIDEO.equals(attachment.attachmentType)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return resolver.loadThumbnail(uri, new android.util.Size(256, 256), null);
                }
                return loadVideoFrame(uri);
            }

            if (NoteAttachment.TYPE_PDF.equals(attachment.attachmentType)) {
                return loadPdfThumbnail(uri);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Bitmap decodeImageThumbnail(android.net.Uri uri) throws IOException {
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, 256, 256);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        }
    }

    private Bitmap loadVideoFrame(android.net.Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, uri);
            Bitmap frame = retriever.getFrameAtTime(0);
            if (frame != null) {
                return ThumbnailUtils.extractThumbnail(frame, 256, 256);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private Bitmap loadPdfThumbnail(android.net.Uri uri) {
        try (ParcelFileDescriptor descriptor = appContext.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) return null;
            android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(descriptor);
            android.graphics.pdf.PdfRenderer.Page page = null;
            try {
                page = renderer.openPage(0);
                Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return Bitmap.createScaledBitmap(bitmap, 256, 256, true);
            } finally {
                if (page != null) {
                    page.close();
                }
                renderer.close();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private String formatTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private String buildMetaLabel(NoteAttachment attachment) {
        if (attachment == null) return "Attachment";
        if (NoteAttachment.TYPE_RECORDING.equals(attachment.attachmentType)) return "Yournal recording";
        if (NoteAttachment.TYPE_AUDIO.equals(attachment.attachmentType)) return "Audio";
        if (NoteAttachment.TYPE_VIDEO.equals(attachment.attachmentType)) return "Video";
        if (NoteAttachment.TYPE_IMAGE.equals(attachment.attachmentType)) return "Image";
        if (NoteAttachment.TYPE_PDF.equals(attachment.attachmentType)) return "PDF";
        return "Attachment";
    }

    private int resolveFallbackIcon(NoteAttachment attachment) {
        if (attachment == null) return android.R.drawable.ic_menu_report_image;
        if (NoteAttachment.TYPE_PDF.equals(attachment.attachmentType)) {
            return android.R.drawable.ic_menu_view;
        }
        if (NoteAttachment.TYPE_VIDEO.equals(attachment.attachmentType)) {
            return android.R.drawable.ic_media_play;
        }
        if (NoteAttachment.TYPE_AUDIO.equals(attachment.attachmentType) || NoteAttachment.TYPE_RECORDING.equals(attachment.attachmentType)) {
            return android.R.drawable.ic_btn_speak_now;
        }
        if (NoteAttachment.TYPE_IMAGE.equals(attachment.attachmentType)) {
            return android.R.drawable.ic_menu_gallery;
        }
        return android.R.drawable.ic_menu_report_image;
    }

    private int resolveThemeColor(Context context, int attr, int fallbackColor) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId);
            }
            return typedValue.data;
        }
        return fallbackColor;
    }
}
