package com.yournal;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.yournal.databinding.FragmentSettingsBinding;
import com.yournal.repository.SettingsRepository;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsRepository settingsRepository;
    private com.yournal.repository.YournalRepository yournalRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        settingsRepository = SettingsRepository.getInstance(requireContext());
        yournalRepository = new com.yournal.repository.YournalRepository(requireActivity().getApplication());
        
        setupThemeSelection();
        setupToggles();
        setupAccentColorPicker();
        setupDataManagement();
        displayActiveSamplingRate();
    }

    private void displayActiveSamplingRate() {
        int rate = com.yournal.util.AudioUtils.getBestSupportedSampleRate();
        binding.tvSamplingRateValue.setText("Max Supported: " + rate + " Hz");
    }

    private void setupDataManagement() {
        binding.containerImport.setOnClickListener(v -> {
            importFilePickerLauncher.launch(new String[]{"text/plain", "text/*", "application/octet-stream"});
        });

        binding.containerImportAudio.setOnClickListener(v -> {
            importAudioPickerLauncher.launch(new String[]{"audio/*"});
        });
    }

    private final androidx.activity.result.ActivityResultLauncher<String[]> importFilePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    for (android.net.Uri uri : uris) {
                        importFile(uri);
                    }
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String[]> importAudioPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    for (android.net.Uri uri : uris) {
                        importAudio(uri);
                    }
                }
            });

    private void importFile(android.net.Uri uri) {
        android.widget.Toast.makeText(requireContext(), "Importing...", android.widget.Toast.LENGTH_SHORT).show();
        io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                String displayName = "Imported Note";
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex);
                }

                try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }

                    com.yournal.model.YournalEntry entry = new com.yournal.model.YournalEntry();
                    entry.noteTitle = displayName;
                    entry.noteContent = content.toString();
                    entry.dateCreated = System.currentTimeMillis();
                    entry.noteType = "note";
                    yournalRepository.insert(entry);
                }
            }
        }).subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
          .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
          .subscribe(() -> {
              android.widget.Toast.makeText(requireContext(), "Imported successfully", android.widget.Toast.LENGTH_SHORT).show();
          }, throwable -> {
              android.widget.Toast.makeText(requireContext(), "Import failed", android.widget.Toast.LENGTH_SHORT).show();
          });
    }

    private void importAudio(android.net.Uri uri) {
        android.widget.Toast.makeText(requireContext(), "Importing...", android.widget.Toast.LENGTH_SHORT).show();
        io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                String displayName = "Imported Recording";
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex);
                }

                String fileName = "imported_" + System.currentTimeMillis() + "_" + displayName;
                java.io.File destFile = new java.io.File(requireContext().getExternalFilesDir(null), fileName);
                
                try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }

                com.yournal.model.YournalEntry newRecording = new com.yournal.model.YournalEntry();
                newRecording.noteTitle = displayName;
                newRecording.filePath = destFile.getAbsolutePath();
                newRecording.dateCreated = System.currentTimeMillis();
                newRecording.noteType = "recording";
                newRecording.amplitudes = new java.util.ArrayList<>(); // Amplitudes generated dynamically in RecorderFragment
                
                yournalRepository.insert(newRecording);
            }
        }).subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
          .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
          .subscribe(() -> {
              android.widget.Toast.makeText(requireContext(), "Audio imported successfully", android.widget.Toast.LENGTH_SHORT).show();
          }, throwable -> {
              android.widget.Toast.makeText(requireContext(), "Audio import failed", android.widget.Toast.LENGTH_SHORT).show();
          });
    }

    private void setupThemeSelection() {
        disposables.add(settingsRepository.getThemeMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mode -> {
                    String value = "System Default";
                    if (mode == 0) value = "Light";
                    else if (mode == 1) value = "Dark";
                    
                    binding.tvThemeValue.setText(value);
                    
                }));

        disposables.add(io.reactivex.rxjava3.core.Flowable.combineLatest(
                settingsRepository.getRecordingFormat(),
                settingsRepository.getRecordingBitrate(),
                (format, bitrate) -> {
                    String q = "High";
                    if (bitrate <= 64) q = "Low";
                    else if (bitrate <= 128) q = "Standard";
                    return "m4a " + q + " (" + bitrate + " kbps)";
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(text -> {
                     // We need to find the TextView for quality value. 
                     // Looking at fragment_settings.xml, it's the second child of RelativeLayout container_quality
                     binding.tvQualityValue.setText(text);
                }));
        binding.containerTheme.setOnClickListener(v -> showThemeDialog());
    }

    private void setupToggles() {
        // Listeners for all settings switches
        binding.switchTranscribe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setAutoTranscribe(isChecked).subscribe();
        });
        
        binding.containerQuality.setOnClickListener(v -> showQualityDialog());
        
        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                if (isBiometricEnrolled()) {
                    verifyBiometricsForToggle(isChecked);
                } else {
                    buttonView.setChecked(!isChecked);
                    android.widget.Toast.makeText(requireContext(), "Please set up biometrics in your system settings first", android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        
        binding.switchScreenshots.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setPreventScreenshots(isChecked).subscribe();
        });
        
        binding.switchStereo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setRecordStereo(isChecked).subscribe();
        });
        
        binding.switchHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setHapticFeedback(isChecked).subscribe();
        });

        binding.switchBluetoothMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsRepository.setBluetoothMic(isChecked).subscribe();
            }
        });

        binding.switchBlockCalls.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsRepository.setBlockCalls(isChecked).subscribe();
            }
        });

        binding.switchLiveNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsRepository.setLiveNotification(isChecked).subscribe();
            }
        });

        binding.switchNoiseCancellation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setNoiseCancellation(isChecked).subscribe();
        });

