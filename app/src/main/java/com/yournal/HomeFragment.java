package com.yournal;

import android.os.Bundle;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import android.graphics.drawable.ColorDrawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.yournal.databinding.FragmentHomeBinding;
import com.yournal.model.YournalEntry;
import com.yournal.viewmodel.HomeViewModel;
import com.yournal.util.MotionConfig;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private NoteAdapter adapter;
    private NoteAdapter searchAdapter;
    private HomeViewModel homeViewModel;
    private com.yournal.repository.SettingsRepository settingsRepository;
    private com.yournal.util.HapticHelper hapticHelper;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();
    private com.yournal.model.YournalEntry pendingExportNote;
    private int currentAccentColor = 0;
    private LiveData<List<YournalEntry>> activeSearchResults;
    private boolean isFabMenuOpen = false;
    private final List<String> cachedTags = new ArrayList<>();
    private final Observer<List<YournalEntry>> searchResultsObserver = notes -> {
        if (searchAdapter != null) {
            searchAdapter.submitList(notes == null ? null : new ArrayList<>(notes));
        }
    };

    private final androidx.activity.result.ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("audio/*"), uri -> {
                if (uri != null && pendingExportNote != null) {
                    com.yournal.util.ShareUtils.exportRecording(requireContext(), pendingExportNote, uri);
                    pendingExportNote = null;
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        settingsRepository = com.yournal.repository.SettingsRepository.getInstance(requireContext());
        hapticHelper = new com.yournal.util.HapticHelper(requireContext());
        
        setupRecyclerView();
        setupFab();
        setupSearch();
        setupFilterChips();
        observeAccentColor();
        setupSelectionBar();
    }

    private void setupSelectionBar() {
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedNotes());
        binding.btnClearSelection.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
            updateSelectionUI();
        });
    }

    private void deleteSelectedNotes() {
        java.util.Set<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setTitle("Delete Selected")
                .setMessage("Move " + selectedIds.size() + " items to Recycle Bin?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    List<com.yournal.model.YournalEntry> currentList = adapter.getCurrentList();
                    for (com.yournal.model.YournalEntry entry : currentList) {
                        if (selectedIds.contains(entry.id)) {
                            entry.isDeleted = true;
                            entry.dateDeleted = System.currentTimeMillis();
                            homeViewModel.update(entry);
                        }
                    }
                    adapter.setSelectionMode(false);
                    updateSelectionUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSelectionUI() {
        java.util.Set<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            binding.cardSelection.setVisibility(View.GONE);
            binding.fabAddItem.show();
        } else {
            binding.cardSelection.setVisibility(View.VISIBLE);
            binding.tvSelectionCount.setText(selectedIds.size() + " selected");
            closeFabMenu();
            binding.fabAddItem.hide();
        }
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
                        if (adapter != null) adapter.setAccentColor(color);
                        if (searchAdapter != null) searchAdapter.setAccentColor(color);
                    }
                }));
    }

    private void applyAccentToUI(int color) {
        android.content.res.ColorStateList csl = android.content.res.ColorStateList.valueOf(color);
        binding.fabAddItem.setBackgroundTintList(csl);
        binding.fabScrollUp.setBackgroundTintList(csl);
        binding.fabMenuAddNote.setBackgroundTintList(csl);
        binding.fabMenuAddRecording.setBackgroundTintList(csl);
        binding.fabMenuAddDrawing.setBackgroundTintList(csl);
        binding.cardSelection.setCardBackgroundColor(csl);
    }

    private void setupRecyclerView() {
        adapter = new NoteAdapter(this::onNoteClick, this::onNoteLongClick, this::onOverflowClick);
        adapter.setOnTagsClickListener(this::onTagsClick);
        searchAdapter = new NoteAdapter(this::onNoteClick, this::onNoteLongClick, this::onOverflowClick);
        searchAdapter.setOnTagsClickListener(this::onTagsClick);
        
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);
        
        binding.searchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.searchRecyclerView.setAdapter(searchAdapter);

        homeViewModel.getFilteredNotes().observe(getViewLifecycleOwner(), notes -> {
            List<YournalEntry> snapshot = notes == null ? null : new ArrayList<>(notes);
            adapter.submitList(snapshot);
            if (!binding.searchView.isShowing() || activeSearchResults == null) {
                searchAdapter.submitList(snapshot);
            }
            if (notes != null && !notes.isEmpty()) {
                binding.recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }
    
    private void setupSearch() {
        binding.searchView.setupWithSearchBar(binding.searchBar);
        
        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                observeSearchQuery(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        binding.searchView.addTransitionListener((searchView, previousState, newState) -> {
            if (newState == com.google.android.material.search.SearchView.TransitionState.HIDDEN) {
                clearSearchObservation();
            }
        });
    }

    private void observeSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            clearSearchObservation();
            searchAdapter.submitList(adapter.getCurrentList());
            return;
        }

        LiveData<List<YournalEntry>> nextResults = homeViewModel.searchNotes(normalized);
        if (activeSearchResults == nextResults) return;

        clearSearchObservation();
        activeSearchResults = nextResults;
        activeSearchResults.observe(getViewLifecycleOwner(), searchResultsObserver);
    }

    private void clearSearchObservation() {
        if (activeSearchResults != null) {
            activeSearchResults.removeObserver(searchResultsObserver);
            activeSearchResults = null;
        }
    }

    private void setupFilterChips() {
        binding.chipFilterAll.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            binding.searchView.getEditText().setText("");
            homeViewModel.clearTypeFilter();
            homeViewModel.clearTagFilter();
            homeViewModel.setSortMode(0);
        });

        binding.chipFilterSort.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                    .setTitle("Sort order")
                    .setSingleChoiceItems(new CharSequence[]{"Date (Newest)", "Date (Oldest)", "A-Z (Title)", "Z-A (Title)"}, 
                            homeViewModel.getSortModeValue(), (dialog, which) -> {
                        homeViewModel.setSortMode(which);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.chipFilterType.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            String[] types = {"Notes", "Recordings", "Drawings", "Audio Notes", "All / Off"};
            String currentType = homeViewModel.getTypeFilter().getValue();
            int checkedItem = 4;
            if ("note".equals(currentType)) checkedItem = 0;
            else if ("recording".equals(currentType)) checkedItem = 1;
            else if ("drawing".equals(currentType)) checkedItem = 2;
            else if ("audionote".equals(currentType)) checkedItem = 3;

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                    .setTitle("Type")
                    .setSingleChoiceItems(types, checkedItem, (dialog, which) -> {
                        switch (which) {
                            case 0: homeViewModel.setTypeFilter("note"); break;
                            case 1: homeViewModel.setTypeFilter("recording"); break;
                            case 2: homeViewModel.setTypeFilter("drawing"); break;
                            case 3: homeViewModel.setTypeFilter("audionote"); break;
                            case 4: default: homeViewModel.clearTypeFilter(); break;
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.chipFilterTags.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            showLabelFilterDialog();
        });

        homeViewModel.getTypeFilter().observe(getViewLifecycleOwner(), type -> refreshFilterChipLabels());
        homeViewModel.getTagFilter().observe(getViewLifecycleOwner(), tag -> refreshFilterChipLabels());
        homeViewModel.getSortMode().observe(getViewLifecycleOwner(), sort -> refreshFilterChipLabels());
        
        homeViewModel.getAllUniqueTags().observe(getViewLifecycleOwner(), tags -> {
            cachedTags.clear();
            if (tags != null) {
                cachedTags.addAll(tags);
            }
        });
        
        refreshFilterChipLabels();
    }

    private void showLabelFilterDialog() {
        if (!isAdded()) return;

        List<String> displayTags = new ArrayList<>();
        displayTags.add("All tags");
        displayTags.addAll(cachedTags);

        String currentTag = homeViewModel.getTagFilter().getValue();
        int checkedItem = 0;
        if (currentTag != null && cachedTags.contains(currentTag)) {
            checkedItem = cachedTags.indexOf(currentTag) + 1;
        }

        CharSequence[] items = displayTags.toArray(new CharSequence[0]);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setTitle("Tags")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        homeViewModel.clearTagFilter();
                    } else {
                        homeViewModel.setTagFilter(displayTags.get(which));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshFilterChipLabels() {
        if (binding == null) return;
        
        int sortMode = homeViewModel.getSortModeValue();
        String sortText = "Date (Newest)";
        if (sortMode == 1) sortText = "Date (Oldest)";
        else if (sortMode == 2) sortText = "A-Z (Title)";
        else if (sortMode == 3) sortText = "Z-A (Title)";
        binding.chipFilterSort.setText("Sort: " + sortText);

        String type = homeViewModel.getTypeFilter().getValue();
        if (type == null) {
            binding.chipFilterType.setText("Type: All");
        } else {
            switch (type) {
                case "note": binding.chipFilterType.setText("Type: Notes"); break;
                case "recording": binding.chipFilterType.setText("Type: Recordings"); break;
                case "drawing": binding.chipFilterType.setText("Type: Drawings"); break;
                case "audionote": binding.chipFilterType.setText("Type: Audio Notes"); break;
                default: binding.chipFilterType.setText("Type: " + type); break;
            }
        }

        String tag = homeViewModel.getTagFilter().getValue();
        if (tag == null) {
            binding.chipFilterTags.setText("Tags: All");
        } else {
            binding.chipFilterTags.setText("Tag: " + tag);
        }
    }

    private void showAddTagDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_tags, null);
        com.google.android.material.chip.ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tags);
        com.google.android.material.textfield.TextInputLayout tilInput = dialogView.findViewById(R.id.til_input);
        com.google.android.material.textfield.TextInputEditText etInput = dialogView.findViewById(R.id.et_input);
        android.widget.TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.TextView messageView = dialogView.findViewById(R.id.tv_dialog_message);
        android.widget.Button secondaryButton = dialogView.findViewById(R.id.btn_secondary);
        android.widget.Button primaryButton = dialogView.findViewById(R.id.btn_primary);

        titleView.setText("Create Tag");
        messageView.setText("Create a new tag or pick an existing one.");
        chipGroup.setVisibility(View.VISIBLE);
        tilInput.setHint("Tag name");
        primaryButton.setText("Create");
        secondaryButton.setText("Cancel");

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setView(dialogView)
                .create();
        makeDialogTransparent(dialog);

        LiveData<List<String>> tagsLiveData = homeViewModel.getAllUniqueTags();
        Observer<List<String>> tagsObserver = tags -> {
            chipGroup.removeAllViews();
            for (String tag : tags) {
                Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.layout_filter_chip, chipGroup, false);
                chip.setText(tag);
                chip.setCheckable(false);
                chip.setOnClickListener(v -> etInput.setText(tag));
                chipGroup.addView(chip);
            }
        };
        tagsLiveData.observe(getViewLifecycleOwner(), tagsObserver);
        dialog.setOnDismissListener(d -> tagsLiveData.removeObserver(tagsObserver));

        secondaryButton.setOnClickListener(v -> dialog.dismiss());
        primaryButton.setOnClickListener(v -> {
            String newTag = etInput.getText() == null ? "" : etInput.getText().toString().trim();
            if (!newTag.isEmpty()) {
                homeViewModel.setTagFilter(newTag);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupFab() {
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && binding.fabScrollUp.getVisibility() != View.VISIBLE) {
                    binding.fabScrollUp.show();
                    closeFabMenu();
                    binding.fabAddItem.hide();
                } else if (dy < 0 || !recyclerView.canScrollVertically(-1)) {
                    if (dy < 0) binding.fabScrollUp.hide();
                    if (!recyclerView.canScrollVertically(-1)) binding.fabScrollUp.hide();
                    if (binding.fabAddItem.getVisibility() != View.VISIBLE) {
                        binding.fabAddItem.show();
                    }
                }
            }
        });

        binding.fabScrollUp.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            scrollToTop();
        });

        binding.fabAddItem.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            if (isFabMenuOpen) {
                closeFabMenu();
            } else {
                openFabMenu();
            }
        });

        binding.fabMenuAddNote.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            closeFabMenu();
            navigateToNewNote(v);
        });

        binding.fabMenuAddRecording.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            closeFabMenu();
            navigateToNewRecording(v);
        });

        binding.fabMenuAddDrawing.setOnClickListener(v -> {
            if (hapticHelper != null) hapticHelper.vibrateSelection();
            closeFabMenu();
            Toast.makeText(requireContext(), "This feature is still in development.", Toast.LENGTH_SHORT).show();
        });
    }

    private void openFabMenu() {
        if (isFabMenuOpen) return;
        isFabMenuOpen = true;
        binding.fabMenuContainer.setVisibility(View.VISIBLE);
        binding.fabMenuContainer.setAlpha(0f);
        binding.fabMenuContainer.setScaleX(0.9f);
        binding.fabMenuContainer.setScaleY(0.9f);
        binding.fabAddItem.setText("Close");
        binding.fabMenuContainer.post(() -> {
            if (isFabMenuOpen) {
                animateFabMenu(true);
            }
        });
    }

    private void closeFabMenu() {
        if (!isFabMenuOpen && binding.fabMenuContainer.getVisibility() != View.VISIBLE) {
            binding.fabAddItem.setText("Add Item");
            return;
        }
        isFabMenuOpen = false;
        binding.fabAddItem.setText("Add Item");
        animateFabMenu(false);
    }

    private void animateFabMenu(boolean opening) {
        if (binding == null || !isAdded()) return;
        if (opening && !isFabMenuOpen) return;

        binding.fabMenuContainer.animate().cancel();
        binding.fabMenuAddNote.animate().cancel();
        binding.fabMenuAddRecording.animate().cancel();
        binding.fabMenuAddDrawing.animate().cancel();

        int[] fabLocation = new int[2];
        int[] menuLocation = new int[2];
        binding.fabAddItem.getLocationOnScreen(fabLocation);
        binding.fabMenuContainer.getLocationOnScreen(menuLocation);

        float fabCenterX = fabLocation[0] + (binding.fabAddItem.getWidth() / 2f);
        float fabCenterY = fabLocation[1] + (binding.fabAddItem.getHeight() / 2f);
        float menuCenterX = menuLocation[0] + (binding.fabMenuContainer.getWidth() / 2f);
        float menuCenterY = menuLocation[1] + (binding.fabMenuContainer.getHeight() / 2f);

        float startX = fabCenterX - menuCenterX;
        float startY = fabCenterY - menuCenterY;
        long duration = MotionConfig.getDurationMs(requireContext());

        if (opening) {
            binding.fabMenuContainer.setAlpha(0f);
            binding.fabMenuContainer.setScaleX(0.9f);
            binding.fabMenuContainer.setScaleY(0.9f);
            binding.fabMenuContainer.setTranslationX(startX);
            binding.fabMenuContainer.setTranslationY(startY);
            binding.fabMenuContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            animateFabMenuItems(true, duration);
        } else {
            binding.fabMenuContainer.animate()
                    .alpha(0f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .translationX(startX)
                    .translationY(startY)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (binding == null || isFabMenuOpen) return;
                        binding.fabMenuContainer.setVisibility(View.GONE);
                        binding.fabMenuContainer.setAlpha(1f);
                        binding.fabMenuContainer.setScaleX(1f);
                        binding.fabMenuContainer.setScaleY(1f);
                        binding.fabMenuContainer.setTranslationX(0f);
                        binding.fabMenuContainer.setTranslationY(0f);
                    })
                    .start();
            animateFabMenuItems(false, duration);
        }
    }

    private void animateFabMenuItems(boolean opening, long duration) {
        View[] items = new View[]{binding.fabMenuAddNote, binding.fabMenuAddRecording, binding.fabMenuAddDrawing};
        float offset = dp(18f);

        for (int i = 0; i < items.length; i++) {
            View item = items[i];
            item.animate().cancel();
            if (opening) {
                item.setAlpha(0f);
                item.setTranslationY(offset);
                item.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(i * 35L)
                        .setDuration(duration)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            } else {
                item.animate()
                        .alpha(0f)
                        .translationY(offset)
                        .setStartDelay(i * 20L)
                        .setDuration(duration / 2)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        }
    }

    private float dp(float value) {
        return value * requireContext().getResources().getDisplayMetrics().density;
    }

    private void makeDialogTransparent(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    private void navigateToNewNote(View sharedView) {
        androidx.navigation.NavController navController = androidx.navigation.fragment.NavHostFragment.findNavController(this);
        android.os.Bundle args = new android.os.Bundle();
        args.putInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, currentAccentColor);
        MotionConfig.assignTransitionName(sharedView, MotionConfig.newNoteTransitionName());
        androidx.navigation.fragment.FragmentNavigator.Extras extras = MotionConfig.sharedElementExtras(sharedView, MotionConfig.newNoteTransitionName());
        navController.navigate(R.id.navigation_note_detail, args, null, extras);
    }

    private void navigateToNewRecording(View sharedView) {
        androidx.navigation.NavController navController = androidx.navigation.fragment.NavHostFragment.findNavController(this);
        android.os.Bundle args = new android.os.Bundle();
        args.putInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, currentAccentColor);
        MotionConfig.assignTransitionName(sharedView, MotionConfig.newRecordingTransitionName());
        androidx.navigation.fragment.FragmentNavigator.Extras extras = MotionConfig.sharedElementExtras(sharedView, MotionConfig.newRecordingTransitionName());
        navController.navigate(R.id.navigation_recorder, args, null, extras);
    }

    private void scrollToTop() {
        binding.recyclerView.smoothScrollToPosition(0);
        binding.appBarLayout.setExpanded(true, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If returning from creating a note, scroll to top
        closeFabMenu();
        scrollToTop();
    }

    private void onNoteClick(com.yournal.model.YournalEntry note, View view) {
        if ("recording".equals(note.noteType)) {
            openRecording(note, view);
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("note_id", note.id);
        bundle.putString("note_title", note.noteTitle);
        bundle.putString("note_content", note.noteContent);
        bundle.putInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, currentAccentColor);
        MotionConfig.assignTransitionName(view, MotionConfig.noteTransitionName(note.id));
        androidx.navigation.fragment.FragmentNavigator.Extras extras = MotionConfig.sharedElementExtras(view, MotionConfig.noteTransitionName(note.id));
        androidx.navigation.fragment.NavHostFragment.findNavController(this)
            .navigate(R.id.navigation_note_detail, bundle, null, extras);
    }

    private void openRecording(com.yournal.model.YournalEntry note, View view) {
        if (note.filePath == null || note.filePath.isEmpty()) {
            Toast.makeText(requireContext(), "No file found for this recording", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putInt("note_id", note.id);
        bundle.putInt(MotionConfig.ARG_MOTION_ACCENT_COLOR, currentAccentColor);
        String transitionName = MotionConfig.recordingTransitionName(note.id);
        MotionConfig.assignTransitionName(view, transitionName);
        androidx.navigation.fragment.FragmentNavigator.Extras extras = MotionConfig.sharedElementExtras(view, transitionName);
        androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.navigation_recorder, bundle, null, extras);
    }

    private void onNoteLongClick(com.yournal.model.YournalEntry note, View view) {
        hapticHelper.vibrateSelection();
        adapter.setSelectionMode(true);
        adapter.toggleSelection(note.id);
        updateSelectionUI();
    }

    private void onOverflowClick(com.yournal.model.YournalEntry note, View view) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.menu_note_item, popup.getMenu());
        
        popup.getMenu().findItem(R.id.action_pin).setTitle(note.isPinned ? "Unpin" : "Pin");
        popup.getMenu().findItem(R.id.action_favorite).setTitle(note.isFavorite ? "Remove Favorite" : "Favorite");
        popup.getMenu().findItem(R.id.action_convert_audio_note).setVisible("recording".equals(note.noteType));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_pin) {
                note.isPinned = !note.isPinned;
                homeViewModel.update(note);
            } else if (id == R.id.action_favorite) {
                note.isFavorite = !note.isFavorite;
                homeViewModel.update(note);
            } else if (id == R.id.action_delete) {
                note.isDeleted = true;
                note.dateDeleted = System.currentTimeMillis();
                homeViewModel.update(note);
            } else if (id == R.id.action_tags) {
                showNoteTagsDialog(note);
            } else if (id == R.id.action_share) {
                if ("recording".equals(note.noteType) || "audionote".equals(note.noteType)) {
                    com.yournal.util.ShareUtils.shareRecording(requireContext(), note);
                } else {
                    com.yournal.util.ShareUtils.shareNote(requireContext(), note);
                }
            } else if (id == R.id.action_export) {
                if ("recording".equals(note.noteType) || "audionote".equals(note.noteType)) {
                    pendingExportNote = note;
                    String extension = note.filePath != null && note.filePath.contains(".") ? 
                        note.filePath.substring(note.filePath.lastIndexOf(".")) : ".m4a";
                    exportLauncher.launch(note.noteTitle + extension);
                } else {
                    Toast.makeText(requireContext(), "Only recordings can be exported as files", Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.action_convert_audio_note) {
                convertRecordingToAudioNote(note);
            }
            return true;
        });
        popup.show();
    }

    private void onTagsClick(com.yournal.model.YournalEntry note, View view) {
        if (hapticHelper != null) {
            hapticHelper.vibrateSelection();
        }
        showNoteTagsDialog(note);
    }

    private void convertRecordingToAudioNote(YournalEntry note) {
        if (!"recording".equals(note.noteType)) {
            return;
        }
        note.noteType = "audionote";
        homeViewModel.update(note);
        Toast.makeText(requireContext(), "Recording converted to Audio Note", Toast.LENGTH_SHORT).show();
    }

    private void showNoteTagsDialog(com.yournal.model.YournalEntry note) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_tags, null);
        com.google.android.material.chip.ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tags);
        com.google.android.material.textfield.TextInputEditText etNewTag = dialogView.findViewById(R.id.et_input);
        com.google.android.material.textfield.TextInputLayout tilNewTag = dialogView.findViewById(R.id.til_input);
        android.widget.TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.TextView messageView = dialogView.findViewById(R.id.tv_dialog_message);
        android.widget.Button cancelButton = dialogView.findViewById(R.id.btn_secondary);
        android.widget.Button saveButton = dialogView.findViewById(R.id.btn_primary);

        List<String> currentTags = note.tags != null ? new ArrayList<>(note.tags) : new ArrayList<>();
        titleView.setText("Manage Tags");
        messageView.setText("Select tags for this note or create a new one.");
        chipGroup.setVisibility(View.VISIBLE);
        tilNewTag.setHint("New tag");
        saveButton.setText("Save");
        cancelButton.setText("Cancel");
        
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setView(dialogView)
                .create();
        makeDialogTransparent(dialog);

        // Load all tags to show as suggestions/toggleable
        LiveData<List<String>> tagsLiveData = homeViewModel.getAllUniqueTags();
        Observer<List<String>> tagsObserver = allTags -> {
            chipGroup.removeAllViews();
            for (String tag : allTags) {
                Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.layout_filter_chip, chipGroup, false);
                chip.setText(tag);
                chip.setCheckable(true);
                chip.setChecked(currentTags.contains(tag));
                chip.setOnCheckedChangeListener((v, isChecked) -> {
                    if (isChecked) {
                        if (!currentTags.contains(tag)) currentTags.add(tag);
                    } else {
                        currentTags.remove(tag);
                    }
                });
                chipGroup.addView(chip);
            }
        };
        tagsLiveData.observe(getViewLifecycleOwner(), tagsObserver);
        dialog.setOnDismissListener(d -> tagsLiveData.removeObserver(tagsObserver));

        tilNewTag.setEndIconOnClickListener(v -> {
            String newTag = etNewTag.getText().toString().trim();
            if (!newTag.isEmpty()) {
                if (!currentTags.contains(newTag)) {
                    currentTags.add(newTag);
                    // Add a new chip immediately
                    Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.layout_filter_chip, chipGroup, false);
                    chip.setText(newTag);
                    chip.setCheckable(true);
                    chip.setChecked(true);
                    chip.setOnCheckedChangeListener((v2, isChecked) -> {
                        if (isChecked) if (!currentTags.contains(newTag)) currentTags.add(newTag);
                        else currentTags.remove(newTag);
                    });
                    chipGroup.addView(chip);
                }
                etNewTag.setText("");
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            note.tags = currentTags;
            homeViewModel.update(note);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showAddTagDialogForNote(YournalEntry note) {
        EditText etTag = new EditText(requireContext());
        new AlertDialog.Builder(requireContext())
            .setTitle("Add New Tag")
            .setView(etTag)
            .setPositiveButton("Add", (d, w) -> {
                String newTag = etTag.getText().toString().trim();
                if (!newTag.isEmpty()) {
                    if (note.tags == null) note.tags = new ArrayList<>();
                    if (!note.tags.contains(newTag)) {
                        note.tags.add(newTag);
                        homeViewModel.update(note);
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showExportMenu(YournalEntry note) {
        String[] options = {"Share", "Export as .txt", "Export as .md"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Export Note")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: shareNote(note); break;
                        case 1: exportNote(note, ".txt"); break;
                        case 2: exportNote(note, ".md"); break;
                    }
                })
                .show();
    }

    private void shareNote(YournalEntry note) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, note.noteTitle);
        intent.putExtra(Intent.EXTRA_TEXT, note.noteContent);
        startActivity(Intent.createChooser(intent, "Share Note"));
    }

    private void exportNote(YournalEntry note, String extension) {
        com.yournal.repository.SettingsRepository settingsRepo = com.yournal.repository.SettingsRepository.getInstance(requireContext());
        disposables.add(settingsRepo.getStorageUri()
                .firstOrError()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(uri -> {
                    if (uri == null || uri.isEmpty()) {
                        java.io.File dir = requireContext().getExternalFilesDir("Exports");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File file = new java.io.File(dir, note.noteTitle + extension);
                        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                            writer.write(note.noteContent);
                            android.widget.Toast.makeText(requireContext(), "Exported to: " + file.getAbsolutePath(), android.widget.Toast.LENGTH_SHORT).show();
                        } catch (java.io.IOException e) {
                            android.widget.Toast.makeText(requireContext(), "Export failed", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            android.net.Uri treeUri = android.net.Uri.parse(uri);
                            androidx.documentfile.provider.DocumentFile root = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), treeUri);
                            androidx.documentfile.provider.DocumentFile file = root.createFile("text/plain", note.noteTitle + extension);
                            if (file != null) {
                                try (java.io.OutputStream os = requireContext().getContentResolver().openOutputStream(file.getUri())) {
                                    os.write(note.noteContent.getBytes());
                                    android.widget.Toast.makeText(requireContext(), "Exported successfully", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (Exception e) {
                            android.widget.Toast.makeText(requireContext(), "SAF Export failed", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                }, throwable -> {}));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        clearSearchObservation();
        if (hapticHelper != null) {
            hapticHelper.release();
            hapticHelper = null;
        }
        binding = null;
    }
}
