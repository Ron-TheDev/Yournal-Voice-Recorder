package com.yournal;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.yournal.databinding.FragmentTranscriptionBinding;
import com.yournal.repository.SettingsRepository;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class TranscriptionFragment extends Fragment {

    private FragmentTranscriptionBinding binding;
    private AudioRecorderManager audioRecorderManager;
    private SettingsRepository settingsRepository;
    private boolean isRecording = false;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTranscriptionBinding.inflate(inflater, container, false);
        audioRecorderManager = new AudioRecorderManager(requireContext());
        settingsRepository = SettingsRepository.getInstance(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupButtons();
        observeTranscriptionModel();
        
        audioRecorderManager.setTranscriptionListener(new TranscriptionManager.TranscriptionUpdateListener() {
            @Override
            public void onPartialResult(String text) {
                requireActivity().runOnUiThread(() -> {
                    String current = binding.etTranscriptionText.getText().toString();
                    if (!current.isEmpty() && !current.endsWith(" ")) {
                        current += " ";
                    }
                    binding.etTranscriptionText.setText(current + text + "...");
                    binding.etTranscriptionText.setSelection(binding.etTranscriptionText.getText().length());
                });
            }

            @Override
            public void onFinalResult(String text) {
                requireActivity().runOnUiThread(() -> {
                    String current = binding.etTranscriptionText.getText().toString();
                    // Remove trailing "..." if there is one from partial result
                    if (current.endsWith("...")) {
                        current = current.substring(0, current.length() - 3);
                    }
                    if (!current.isEmpty() && !current.endsWith(" ")) {
                        current += " ";
                    }
                    binding.etTranscriptionText.setText(current + text + " ");
                    binding.etTranscriptionText.setSelection(binding.etTranscriptionText.getText().length());
                });
            }
        });
    }

    private void observeTranscriptionModel() {
        disposables.add(settingsRepository.getTranscriptionModel()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(model -> binding.tvModelStatus.setText("Ready to Transcribe (Vosk)"))
        );
    }

    private void setupButtons() {
        binding.btnRecord.setOnClickListener(v -> toggleTranscription());
        
        binding.btnCopy.setOnClickListener(v -> {
            String text = binding.etTranscriptionText.getText().toString();
            if (!text.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Transcription", text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnShare.setOnClickListener(v -> {
            String text = binding.etTranscriptionText.getText().toString();
            if (!text.isEmpty()) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, "Share Transcription"));
            }
        });
    }

    private void toggleTranscription() {
        if (!hasMicrophonePermission()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        if (!audioRecorderManager.isRecording()) {
            startTranscription();
        } else if (audioRecorderManager.isPaused()) {
            resumeTranscription();
        } else {
            pauseTranscription();
        }
    }

    private void startTranscription() {
        binding.btnRecord.setImageResource(android.R.drawable.ic_media_pause);
        binding.tvModelStatus.setText("Listening...");
        audioRecorderManager.startTranscriptionOnly();
    }

    private void pauseTranscription() {
        binding.btnRecord.setImageResource(android.R.drawable.ic_media_play);
        binding.tvModelStatus.setText("Paused");
        audioRecorderManager.pauseRecording();
    }

    private void resumeTranscription() {
        binding.btnRecord.setImageResource(android.R.drawable.ic_media_pause);
        binding.tvModelStatus.setText("Listening...");
        audioRecorderManager.resumeRecording();
    }

    private boolean hasMicrophonePermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (audioRecorderManager.isRecording()) {
            audioRecorderManager.stopRecording();
        }
        disposables.clear();
        binding = null;
    }
}
