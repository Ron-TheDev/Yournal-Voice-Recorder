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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.yournal.databinding.ItemAttachmentBinding;
import com.yournal.model.NoteAttachment;
import com.yournal.util.AttachmentMarkdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttachmentPreviewAdapter extends ListAdapter<NoteAttachment, AttachmentPreviewAdapter.AttachmentViewHolder> {

    private final Context appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private String playingUriString;
    private int playingAdapterPosition = RecyclerView.NO_POSITION;
    private boolean preparingPlayback = false;

    private final Runnable playbackTicker = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || !mediaPlayer.isPlaying() || playingAdapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            if (playingAdapterPosition >= 0 && playingAdapterPosition < getItemCount()) {
                notifyItemChanged(playingAdapterPosition);
            }
            mainHandler.postDelayed(this, 200);
        }
    };

    public AttachmentPreviewAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAttachmentBinding binding = ItemAttachmentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AttachmentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public void submitAttachments(List<NoteAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            submitList(null);
            return;
        }
        submitList(List.copyOf(attachments));
    }

    public void release() {
        mainHandler.removeCallbacksAndMessages(null);
        stopPlayback();
        executor.shutdownNow();
    }

    private void togglePlayback(NoteAttachment attachment, int adapterPosition) {
        if (attachment == null || attachment.uriString == null) {
            return;
        }

        if (preparingPlayback) {
            return;
        }

        if (mediaPlayer != null && attachment.uriString.equals(playingUriString)) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.start();
                scheduleTicker();
            }
            if (adapterPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPosition);
            }
            return;
        }

        stopPlayback();
        mediaPlayer = new MediaPlayer();
        playingUriString = attachment.uriString;
        playingAdapterPosition = adapterPosition;
        preparingPlayback = true;

        try {
            mediaPlayer.setDataSource(appContext, AttachmentMarkdown.toPlayableUri(attachment));
            mediaPlayer.setOnPreparedListener(mp -> {
                preparingPlayback = false;
                mp.start();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
                scheduleTicker();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopPlayback();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            stopPlayback();
        }

        if (adapterPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(adapterPosition);
        }
    }

    private void stopPlayback() {
        mainHandler.removeCallbacks(playbackTicker);
        playingUriString = null;
        playingAdapterPosition = RecyclerView.NO_POSITION;
        preparingPlayback = false;
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

    private boolean isCurrentItemPlaying(NoteAttachment attachment) {
        return mediaPlayer != null
                && mediaPlayer.isPlaying()
                && attachment != null
                && attachment.uriString != null
                && attachment.uriString.equals(playingUriString);
    }

    private static final DiffUtil.ItemCallback<NoteAttachment> DIFF_CALLBACK = new DiffUtil.ItemCallback<NoteAttachment>() {
        @Override
        public boolean areItemsTheSame(@NonNull NoteAttachment oldItem, @NonNull NoteAttachment newItem) {
            return safeEquals(oldItem.uriString, newItem.uriString)
                    && safeEquals(oldItem.attachmentType, newItem.attachmentType)
                    && safeEquals(oldItem.displayName, newItem.displayName);
        }

        @Override
        public boolean areContentsTheSame(@NonNull NoteAttachment oldItem, @NonNull NoteAttachment newItem) {
            return areItemsTheSame(oldItem, newItem)
                    && safeEquals(oldItem.mimeType, newItem.mimeType)
                    && oldItem.sourceNoteId == newItem.sourceNoteId;
        }

        private boolean safeEquals(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    };

    class AttachmentViewHolder extends RecyclerView.ViewHolder {
        private final ItemAttachmentBinding binding;

        AttachmentViewHolder(ItemAttachmentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NoteAttachment attachment) {
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

                binding.btnAudioPlayPause.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        togglePlayback(attachment, position);
                    }
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
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        togglePlayback(attachment, position);
                    }
                });
            } else {
                binding.btnAudioPlayPause.setOnClickListener(null);
                binding.seekAudioProgress.setOnSeekBarChangeListener(null);
                binding.seekAudioProgress.setProgress(0);
                loadThumbnailAsync(attachment, binding.ivAttachmentThumbnail);
                binding.getRoot().setOnClickListener(v -> AttachmentMarkdown.openAttachment(v.getContext(), attachment));
            }

            binding.btnOpenAttachment.setOnClickListener(v -> AttachmentMarkdown.openAttachment(v.getContext(), attachment));
        }
    }
}
