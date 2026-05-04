package com.yournal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.yournal.databinding.FragmentRecycleBinBinding;
import com.yournal.model.YournalEntry;
import com.yournal.viewmodel.HomeViewModel;

public class RecycleBinFragment extends Fragment {

    private FragmentRecycleBinBinding binding;
    private HomeViewModel homeViewModel;
    private NoteAdapter adapter;
    private com.yournal.repository.SettingsRepository settingsRepository;
    private com.yournal.util.HapticHelper hapticHelper;
    private final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecycleBinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        settingsRepository = com.yournal.repository.SettingsRepository.getInstance(requireContext());
        hapticHelper = new com.yournal.util.HapticHelper(requireContext());
        
        setupRecyclerView();
        setupActions();
        observeData();
        observeAccentColor();
        setupSelectionBar();
    }

    private void observeAccentColor() {
        disposables.add(settingsRepository.getAccentColor()
                .distinctUntilChanged()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    if (color != 0) {
                        binding.cardSelection.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(color));
                        if (adapter != null) adapter.setAccentColor(color);
                    }
                }));
    }

    private void setupSelectionBar() {
        binding.btnRestoreSelected.setOnClickListener(v -> restoreSelectedItems());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedItemsPermanently());
        binding.btnClearSelection.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
            updateSelectionUI();
        });
    }

    private void restoreSelectedItems() {
        java.util.Set<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) return;
        
        for (Integer id : selectedIds) {
            homeViewModel.restore(id);
        }
        adapter.setSelectionMode(false);
        updateSelectionUI();
    }

    private void deleteSelectedItemsPermanently() {
        java.util.Set<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Yournal_Custom_Dialog_Style)
                .setTitle("Delete Permanently?")
                .setMessage("Are you sure you want to permanently delete " + selectedIds.size() + " items?")
                .setPositiveButton("Delete", (d, w) -> {
                    for (YournalEntry entry : adapter.getCurrentList()) {
                        if (selectedIds.contains(entry.id)) {
                            homeViewModel.deletePermanently(entry);
                        }
                    }
                    adapter.setSelectionMode(false);
                    updateSelectionUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        adapter = new NoteAdapter(this::onItemClick, this::onItemLongClick, this::onOverflowClick);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
    }

    private void onItemClick(YournalEntry note, View view) {
        if (!adapter.getSelectedIds().isEmpty()) {
            adapter.toggleSelection(note.id);
            updateSelectionUI();
        } else {
            showItemActions(note);
        }
    }

    private void onItemLongClick(YournalEntry note, View view) {
        hapticHelper.vibrateSelection();
        adapter.setSelectionMode(true);
        adapter.toggleSelection(note.id);
        updateSelectionUI();
    }

    private void onOverflowClick(YournalEntry note, View view) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(requireContext(), view);
        popup.getMenu().add("Restore").setOnMenuItemClickListener(i -> {
            homeViewModel.restore(note.id);
            return true;
        });
        popup.getMenu().add("Delete Permanently").setOnMenuItemClickListener(i -> {
            homeViewModel.deletePermanently(note);
            return true;
        });
        popup.show();
    }

    private void updateSelectionUI() {
        java.util.Set<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            binding.cardSelection.setVisibility(View.GONE);
        } else {
            binding.cardSelection.setVisibility(View.VISIBLE);
            binding.tvSelectionCount.setText(selectedIds.size() + " selected");
        }
    }

    private void observeData() {
        homeViewModel.getDeletedEntries().observe(getViewLifecycleOwner(), notes -> {
            adapter.submitList(notes);
        });

        homeViewModel.getDeletedCount().observe(getViewLifecycleOwner(), count -> {
            binding.tvItemCount.setText((count != null ? count : 0) + " items");
        });
    }

    private void setupActions() {
        binding.btnEmptyBin.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Empty Recycle Bin?")
                    .setMessage("This will permanently delete all items in the recycle bin. This action cannot be undone.")
                    .setPositiveButton("Empty", (dialog, which) -> homeViewModel.emptyBin())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showItemActions(YournalEntry note) {
        String[] options = {"Restore", "Delete Permanently"};
        new AlertDialog.Builder(requireContext())
                .setTitle(note.noteTitle)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        homeViewModel.restore(note.id);
                    } else {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete Permanently?")
                                .setMessage("Are you sure you want to permanently delete this note?")
                                .setPositiveButton("Delete", (d, w) -> homeViewModel.deletePermanently(note))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        if (hapticHelper != null) {
            hapticHelper.release();
            hapticHelper = null;
        }
        binding = null;
    }
}
