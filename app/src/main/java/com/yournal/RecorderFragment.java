package com.yournal;

import static android.content.ContentValues.TAG;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.color.MaterialColors;

import com.yournal.databinding.FragmentRecorderBinding;
import com.yournal.model.YournalEntry;
import com.yournal.repository.YournalRepository;
import com.yournal.util.MotionConfig;
import com.yournal.util.WaveformGenerator;

import java.util.ArrayList;
import java.util.List;

public class RecorderFragment extends Fragment {

    private FragmentRecorderBinding binding;
    private RecordingService recordingService;
    private com.yournal.repository.SettingsRepository settingsRepository;
    private com.yournal.util.HapticHelper hapticHelper;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private boolean isBound = false;
    private boolean isPlaybackMode = false;
    private com.yournal.model.YournalEntry playbackNote;
    private boolean playbackUiInitialized = false;
    private String savedPlaybackTranscription = "";
    private boolean playbackTranscriptionDirty = false;
    private boolean suppressPlaybackTextChanges = false;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable playbackDisposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentAccentColor = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int noteId = getArguments() != null ? getArguments().getInt("note_id", -1) : -1;
        String transitionName = noteId > 0
                ? MotionConfig.recordingTransitionName(noteId)
                : MotionConfig.newRecordingTransitionName();

