package com.yournal.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import com.yournal.model.YournalEntry;
import com.yournal.repository.YournalRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;

public class HomeViewModel extends AndroidViewModel {

    private YournalRepository repository;
    
    // Sort modes: 0: Date Desc, 1: Date Asc, 2: Title Asc, 3: Title Desc
    private final MutableLiveData<Integer> sortMode = new MutableLiveData<>(0);
    private final MutableLiveData<String> typeFilter = new MutableLiveData<>(null);
    private final MutableLiveData<String> tagFilter = new MutableLiveData<>(null);
    
    private final LiveData<List<YournalEntry>> filteredNotes;
    private LiveData<List<YournalEntry>> deletedEntriesForCleanup;
    private final Observer<List<YournalEntry>> deletedEntriesCleanupObserver = entries -> {
        if (entries != null) {
            long thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000;
            long now = System.currentTimeMillis();
            for (YournalEntry entry : entries) {
                if (now - entry.dateDeleted > thirtyDaysMillis) {
                    repository.deletePermanently(entry);
                }
            }
        }
    };

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = new YournalRepository(application);
        
        // React to sort, type, or tag changes
        filteredNotes = Transformations.switchMap(sortMode, mode ->
            Transformations.switchMap(typeFilter, type ->
                Transformations.switchMap(tagFilter, tag ->
                    Transformations.map(repository.getAllActiveEntries(mode), list ->
                            applyFilters(list, type, tag))
                )
            )
        );

        // Run cleanup on init
        cleanupOldDeletedEntries();
    }

    private void cleanupOldDeletedEntries() {
        deletedEntriesForCleanup = repository.getDeletedEntries();
        deletedEntriesForCleanup.observeForever(deletedEntriesCleanupObserver);
    }

    private List<YournalEntry> applyFilters(List<YournalEntry> list, String type, String tag) {
        List<YournalEntry> result = new ArrayList<>();
        if (list == null) return result;

        for (YournalEntry entry : list) {
            boolean typeMatches = type == null || type.equals(entry.noteType);
            boolean tagMatches = tag == null
                    || (entry.tags != null && entry.tags.contains(tag));
            if (typeMatches && tagMatches) {
                result.add(entry);
            }
        }
        return result;
    }

    public LiveData<List<YournalEntry>> getFilteredNotes() {
        return filteredNotes;
    }
    
    public void setSortMode(int mode) {
        sortMode.setValue(mode);
    }

    public LiveData<Integer> getSortMode() {
        return sortMode;
    }

    public int getSortModeValue() {
        return sortMode.getValue() != null ? sortMode.getValue() : 0;
    }

    public void setTypeFilter(String type) {
        typeFilter.setValue(type);
    }

    public void setTagFilter(String tag) {
        tagFilter.setValue(tag);
    }

    public void clearTypeFilter() {
        typeFilter.setValue(null);
    }

    public void clearTagFilter() {
        tagFilter.setValue(null);
    }

    public void clearFilters() {
        typeFilter.setValue(null);
        tagFilter.setValue(null);
    }

    public LiveData<String> getTypeFilter() {
        return typeFilter;
    }

    public LiveData<String> getTagFilter() {
        return tagFilter;
    }

    public LiveData<List<String>> getAllUniqueTags() {
        return Transformations.map(repository.getAllTagsLists(), lists -> {
            Set<String> uniqueTags = new HashSet<>();
            if (lists != null) {
                for (List<String> list : lists) {
                    if (list != null) {
                        uniqueTags.addAll(list);
                    }
                }
            }
            List<String> result = new ArrayList<>(uniqueTags);
            Collections.sort(result);
            return result;
        });
    }

    public LiveData<List<YournalEntry>> searchNotes(String query) {
        return repository.searchEntries(query);
    }

    public LiveData<Integer> getDeletedCount() {
        return repository.getDeletedEntriesCount();
    }

    public LiveData<List<YournalEntry>> getDeletedEntries() {
        return repository.getDeletedEntries();
    }

    public LiveData<List<YournalEntry>> getRecordings() {
        return Transformations.map(repository.getAllActiveEntries(0), entries -> {
            List<YournalEntry> result = new ArrayList<>();
            if (entries != null) {
                for (YournalEntry entry : entries) {
                    if ("recording".equals(entry.noteType) || "audionote".equals(entry.noteType)) {
                        result.add(entry);
                    }
                }
            }
            return result;
        });
    }

    public void restore(int id) {
        repository.restore(id);
    }

    public Flowable<YournalEntry> getNoteById(int id) {
        return repository.getNoteById(id);
    }

    public void deletePermanently(YournalEntry entry) {
        repository.deletePermanently(entry);
    }

    public void emptyBin() {
        repository.emptyRecycleBin();
    }

    public LiveData<Integer> getRecordingsCount() {
        return repository.getRecordingsCount();
    }

    public void insert(YournalEntry entry) {
        YournalRepository.databaseWriteExecutor.execute(() -> repository.insert(entry));
    }

    public void update(YournalEntry entry) {
        repository.update(entry);
    }

    public void delete(YournalEntry entry) {
        // Move to recycle bin
        entry.isDeleted = true;
        entry.dateDeleted = System.currentTimeMillis();
        repository.update(entry);
    }

    public void insert(YournalEntry entry, Object o) {
    }

    public void save(YournalEntry entry, Runnable onComplete) {
        repository.save(entry, onComplete);
    }

    @Override
    protected void onCleared() {
        if (deletedEntriesForCleanup != null) {
            deletedEntriesForCleanup.removeObserver(deletedEntriesCleanupObserver);
            deletedEntriesForCleanup = null;
        }
        super.onCleared();
    }
}
