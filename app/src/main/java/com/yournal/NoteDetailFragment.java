package com.yournal;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.core.view.ViewCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.yournal.databinding.FragmentNoteDetailBinding;
import com.yournal.model.NoteAttachment;
import com.yournal.model.YournalEntry;
import com.yournal.repository.SettingsRepository;
import com.yournal.viewmodel.HomeViewModel;
import com.yournal.util.AttachmentMarkdown;
import com.yournal.util.MotionConfig;
import com.yournal.util.MarkdownRendererFactory;
import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class NoteDetailFragment extends Fragment {

    private FragmentNoteDetailBinding binding;
    private HomeViewModel homeViewModel;
    private SettingsRepository settingsRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private int currentAccentColor = 0;

    private Integer currentNoteId = null;
    private YournalEntry currentEntry; // source of truth
    private Markwon markwon;
    private MarkdownPreviewRenderer markdownPreviewRenderer;
    private MediaPlayer audioPlayer;
    private final Handler audioHandler = new Handler(Looper.getMainLooper());
    private boolean audioPreparing = false;
    private final Runnable audioProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (audioPlayer == null || !audioPlayer.isPlaying() || binding == null) {
                return;
            }
            updateAudioPlayerUi();
            audioHandler.postDelayed(this, 200);
        }
    };

    private final androidx.activity.result.ActivityResultLauncher<String[]> attachmentPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    handlePickedAttachments(uris);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int accentColor = getArguments() != null
                ? getArguments().getInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, 0)
                : 0;
        currentAccentColor = accentColor;

        int noteId = getArguments() != null ? getArguments().getInt("note_id", -1) : -1;
        String transitionName = noteId > 0
                ? MotionConfig.noteTransitionName(noteId)
                : MotionConfig.newNoteTransitionName();

        setSharedElementEnterTransition(MotionConfig.createContainerTransform(
                requireContext(), true, accentColor != 0 ? accentColor : resolveFallbackColor(), resolveSurfaceColor()));
        setSharedElementReturnTransition(MotionConfig.createContainerTransform(
                requireContext(), false, accentColor != 0 ? accentColor : resolveFallbackColor(), resolveSurfaceColor()));
        postponeEnterTransition();
        if (binding != null) {
            ViewCompat.setTransitionName(binding.getRoot(), transitionName);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNoteDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        settingsRepository = SettingsRepository.getInstance(requireContext());
        markwon = MarkdownRendererFactory.create(requireContext());
        markdownPreviewRenderer = new MarkdownPreviewRenderer(requireContext(), markwon);

        int noteId = getArguments() != null ? getArguments().getInt("note_id", -1) : -1;
        String transitionName = noteId > 0
                ? MotionConfig.noteTransitionName(noteId)
                : MotionConfig.newNoteTransitionName();
        ViewCompat.setTransitionName(binding.getRoot(), transitionName);

        setupBackNavigation();
        setupToolbar();
        setupFormattingButtons();
        setupAttachmentPreview();
        setupAudioPlayer();
        observeAccentColor();
        loadNote();
        binding.getRoot().post(this::startPostponedEnterTransition);
    }

    // ---------------------------
    // Initialization
    // ---------------------------

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_preview) {
                togglePreview();
                return true;
            } else if (item.getItemId() == R.id.action_save) {
                saveNote();
                return true;
            }
            return false;
        });
    }

    private void setupFormattingButtons() {
        binding.btnFormatBold.setOnClickListener(v -> insertMarkdown("**", "**"));
        binding.btnFormatItalic.setOnClickListener(v -> insertMarkdown("*", "*"));
        binding.btnFormatBullet.setOnClickListener(v -> insertMarkdownAtLineStart("- "));
        binding.btnFormatNumber.setOnClickListener(v -> insertMarkdownAtLineStart("1. "));
        binding.btnFormatAttachment.setOnClickListener(v -> showAttachmentOptions());
    }

    private void setupAttachmentPreview() {
        binding.previewFlowContainer.setVisibility(View.GONE);
    }

    private void setupAudioPlayer() {
        binding.audioNoteCard.setVisibility(View.GONE);
        binding.btnAudioPlayPause.setOnClickListener(v -> toggleAudioPlayback());
        binding.seekAudioProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioPlayer != null && currentEntry != null && audioPlayer.getDuration() > 0) {
                    audioPlayer.seekTo(progress);
                    updateAudioPlayerUi();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void loadNote() {
        if (getArguments() != null && getArguments().containsKey("note_id")) {
            currentNoteId = getArguments().getInt("note_id");

            // Observe DB instead of passing full object via bundle
            disposables.add(
                    homeViewModel.getNoteById(currentNoteId)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(entry -> {
                                if (entry == null) return;

                                currentEntry = entry;
                                binding.etTitle.setText(entry.noteTitle);
                                binding.etContent.setText(entry.noteContent);
                                renderPreview();
                                bindAudioNote(entry);
                            }, Throwable::printStackTrace)
            );
        }
    }

    // ---------------------------
    // Preview
    // ---------------------------

    private void togglePreview() {
        boolean isEditing = binding.etContent.getVisibility() == View.VISIBLE;

        if (isEditing) {
            renderPreview();

            binding.etContent.setVisibility(View.GONE);
            binding.previewFlowContainer.setVisibility(View.VISIBLE);
            binding.formatBar.setVisibility(View.GONE);
            binding.toolbar.getMenu().findItem(R.id.action_preview)
                    .setTitle("Edit"); // move to strings.xml
        } else {
            binding.previewFlowContainer.setVisibility(View.GONE);
            binding.etContent.setVisibility(View.VISIBLE);
            binding.formatBar.setVisibility(View.VISIBLE);
            binding.toolbar.getMenu().findItem(R.id.action_preview)
                    .setTitle("Preview");
        }
    }

    private void renderPreview() {
        if (binding == null) return;
        markdownPreviewRenderer.render(binding.previewFlowContainer, getText(binding.etContent));
    }

    // ---------------------------
    // Back Navigation
    // ---------------------------

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (hasUnsavedChanges()) {
                            showUnsavedChangesDialog();
                        } else {
                            navigateUp();
                        }
                    }
                });
    }

    private boolean hasUnsavedChanges() {
        String currentTitle = getText(binding.etTitle);
        String currentContent = getText(binding.etContent);

        if (currentEntry == null) {
            return !(currentTitle.isEmpty() && currentContent.isEmpty());
        }

        return !currentTitle.equals(currentEntry.noteTitle)
                || !currentContent.equals(currentEntry.noteContent);
    }

    private void showUnsavedChangesDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unsaved Changes") // move to strings.xml
                .setMessage("Save changes before leaving?")
                .setPositiveButton("Save", (d, w) -> saveNote())
                .setNegativeButton("Discard", (d, w) -> navigateUp())
                .setNeutralButton("Cancel", null)
                .show();
    }

    // ---------------------------
    // Save Logic (FIXED)
    // ---------------------------

    private void saveNote() {
        String title = getText(binding.etTitle).trim();
        String content = getText(binding.etContent).trim();

        if (title.isEmpty() && content.isEmpty()) {
            navigateUp();
            return;
        }

        if (title.isEmpty()) title = "Untitled Note";

        YournalEntry entry = new YournalEntry();

        if (currentEntry != null) {
            // UPDATE (preserve fields)
            entry.id = currentEntry.id;
            entry.dateCreated = currentEntry.dateCreated;
            entry.tags = currentEntry.tags;
            entry.filePath = currentEntry.filePath;
            entry.amplitudes = currentEntry.amplitudes;
            entry.attachments = currentEntry.attachments;
            entry.noteType = currentEntry.noteType;
        } else {
            // INSERT
            entry.dateCreated = System.currentTimeMillis();
            entry.tags = new ArrayList<>();
            entry.noteType = "note";
        }

        entry.noteTitle = title;
        entry.noteContent = content;
        entry.isDeleted = false;
        entry.attachments = AttachmentMarkdown.extractAttachments(content);
        if (!"audionote".equals(entry.noteType)) {
            entry.noteType = "note";
        }

        // Use callback to ensure save completes before navigating
        homeViewModel.save(entry, () -> requireActivity().runOnUiThread(this::navigateUp));
    }

    private void navigateUp() {
        NavHostFragment.findNavController(this).navigateUp();
    }

    // ---------------------------
    // Note formatting helpers
    // ---------------------------

    private void insertMarkdown(String prefix, String suffix) {
        Editable text = binding.etContent.getText();
        if (text == null) return;

        int start = binding.etContent.getSelectionStart();
        int end = binding.etContent.getSelectionEnd();

        int min = Math.min(start, end);
        int max = Math.max(start, end);

        if (min == max) {
            text.insert(min, prefix + suffix);
            binding.etContent.setSelection(min + prefix.length());
        } else {
            text.insert(min, prefix);
            text.insert(max + prefix.length(), suffix);
        }
    }

    private void insertMarkdownAtLineStart(String prefix) {
        Editable text = binding.etContent.getText();
        if (text == null) return;

        int pos = binding.etContent.getSelectionStart();
        int lineStart = pos;

        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        text.insert(lineStart, prefix);
    }

    private void showAttachmentOptions() {
        String[] options = {"File attachment", "Recording"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Attachment")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        attachmentPickerLauncher.launch(new String[]{
                                "application/pdf",
                                "image/*",
                                "video/*",
                                "audio/*"
                        });
                    } else {
                        showRecordingAttachmentDialog();
                    }
                })
                .show();
    }

    private void showRecordingAttachmentDialog() {
        androidx.lifecycle.LiveData<List<YournalEntry>> recordingsLiveData = homeViewModel.getRecordings();
        androidx.lifecycle.Observer<List<YournalEntry>> observer = new androidx.lifecycle.Observer<List<YournalEntry>>() {
            @Override
            public void onChanged(List<YournalEntry> recordings) {
                recordingsLiveData.removeObserver(this);

                if (recordings == null || recordings.isEmpty()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No recordings")
                            .setMessage("There are no recordings available to attach.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }

                String[] labels = new String[recordings.size()];
                for (int i = 0; i < recordings.size(); i++) {
                    YournalEntry recording = recordings.get(i);
                    labels[i] = recording.noteTitle != null ? recording.noteTitle : "Recording " + (i + 1);
                }

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Attach Recording")
                        .setItems(labels, (dialog, which) -> insertAttachment(createRecordingAttachment(recordings.get(which))))
                        .show();
            }
        };
        recordingsLiveData.observe(getViewLifecycleOwner(), observer);
    }

    private void handlePickedAttachments(List<Uri> uris) {
        for (Uri uri : uris) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            NoteAttachment attachment = createAttachmentFromUri(uri);
            if (attachment != null) {
                insertAttachment(attachment);
            }
        }
    }

    private NoteAttachment createAttachmentFromUri(Uri uri) {
        if (uri == null) return null;

        String displayName = "Attachment";
        String mimeType = requireContext().getContentResolver().getType(uri);
        try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        }

        String attachmentType = resolveAttachmentType(mimeType, displayName);
        return new NoteAttachment(displayName, uri.toString(), mimeType, attachmentType);
    }

    private NoteAttachment createRecordingAttachment(YournalEntry recording) {
        NoteAttachment attachment = new NoteAttachment(
                recording.noteTitle != null ? recording.noteTitle : "Yournal recording",
                recording.filePath,
                "audio/*",
                NoteAttachment.TYPE_RECORDING
        );
        attachment.sourceNoteId = recording.id;
        return attachment;
    }

    private void insertAttachment(NoteAttachment attachment) {
        if (attachment == null || binding == null) return;
        Editable text = binding.etContent.getText();
        if (text == null) return;

        int start = binding.etContent.getSelectionStart();
        int end = binding.etContent.getSelectionEnd();
        String token = AttachmentMarkdown.toMarkdown(attachment);
        String updated = AttachmentMarkdown.insertInlineToken(text, start, end, token);
        binding.etContent.setText(updated);
        int newCursor = Math.min(updated.length(), Math.max(0, start + token.length()));
        binding.etContent.setSelection(newCursor);
        if (binding.previewFlowContainer.getVisibility() == View.VISIBLE) {
            renderPreview();
        }
    }

    private void bindAudioNote(YournalEntry entry) {
        if (binding == null) return;

        boolean showAudio = entry != null && "audionote".equals(entry.noteType) && entry.filePath != null && !entry.filePath.isEmpty();
        binding.audioNoteCard.setVisibility(showAudio ? View.VISIBLE : View.GONE);
        if (!showAudio) {
            stopAudioPlayback();
            return;
        }

        binding.tvAudioTitle.setText(entry.noteTitle != null ? entry.noteTitle : "Audio Note");
        binding.tvAudioMeta.setText("Audio note");
        binding.ivAudioThumbnail.setImageResource(android.R.drawable.ic_btn_speak_now);
        if (audioPlayer != null) {
            updateAudioPlayerUi();
        } else {
            binding.btnAudioPlayPause.setImageResource(android.R.drawable.ic_media_play);
            binding.seekAudioProgress.setMax(1);
            binding.seekAudioProgress.setProgress(0);
            binding.tvAudioDuration.setText("0:00 / 0:00");
        }
    }

    private void toggleAudioPlayback() {
        if (currentEntry == null || currentEntry.filePath == null || currentEntry.filePath.isEmpty()) {
            return;
        }

        if (audioPreparing) {
            return;
        }

        if (audioPlayer != null) {
            if (audioPlayer.isPlaying()) {
                audioPlayer.pause();
                updateAudioPlayerUi();
                return;
            }

            try {
                audioPlayer.start();
                updateAudioPlayerUi();
                scheduleAudioProgress();
                return;
            } catch (IllegalStateException ignored) {
            }
        }

        prepareAudioPlayback();
    }

    private void prepareAudioPlayback() {
        stopAudioPlayback();
        audioPlayer = new MediaPlayer();
        audioPreparing = true;

        try {
            audioPlayer.setDataSource(requireContext(), resolveAudioUri(currentEntry.filePath));
            audioPlayer.setOnPreparedListener(mp -> {
                audioPreparing = false;
                mp.start();
                updateAudioPlayerUi();
                scheduleAudioProgress();
            });
            audioPlayer.setOnCompletionListener(mp -> {
                mp.seekTo(0);
                mp.pause();
                updateAudioPlayerUi();
            });
            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAudioPlayback();
                return true;
            });
            audioPlayer.prepareAsync();
        } catch (Exception e) {
            audioPreparing = false;
            stopAudioPlayback();
        }
    }

    private void updateAudioPlayerUi() {
        if (binding == null || audioPlayer == null) return;

        boolean playing = audioPlayer.isPlaying();
        binding.btnAudioPlayPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        int duration = Math.max(1, audioPlayer.getDuration());
        int position = Math.min(audioPlayer.getCurrentPosition(), duration);
        binding.seekAudioProgress.setMax(duration);
        binding.seekAudioProgress.setProgress(position);
        binding.tvAudioDuration.setText(formatTime(position) + " / " + formatTime(duration));
    }

    private void scheduleAudioProgress() {
        audioHandler.removeCallbacks(audioProgressUpdater);
        audioHandler.post(audioProgressUpdater);
    }

    private void stopAudioPlayback() {
        audioHandler.removeCallbacks(audioProgressUpdater);
        audioPreparing = false;
        if (audioPlayer != null) {
            try {
                audioPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            audioPlayer.release();
            audioPlayer = null;
        }
    }

    private Uri resolveAudioUri(String filePath) {
        if (filePath == null) return Uri.EMPTY;
        if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("android.resource://")) {
            return Uri.parse(filePath);
        }
        return Uri.fromFile(new java.io.File(filePath));
    }

    private String formatTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private String resolveAttachmentType(String mimeType, String displayName) {
        if (mimeType != null) {
            if (mimeType.equals("application/pdf")) {
                return NoteAttachment.TYPE_PDF;
            }
            if (mimeType.startsWith("image/")) {
                return NoteAttachment.TYPE_IMAGE;
            }
            if (mimeType.startsWith("video/")) {
                return NoteAttachment.TYPE_VIDEO;
            }
            if (mimeType.startsWith("audio/")) {
                return NoteAttachment.TYPE_AUDIO;
            }
        }

        String lowerName = displayName == null ? "" : displayName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return NoteAttachment.TYPE_PDF;
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")) {
            return NoteAttachment.TYPE_IMAGE;
        }
        if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov")
                || lowerName.endsWith(".webm") || lowerName.endsWith(".3gp")) {
            return NoteAttachment.TYPE_VIDEO;
        }
        if (lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") || lowerName.endsWith(".aac")
                || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg") || lowerName.endsWith(".flac")) {
            return NoteAttachment.TYPE_AUDIO;
        }
        return NoteAttachment.TYPE_AUDIO;
    }

    // ---------------------------
    // Accent Color
    // ---------------------------

    private void observeAccentColor() {
        disposables.add(settingsRepository.getAccentColor()
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    if (color == 0 || binding == null) return;

                    currentAccentColor = color;
                    ColorStateList csl = ColorStateList.valueOf(color);
                    binding.btnFormatBold.setImageTintList(csl);
                    binding.btnFormatItalic.setImageTintList(csl);
                    binding.btnFormatBullet.setImageTintList(csl);
                    binding.btnFormatNumber.setImageTintList(csl);
                }));
    }

    // ---------------------------
    // Utils
    // ---------------------------

    private String getText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private int resolveFallbackColor() {
        return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent_blue);
    }

    private int resolveSurfaceColor() {
        return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.oled_black);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAudioPlayback();
        if (markdownPreviewRenderer != null) {
            markdownPreviewRenderer.release();
        }
        disposables.clear();
        binding = null;
    }
}