        int accentColor = getArguments() != null
                ? getArguments().getInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, 0)
                : 0;
        currentAccentColor = accentColor;

        setSharedElementEnterTransition(MotionConfig.createContainerTransform(
                requireContext(), true, accentColor != 0 ? accentColor : resolveFallbackColor(), resolveSurfaceColor()));
        setSharedElementReturnTransition(MotionConfig.createContainerTransform(
                requireContext(), false, accentColor != 0 ? accentColor : resolveFallbackColor(), resolveSurfaceColor()));
        postponeEnterTransition();
        if (binding != null) {
            ViewCompat.setTransitionName(binding.getRoot(), transitionName);
        }
    }

    private Runnable timerUpdater = new Runnable() {
        @Override
        public void run() {
            if (isBound && recordingService.isRecording() && !recordingService.isPaused()) {
                long elapsed = System.currentTimeMillis() - recordingService.getRecordingStartTime() - recordingService.getPausedDuration();
                updateTimerDisplay(elapsed);
            } else if (isBound && recordingService.isPlaybackMode()) {
                updateTimerDisplay(recordingService.getPlayerManager().getCurrentPosition());
                int duration = recordingService.getPlayerManager().getDuration();
                if (duration > 0) {
                    binding.waveformView.setPlaybackProgress((float) recordingService.getPlayerManager().getCurrentPosition() / duration);
                }
            }
            handler.postDelayed(this, 100);
        }
    };

    private void updateTimerDisplay(long millis) {
        int minutes = (int) (millis / 60000);
        int seconds = (int) ((millis % 60000) / 1000);
        int tenths = (int) ((millis % 1000) / 100);
        binding.timerText.setText(String.format("%02d:%02d.%d", minutes, seconds, tenths));
    }

    private Runnable amplitudeUpdater = new Runnable() {
        @Override
        public void run() {
            if (isBound && recordingService.isRecording() && !recordingService.isPaused()) {
                int amp = recordingService.getAmplitude();
                binding.waveformView.addAmplitude(amp / 32767f); // Normalize
            }
            handler.postDelayed(this, 100);
        }
    };

    // Accumulates finalized sentences from live transcription
    private final StringBuilder liveTranscription = new StringBuilder();

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            RecordingService.LocalBinder binder = (RecordingService.LocalBinder) service;
            recordingService = binder.getService();
            isBound = true;

            // Wire up real-time transcription callbacks
            recordingService.setTranscriptionListener(new TranscriptionManager.TranscriptionUpdateListener() {
                @Override
                public void onPartialResult(String text) {
                    // Show partial (in-progress) result as a preview
                    if (binding == null || !isAdded()) return;
                    handler.post(() -> {
                        if (binding == null || !isAdded()) return;
                        String committed = liveTranscription.toString();
                        binding.etTranscription.setText(committed.isEmpty() ? text : committed + " " + text);
                    });
                }

                @Override
                public void onFinalResult(String text) {
                    // Finalized sentence — append it permanently
                    if (text == null || text.isEmpty() || binding == null || !isAdded()) return;
                    handler.post(() -> {
                        if (binding == null || !isAdded()) return;
                        if (liveTranscription.length() > 0) liveTranscription.append(" ");
                        liveTranscription.append(text);
                        binding.etTranscription.setText(liveTranscription.toString());
                    });
                }
            });

            updateUiState();
            handler.post(amplitudeUpdater);
            handler.post(timerUpdater);

            if (isPlaybackMode && playbackNote != null) {
                recordingService.startPlayback(playbackNote.id, playbackNote.filePath, playbackNote.noteTitle);
                updateUiState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecorderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsRepository = com.yournal.repository.SettingsRepository.getInstance(requireContext());
        hapticHelper = new com.yournal.util.HapticHelper(requireContext());

        Intent intent = new Intent(requireContext(), RecordingService.class);
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);

        Bundle args = getArguments();
        if (args != null && args.containsKey("note_id")) {
            isPlaybackMode = true;
            loadPlaybackNote(args.getInt("note_id"));
        }

        int noteId = args != null ? args.getInt("note_id", -1) : -1;
        String transitionName = noteId > 0
                ? MotionConfig.recordingTransitionName(noteId)
                : MotionConfig.newRecordingTransitionName();
        ViewCompat.setTransitionName(binding.getRoot(), transitionName);

        binding.btnBack.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            handleBackNavigation();
        });
        setupBackNavigation();

        binding.etTranscription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isPlaybackMode || suppressPlaybackTextChanges || playbackNote == null) return;
                String current = s == null ? "" : s.toString();
                String baseline = savedPlaybackTranscription == null ? "" : savedPlaybackTranscription;
                playbackTranscriptionDirty = !current.equals(baseline);
            }
        });

        binding.btnRecordPause.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            if (isPlaybackMode && isBound) {
                if (recordingService.getPlayerManager().isPlaying()) {
                    recordingService.pausePlayback();
                } else {
                    recordingService.resumePlayback();
                }
            } else if (isBound) {
                if (!recordingService.isRecording()) {
                    checkStorageAndStart();
                } else if (recordingService.isPaused()) {
                    recordingService.resumeRecording();
                    hapticHelper.vibrateStart();
                } else {
                    recordingService.pauseRecording();
                    hapticHelper.vibratePause();
                    if (binding != null) {
                        binding.etTranscription.setHint("Transcription will appear after you stop recording.");
                    }
                }
            }
            updateUiState();
        });

        binding.btnSave.setOnClickListener(v -> {
            if (isBound && recordingService.isRecording()) {
                hapticHelper.vibrateStop();
                String currentPath = recordingService.getCurrentFilepath();
                Log.d(TAG, "onViewCreated: trying to save - filepaths");
                java.util.List<Float> amplitudes = recordingService.getCollectedAmplitudes();
                Log.d(TAG, "onViewCreated: trying to save - amplitudes");
                recordingService.stopRecording();
                Log.d(TAG, "onViewCreated: recording stopped");
                // Transcription is already live — use whatever we have accumulated
                showSaveDialog(currentPath, amplitudes);
            }
        });

        binding.btnTrim.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            // Placeholder for trim logic
            android.widget.Toast.makeText(requireContext(), "Trim feature coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        // Toggle Audio/Transcription visual state
        binding.btnAudioMode.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            binding.waveformView.setVisibility(View.VISIBLE);
            binding.etTranscription.setVisibility(View.GONE);

            binding.btnAudioMode.setBackgroundResource(R.drawable.bg_card_rounded);
            if (currentAccentColor != 0) {
                binding.btnAudioMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentAccentColor));
            } else {
                binding.btnAudioMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.tag_bg)));
            }
            binding.btnAudioMode.setTextColor(getResources().getColor(R.color.white));

            binding.btnTranscribeMode.setBackground(null);
            binding.btnTranscribeMode.setTextColor(getResources().getColor(R.color.light_grey));
        });

        binding.btnTranscribeMode.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            binding.waveformView.setVisibility(View.GONE);
            binding.etTranscription.setVisibility(View.VISIBLE);

            binding.btnTranscribeMode.setBackgroundResource(R.drawable.bg_card_rounded);
            if (currentAccentColor != 0) {
                binding.btnTranscribeMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentAccentColor));
            } else {
                binding.btnTranscribeMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.tag_bg)));
            }
            binding.btnTranscribeMode.setTextColor(getResources().getColor(R.color.white));

            binding.btnAudioMode.setBackground(null);
            binding.btnAudioMode.setTextColor(getResources().getColor(R.color.light_grey));
        });

        binding.btnConvertAudioNote.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            convertPlaybackRecordingToAudioNote();
        });

        // Initial control state
        binding.btnSave.setVisibility(View.INVISIBLE);
        binding.btnTrim.setVisibility(View.INVISIBLE);
        binding.btnConvertAudioNote.setVisibility(View.GONE);

        // Clear live transcription on UI reset
        if (!isPlaybackMode && (!isBound || !recordingService.isRecording())) {
            liveTranscription.setLength(0);
        }

        binding.waveformView.setOnSeekListener(progress -> {
            if (isPlaybackMode && isBound) {
                int position = (int) (progress * recordingService.getPlayerManager().getDuration());
                recordingService.getPlayerManager().seekTo(position);
                updateTimerDisplay(position);
                binding.waveformView.setPlaybackProgress(progress);
            }
        });

        binding.btnMore.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            if (isPlaybackMode && playbackNote != null && playbackNote.filePath != null) {
                performManualTranscription(playbackNote.filePath);
            } else if (isBound && recordingService.isRecording()) {
                android.widget.Toast.makeText(requireContext(), "Live transcription is already active", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(requireContext(), "No recording to transcribe", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        observeAccentColor();
        binding.getRoot().post(this::startPostponedEnterTransition);
    }

    private void performManualTranscription(String filePath) {
        Intent intent = new Intent(requireContext(), TranscriptionService.class);
        intent.setAction(TranscriptionService.ACTION_TRANSCRIBE);
        intent.putExtra(TranscriptionService.EXTRA_NOTE_ID, playbackNote.id);
        intent.putExtra(TranscriptionService.EXTRA_FILE_PATH, filePath);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }

        binding.etTranscription.setHint("Transcription started in background...");
        binding.btnTranscribeMode.performClick(); // Switch to transcription view
        android.widget.Toast.makeText(requireContext(), "Transcription started in background", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void loadPlaybackNote(int noteId) {
        com.yournal.repository.YournalRepository repo = new com.yournal.repository.YournalRepository(requireActivity().getApplication());
        playbackDisposables.add(repo.getNoteById(noteId)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(note -> {
                    if (note == null) return;
                    boolean firstLoad = !playbackUiInitialized;
                    playbackNote = note;
                    if (note.noteContent != null) {
                        String currentText = binding != null && binding.etTranscription.getText() != null
                                ? binding.etTranscription.getText().toString()
                                : "";
                        boolean canRefresh = !playbackTranscriptionDirty || currentText.equals(savedPlaybackTranscription);
                        if (canRefresh && !note.noteContent.equals(currentText) && binding != null) {
                            suppressPlaybackTextChanges = true;
                            binding.etTranscription.setText(note.noteContent);
                            suppressPlaybackTextChanges = false;
                            savedPlaybackTranscription = note.noteContent;
                            playbackTranscriptionDirty = false;
                        } else if (currentText.equals(note.noteContent)) {
                            savedPlaybackTranscription = note.noteContent;
                            playbackTranscriptionDirty = false;
                        }
                    }
                    if (firstLoad) {
                        playbackUiInitialized = true;
                        setupPlaybackUI(note);
                    }
                }, t -> {
                    android.widget.Toast.makeText(requireContext(), "Failed to load recording", android.widget.Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).navigateUp();
                }));
    }

    private void setupPlaybackUI(com.yournal.model.YournalEntry note) {
        binding.timerText.setText("00:00.0");
        savedPlaybackTranscription = note.noteContent == null ? "" : note.noteContent;
        playbackTranscriptionDirty = false;
        suppressPlaybackTextChanges = true;
        binding.etTranscription.setText(savedPlaybackTranscription);
        suppressPlaybackTextChanges = false;
        binding.btnSave.setVisibility(View.GONE);
        binding.btnTrim.setVisibility(View.VISIBLE); // Keep trim for future? 或者隐藏
        binding.btnConvertAudioNote.setVisibility("recording".equals(note.noteType) ? View.VISIBLE : View.GONE);

        if (note.filePath != null) {
            playbackDisposables.add(WaveformGenerator.generate(requireContext(), note.filePath)
                    .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                    .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                    .subscribe(amplitudes -> {
                        if (binding != null) {
                            binding.waveformView.setAmplitudes(amplitudes);
                        }
                    }, throwable -> {
                        android.util.Log.e("RecorderFragment", "Failed to generate waveform", throwable);
                    }));
        }

        if (isBound && note.filePath != null) {
            recordingService.startPlayback(note.id, note.filePath, note.noteTitle);
            recordingService.pausePlayback(); // Start paused
        }
        handler.post(timerUpdater);
    }

    private void observeAccentColor() {
        disposables.add(settingsRepository.getAccentColor()
                .distinctUntilChanged()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    if (color != 0) {
                        currentAccentColor = color;
                        applyAccentToUI(color);
                    }
                }));
    }

    private void applyAccentToUI(int color) {
        android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(color);
        binding.btnRecordPause.setBackgroundTintList(csl);
        binding.btnConvertAudioNote.setBackgroundTintList(csl);
        binding.waveformView.setColor(color);

        // Update mode buttons if they are active
        if (binding.btnAudioMode.getBackground() != null) {
            binding.btnAudioMode.setBackgroundTintList(csl);
        }
        if (binding.btnTranscribeMode.getBackground() != null) {
            binding.btnTranscribeMode.setBackgroundTintList(csl);
        }
    }

    private int resolveFallbackColor() {
        return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent_blue);
    }

    private int resolveSurfaceColor() {
        return MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface,
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.oled_black));
    }

    private final androidx.activity.result.ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean audioGranted = result.getOrDefault(android.Manifest.permission.RECORD_AUDIO, false);
                if (Boolean.TRUE.equals(audioGranted)) {
                    checkStorageAndStart();
                } else {
                    android.widget.Toast.makeText(requireContext(), "Microphone permission denied. Cannot record audio.", android.widget.Toast.LENGTH_SHORT).show();
                }
            });

    private void checkStorageAndStart() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            String[] permissions;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS};
            } else {
                permissions = new String[]{android.Manifest.permission.RECORD_AUDIO};
            }
            requestPermissionsLauncher.launch(permissions);
            return;
        }
        disposables.add(settingsRepository.getStorageUri()
                .firstOrError()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(uri -> {
                    if (uri == null || uri.isEmpty()) {
                        showStorageFirstTimeDialog();
                    } else {
                        prepareFileAndStart(android.net.Uri.parse(uri));
                    }
                }, throwable -> showStorageFirstTimeDialog()));
    }

    private void showStorageFirstTimeDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Save Location")
                .setMessage("Where should Yournal save your recordings? You can select an SD card or any folder.")
                .setPositiveButton("Select Folder", (dialog, which) -> folderPickerLauncher.launch(null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> folderPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    disposables.add(settingsRepository.setStorageUri(uri.toString())
                            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                            .subscribe(prefs -> prepareFileAndStart(uri), throwable -> {
                                android.util.Log.e("RecorderFragment", "Failed to save storage URI", throwable);
                                prepareFileAndStart(uri); // Try to start anyway
                            }));
                }
            });

    private void prepareFileAndStart(android.net.Uri treeUri) {
        try {
            androidx.documentfile.provider.DocumentFile root = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), treeUri);
            if (root == null || !root.canWrite()) {
                android.widget.Toast.makeText(requireContext(), "Cannot write to selected folder", android.widget.Toast.LENGTH_SHORT).show();
                showStorageFirstTimeDialog();
                return;
            }

            String format = settingsRepository.getRecordingFormat().blockingFirst("m4a");
            String mimeType = "audio/mp4";
            String extension = "." + format;

            if ("flac".equals(format)) mimeType = "audio/flac";
            else if ("mp3".equals(format)) mimeType = "audio/mpeg";
            else if ("3gp".equals(format)) mimeType = "audio/3gpp";
            else if ("aac".equals(format)) mimeType = "audio/aac";

            String fileName = "recording_" + System.currentTimeMillis() + extension;
            androidx.documentfile.provider.DocumentFile file = root.createFile(mimeType, fileName);
            if (file == null) {
                android.widget.Toast.makeText(requireContext(), "Failed to create recording file", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            android.os.ParcelFileDescriptor pfd = requireContext().getContentResolver().openFileDescriptor(file.getUri(), "rw");
            if (pfd != null) {
                recordingService.startRecording(pfd.getFileDescriptor(), file.getUri().toString());
                hapticHelper.vibrateStart();
                updateUiState(); // Immediate UI update
            } else {
                android.widget.Toast.makeText(requireContext(), "Failed to open file descriptor", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("RecorderFragment", "Failed to start SAF recording", e);
            android.widget.Toast.makeText(requireContext(), "Error: " + e.getLocalizedMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }


    private void showSaveDialog(String currentPath, java.util.List<Float> amplitudes) {
        Log.d(TAG, "showSaveDialog: loading save dialog");
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_tags, null);
        com.google.android.material.chip.ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tags);
        com.google.android.material.textfield.TextInputLayout tilInput = dialogView.findViewById(R.id.til_input);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.et_input);
        android.widget.TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.TextView messageView = dialogView.findViewById(R.id.tv_dialog_message);
        android.widget.Button cancelButton = dialogView.findViewById(R.id.btn_secondary);
        android.widget.Button saveButton = dialogView.findViewById(R.id.btn_primary);

        String defaultName = "Recording " + new java.text.SimpleDateFormat("MMM dd, HH:mm").format(new java.util.Date());
        etName.setText(defaultName);
        titleView.setText("Save Recording");
        messageView.setText("Give your recording a name or use the default.");
        chipGroup.setVisibility(View.GONE);
        tilInput.setHint("Recording name");
        tilInput.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
        saveButton.setText("Save");
        cancelButton.setText("Discard");

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        saveButton.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) name = defaultName;

            Log.d(TAG, "showSaveDialog: saving");
            saveRecording(name, currentPath, amplitudes);
            dialog.dismiss();
//            NavHostFragment.findNavController(this).navigateUp();
        });

        cancelButton.setOnClickListener(v -> {
            // File is already stopped, maybe delete it if discarded?
            dialog.dismiss();
            NavHostFragment.findNavController(this).navigateUp();
        });

        dialog.show();
    }

    private void performTranscription(String currentPath) {
        if (currentPath == null) return;

        disposables.add(settingsRepository.getAutoTranscribe()
                .firstOrError()
                .flatMap(enabled -> {
                    if (enabled) {
                        TranscriptionManager tm = new TranscriptionManager(requireContext());
                        return tm.transcribe(android.net.Uri.parse(currentPath));
                    }
                    return io.reactivex.rxjava3.core.Single.error(new Exception("Transcription disabled"));
                })
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .doOnSubscribe(d -> {
                    if (binding != null && isAdded()) {
                        binding.etTranscription.setHint("Transcribing locally...");
                    }
                })
                .subscribe(text -> {
                    if (binding != null && isAdded()) {
                        binding.etTranscription.setText(text);
                        binding.etTranscription.setHint("Transcription complete (Offline)");
                    }
                }, throwable -> {
                    android.util.Log.e("RecorderFragment", "Local transcription failed", throwable);
                    if (isAdded() && getContext() != null) {
                        String msg = throwable.getMessage();
                        if (msg != null && msg.contains("wait")) {
                            android.widget.Toast.makeText(getContext(), "Initializing local model...", android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(getContext(), "Transcription error: " + msg, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                }));
    }

    private void saveRecording(String name, String currentPath, List<Float> amplitudes) {
        if (!isAdded()) return;
        Log.d(TAG, "saveRecording: save called");

        YournalEntry entry = new YournalEntry();
        entry.noteTitle = name;
        entry.noteType = "recording";
        entry.filePath = currentPath;
        entry.amplitudes = new ArrayList<>(); // Stop saving amplitudes
        entry.dateCreated = System.currentTimeMillis();

        String transcription = liveTranscription.toString().trim();

        if (transcription.isEmpty() && binding != null) {
            transcription = binding.etTranscription.getText().toString().trim();
        }

        entry.noteContent = transcription.isEmpty()
                ? "Voice recording: " + name
                : transcription;

        entry.isDeleted = false;
        entry.isPinned = false;
        entry.isFavorite = false;
        entry.tags = new ArrayList<>();

        YournalRepository repo = new YournalRepository(requireActivity().getApplication());

        repo.save(entry, () -> {
            Log.d(TAG, "saveRecording: save");
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                Log.d(TAG, "saveRecording: nav up");

                NavHostFragment.findNavController(this).navigateUp();
            });
        });
    }

    private void waitForTranscriptionAndUpdate(com.yournal.repository.YournalRepository repo, int id) {
        handler.postDelayed(new Runnable() {
            int attempts = 0;
            @Override
            public void run() {
                if (binding == null || !isAdded() || attempts > 120) return; // 2 mins max

                CharSequence hint = binding.etTranscription.getHint();
                if (hint != null && !hint.equals("Transcribing...")) {
                    String text = binding.etTranscription.getText().toString().trim();
                    if (!text.isEmpty()) {
                        disposables.add(repo.getNoteById(id)
                                .firstOrError()
                                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                                .subscribe(entry -> {
                                    if (entry != null) {
                                        entry.noteContent = text;
                                        repo.update(entry);
                                    }
                                }, t -> {}));
                    }
                    return; // Finished
                }

                attempts++;
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void updateUiState() {
        if (!isBound) return;

        boolean isRecording = recordingService.isRecording();
        boolean isPaused = recordingService.isPaused();
        boolean isPlayback = recordingService.isPlaybackMode();

        if (isRecording) {
            binding.btnSave.setVisibility(View.VISIBLE);
            binding.btnTrim.setVisibility(View.VISIBLE);
            binding.btnConvertAudioNote.setVisibility(View.GONE);

            if (isPaused) {
                binding.btnRecordPause.setImageResource(android.R.drawable.ic_media_play);
                binding.timerText.setText("Paused");
                binding.etTranscription.setEnabled(true);
            } else {
                binding.btnRecordPause.setImageResource(android.R.drawable.ic_media_pause);
                binding.timerText.setText("Recording...");
                binding.etTranscription.setEnabled(false);
            }
        } else if (isPlayback) {
            binding.btnSave.setVisibility(View.GONE);
            binding.btnTrim.setVisibility(View.VISIBLE);
            binding.btnConvertAudioNote.setVisibility(playbackNote != null && "recording".equals(playbackNote.noteType) ? View.VISIBLE : View.GONE);
            boolean isPlaying = recordingService.getPlayerManager().isPlaying();
            binding.btnRecordPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            if (!isPlaying) {
                binding.timerText.setText("Paused");
            }
            binding.etTranscription.setEnabled(true);
        } else {
            binding.btnSave.setVisibility(View.INVISIBLE);
            binding.btnTrim.setVisibility(View.INVISIBLE);
            binding.btnConvertAudioNote.setVisibility(View.GONE);
            binding.btnRecordPause.setImageResource(android.R.drawable.ic_btn_speak_now);
            binding.timerText.setText("00:00.0");
            binding.etTranscription.setEnabled(true);
        }
    }

    private void convertPlaybackRecordingToAudioNote() {
        if (!isPlaybackMode || playbackNote == null || playbackNote.filePath == null) {
            return;
        }

        if ("audionote".equals(playbackNote.noteType)) {
            android.widget.Toast.makeText(requireContext(), "This recording is already an Audio Note", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        playbackNote.noteType = "audionote";
        if (playbackNote.noteContent == null || playbackNote.noteContent.trim().isEmpty()) {
            playbackNote.noteContent = "Audio note";
        }
        new com.yournal.repository.YournalRepository(requireActivity().getApplication()).update(playbackNote);
        binding.btnConvertAudioNote.setVisibility(View.GONE);
        android.widget.Toast.makeText(requireContext(), "Converted to Audio Note", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBackNavigation();
                    }
                });
    }

    private void handleBackNavigation() {
        if (isPlaybackMode && hasUnsavedTranscriptionChanges()) {
            showUnsavedTranscriptionDialog();
        } else {
            NavHostFragment.findNavController(this).navigateUp();
        }
    }

    private boolean hasUnsavedTranscriptionChanges() {
        if (!isPlaybackMode || binding == null) return false;
        CharSequence text = binding.etTranscription.getText();
        String currentText = text == null ? "" : text.toString();
        String baseline = savedPlaybackTranscription == null ? "" : savedPlaybackTranscription;
        return !currentText.equals(baseline);
    }

    private void showUnsavedTranscriptionDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Unsaved Changes")
                .setMessage("Save transcription changes before leaving?")
                .setPositiveButton("Save", (dialog, which) -> savePlaybackTranscriptionAndExit())
                .setNegativeButton("Discard", (dialog, which) -> NavHostFragment.findNavController(this).navigateUp())
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void savePlaybackTranscriptionAndExit() {
        if (!isPlaybackMode || playbackNote == null || binding == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        String updatedText = binding.etTranscription.getText() == null
                ? ""
                : binding.etTranscription.getText().toString();

        playbackNote.noteContent = updatedText;
        YournalRepository repo = new YournalRepository(requireActivity().getApplication());
        repo.save(playbackNote, () -> requireActivity().runOnUiThread(() -> {
            savedPlaybackTranscription = updatedText;
            playbackTranscriptionDirty = false;
            NavHostFragment.findNavController(this).navigateUp();
        }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound && isPlaybackMode) {
            recordingService.stopPlayback();
        }
        disposables.clear();
        playbackDisposables.clear();
        playbackUiInitialized = false;
        handler.removeCallbacks(amplitudeUpdater);
        handler.removeCallbacks(timerUpdater);
        if (isBound) {
            requireContext().unbindService(connection);
            isBound = false;
        }
        binding = null;
    }
}