//        binding.containerMicSelection.setOnClickListener(v -> {
//            showMicSelectionDialog();
//        });

        binding.containerStorage.setOnClickListener(v -> {
            pickFolder();
        });

        binding.containerModelMgmt.setOnClickListener(v -> showModelInfoDialog());

        binding.containerEngine.setOnClickListener(v -> showEngineSelectionDialog());

        // Observe all settings to update switches
        observeSettings();
    }

    private void observeSettings() {
        disposables.add(settingsRepository.getRecordStereo()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchStereo::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getBluetoothMic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchBluetoothMic::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getBlockCalls()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchBlockCalls::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getLiveNotification()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchLiveNotification::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getBiometrics()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchBiometrics::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getPreventScreenshots()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchScreenshots::setChecked, throwable -> {}));
        
        disposables.add(settingsRepository.getHapticFeedback()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchHaptics::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getNoiseCancellation()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchNoiseCancellation::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getAutoTranscribe()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.switchTranscribe::setChecked, throwable -> {}));

        disposables.add(settingsRepository.getStorageUri()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(uri -> {
                    if (uri == null || uri.isEmpty()) {
                        binding.tvStorageValue.setText("External Storage");
                    } else {
                        binding.tvStorageValue.setText(android.net.Uri.parse(uri).getLastPathSegment());
                    }
                }, throwable -> {}));

//        disposables.add(settingsRepository.getPreferredMicId()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(id -> {
//                    if (id == null || id.isEmpty()) {
//                        binding.tvMicValue.setText("Default");
//                    } else {
//                        String label = "Mic ID: " + id;
//                        java.util.List<android.media.AudioDeviceInfo> mics = com.yournal.util.AudioUtils.getAvailableMicrophones(requireContext());
//                        for (android.media.AudioDeviceInfo m : mics) {
//                            if (String.valueOf(m.getId()).equals(id)) {
//                                label = com.yournal.util.AudioUtils.getMicLabel(m);
//                                break;
//                            }
//                        }
//                        binding.tvMicValue.setText(label);
//                    }
//                }, throwable -> {}));

        disposables.add(settingsRepository.getTranscriptionModel()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(model -> binding.tvEngineValue.setText("Vosk (Local)"), throwable -> {}));

        // Observe stereo toggle to show/hide mic selector label
        disposables.add(settingsRepository.getRecordStereo()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(stereo -> {
                    String label = stereo ? "Select Microphone (L channel)" : "Select Microphone";
                    // Label update is cosmetic; mic picker still works the same
                }, throwable -> {}));

        checkModelStatus();
    }

    private void setupAccentColorPicker() {
        disposables.add(settingsRepository.getAccentColor()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    boolean isDynamic = color == 0;
                    binding.switchDynamicColor.setChecked(isDynamic);
                    binding.containerColorDots.setAlpha(isDynamic ? 0.5f : 1.0f);
                    binding.containerColorDots.setEnabled(!isDynamic);
                    
                    if (!isDynamic) {
                        applyAccentToUI(color);
                    }
                }));
        binding.switchDynamicColor.setOnCheckedChangeListener((button, isChecked) -> {
            if (button.isPressed()) {
                settingsRepository.setAccentColor(isChecked ? 0 : 0xFF4285F4).subscribe();
            }
        });

        binding.dotBlue.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_blue)).subscribe(); });
        binding.dotRed.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_red)).subscribe(); });
        binding.dotPurple.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_purple)).subscribe(); });
        binding.dotOrange.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_orange)).subscribe(); });
        binding.dotGreen.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_green)).subscribe(); });
        binding.dotPink.setOnClickListener(v -> { if (!binding.switchDynamicColor.isChecked()) settingsRepository.setAccentColor(requireContext().getColor(R.color.preset_pink)).subscribe(); });
    }

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> folderPickerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                // Persist permissions
                requireContext().getContentResolver().takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                settingsRepository.setStorageUri(uri.toString()).subscribe();
            }
        });

    private void pickFolder() {
        folderPickerLauncher.launch(null);
    }

    private void showThemeDialog() {
        String[] themes = {"Light", "Dark", "System Default"};
        int currentTheme = settingsRepository.getThemeMode().blockingFirst(2);
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, currentTheme, (dialog, which) -> {
                    settingsRepository.setThemeMode(which).subscribe();
                    dialog.dismiss();
                }).show();
    }

    private void showQualityDialog() {
        // This could be split into two dialogs, but for simplicity let's do Bitrate first
        String[] bitrates = {"64 kbps (Low)", "128 kbps (Standard)", "256 kbps (High)"};
        int[] values = {64, 128, 256};
        int currentBitrate = settingsRepository.getRecordingBitrate().blockingFirst(128);
        int selectedIndex = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentBitrate) selectedIndex = i;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Recording Bitrate")
                .setSingleChoiceItems(bitrates, selectedIndex, (dialog, which) -> {
                    settingsRepository.setRecordingBitrate(values[which]).subscribe();
                    dialog.dismiss();
                    showFormatDialog(); // Chain to format
                }).show();
    }

    private void showFormatDialog() {
        String[] formats = {"m4a (AAC)"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("File Format")
                .setSingleChoiceItems(formats, 0, (dialog, which) -> {
                    settingsRepository.setRecordingFormat("m4a").subscribe();
                    dialog.dismiss();
                }).show();
    }

    private void checkModelStatus() {
        if (TranscriptionManager.getModel() != null) {
            binding.tvModelStatus.setText("Ready (Local English)");
            binding.tvModelStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark));
        } else if (TranscriptionManager.isModelLoading()) {
            binding.tvModelStatus.setText("Loading model...");
            binding.tvModelStatus.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark));
            // Check again in 2 seconds
            binding.tvModelStatus.postDelayed(this::checkModelStatus, 2000);
        } else {
            binding.tvModelStatus.setText("Not found in assets/vosk-model-en-us-0.22-lgraph");
            binding.tvModelStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
        }
    }

    private void showModelInfoDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Offline Transcription")
                .setMessage("To use offline transcription, place the Vosk model folder \"vosk-model-en-us-0.22-lgraph\" in the app assets directory.\n\nStatus: " + (TranscriptionManager.getModel() != null ? "Ready" : "Model Missing"))
                .setPositiveButton("OK", null)
                .show();
    }

    private void showEngineSelectionDialog() {
        String[] engines = {"Vosk (Local)"};
        int selectedIndex = 0;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Transcription Engine")
                .setSingleChoiceItems(engines, selectedIndex, (dialog, which) -> {
                    settingsRepository.setTranscriptionModel("vosk").subscribe(prefs -> {
                        requireActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(requireContext(), "Engine changed to " + engines[which] + ". It will load in the background.", android.widget.Toast.LENGTH_SHORT).show();
                            // trigger transcription manager reload after preference is saved
                            com.yournal.TranscriptionManager.init(requireContext());
                        });
                    }, throwable -> {
                        Log.e("SettingsFragment", "Failed to set transcription model", throwable);
                    });
                    dialog.dismiss();
                }).show();
    }

    private boolean isBiometricEnrolled() {
        androidx.biometric.BiometricManager manager = androidx.biometric.BiometricManager.from(requireContext());
        return manager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void verifyBiometricsForToggle(boolean targetValue) {
        androidx.biometric.BiometricPrompt.AuthenticationCallback callback = new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @androidx.annotation.NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Reset switch to previous value on failure
                binding.switchBiometrics.setChecked(!targetValue);
                android.widget.Toast.makeText(requireContext(), "Authentication failed", android.widget.Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@androidx.annotation.NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                settingsRepository.setBiometrics(targetValue).subscribe();
            }
        };

        androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(this,
                androidx.core.content.ContextCompat.getMainExecutor(requireContext()), callback);

        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle(targetValue ? "Enable biometric lock" : "Disable biometric lock")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showMicSelectionDialog() {
        java.util.List<android.media.AudioDeviceInfo> rawMics = com.yournal.util.AudioUtils.getAvailableMicrophones(requireContext());
        java.util.List<android.media.AudioDeviceInfo> mics = new java.util.ArrayList<>(rawMics);

        // Sort so Bluetooth mics appear first
        java.util.Collections.sort(mics, (d1, d2) -> {
            boolean b1 = (d1.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                         d1.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
            boolean b2 = (d2.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                         d2.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
            if (b1 && !b2) return -1;
            if (!b1 && b2) return 1;
            return 0;
        });

        String[] micNames = new String[mics.size() + 1];
        micNames[0] = "Default";
        for (int i = 0; i < mics.size(); i++) {
            micNames[i + 1] = com.yournal.util.AudioUtils.getMicLabel(mics.get(i));
        }

        String currentId = settingsRepository.getPreferredMicId().blockingFirst("");
        int selectedIndex = 0;
        if (!currentId.isEmpty()) {
            for (int i = 0; i < mics.size(); i++) {
                if (String.valueOf(mics.get(i).getId()).equals(currentId)) {
                    selectedIndex = i + 1;
                    break;
                }
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Microphone")
                .setSingleChoiceItems(micNames, selectedIndex, (dialog, which) -> {
                    String selectedId = (which == 0) ? "" : String.valueOf(mics.get(which - 1).getId());
                    settingsRepository.setPreferredMicId(selectedId).subscribe();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyAccentToUI(int color) {
        boolean isDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int secondaryColor = com.yournal.util.ColorThemeUtils.getSecondaryColor(color, isDark);
        int colorSecondaryContainer = secondaryColor;
        
        android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(color);
        android.content.res.ColorStateList trackCsl = android.content.res.ColorStateList.valueOf(secondaryColor);
        android.content.res.ColorStateList containerCsl = android.content.res.ColorStateList.valueOf(colorSecondaryContainer);
        
        // Switches
        binding.switchBiometrics.setThumbTintList(csl);
        binding.switchBiometrics.setTrackTintList(trackCsl);
        binding.switchScreenshots.setThumbTintList(csl);
        binding.switchScreenshots.setTrackTintList(trackCsl);
        binding.switchDynamicColor.setThumbTintList(csl);
        binding.switchDynamicColor.setTrackTintList(trackCsl);
        binding.switchStereo.setThumbTintList(csl);
        binding.switchStereo.setTrackTintList(trackCsl);
        binding.switchBluetoothMic.setThumbTintList(csl);
        binding.switchBluetoothMic.setTrackTintList(trackCsl);
        binding.switchBlockCalls.setThumbTintList(csl);
        binding.switchBlockCalls.setTrackTintList(trackCsl);
        binding.switchLiveNotification.setThumbTintList(csl);
        binding.switchLiveNotification.setTrackTintList(trackCsl);
        binding.switchTranscribe.setThumbTintList(csl);
        binding.switchTranscribe.setTrackTintList(trackCsl);
        binding.switchHaptics.setThumbTintList(csl);
        binding.switchHaptics.setTrackTintList(trackCsl);
        binding.switchNoiseCancellation.setThumbTintList(csl);
        binding.switchNoiseCancellation.setTrackTintList(trackCsl);
        
        // Tint icons and their backgrounds (bg_tag)
        binding.iconTheme.setImageTintList(csl);
        binding.iconTheme.setBackgroundTintList(containerCsl);
        binding.iconQuality.setImageTintList(csl);
        binding.iconQuality.setBackgroundTintList(containerCsl);
        binding.iconStorage.setImageTintList(csl);
        binding.iconStorage.setBackgroundTintList(containerCsl);
        binding.iconBiometrics.setImageTintList(csl);
        binding.iconBiometrics.setBackgroundTintList(containerCsl);
        binding.iconScreenshots.setImageTintList(csl);
        binding.iconScreenshots.setBackgroundTintList(containerCsl);
        binding.iconAccent.setImageTintList(csl);
        binding.iconAccent.setBackgroundTintList(containerCsl);
        binding.iconPause.setImageTintList(csl);
        binding.iconPause.setBackgroundTintList(containerCsl);
        binding.iconBt.setImageTintList(csl);
        binding.iconBt.setBackgroundTintList(containerCsl);
        binding.iconBlock.setImageTintList(csl);
        binding.iconBlock.setBackgroundTintList(containerCsl);
        binding.iconLiveNotif.setImageTintList(csl);
        binding.iconLiveNotif.setBackgroundTintList(containerCsl);
        binding.iconTranscribe.setImageTintList(csl);
        binding.iconTranscribe.setBackgroundTintList(containerCsl);
        binding.iconLang.setImageTintList(csl);
        binding.iconLang.setBackgroundTintList(containerCsl);
        binding.iconModel.setImageTintList(csl);
        binding.iconModel.setBackgroundTintList(containerCsl);
        binding.iconHaptics.setImageTintList(csl);
        binding.iconHaptics.setBackgroundTintList(containerCsl);
        binding.iconImport.setImageTintList(csl);
        binding.iconImport.setBackgroundTintList(containerCsl);
        binding.iconImportAudio.setImageTintList(csl);
        binding.iconImportAudio.setBackgroundTintList(containerCsl);
        binding.iconNoise.setImageTintList(csl);
        binding.iconNoise.setBackgroundTintList(containerCsl);
        if (binding.iconEngine != null) {
            binding.iconEngine.setImageTintList(csl);
            binding.iconEngine.setBackgroundTintList(containerCsl);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}
