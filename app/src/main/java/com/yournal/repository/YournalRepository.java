package com.yournal.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.yournal.model.AppDatabase;
import com.yournal.model.YournalEntry;
import com.yournal.model.YournalDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import java.util.ArrayList;

public class YournalRepository {

    private YournalDao yournalDao;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public YournalRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        yournalDao = db.yournalDao();
    }

    YournalRepository(YournalDao yournalDao) {
        this.yournalDao = yournalDao;
    }

    public LiveData<List<YournalEntry>> getAllActiveEntries(int sortMode) {
        switch (sortMode) {
            case 1: return yournalDao.getAllActiveEntriesAsc(); // Date Oldest
            case 2: return yournalDao.getAllActiveEntriesByTitleAsc(); // A-Z
            case 3: return yournalDao.getAllActiveEntriesByTitleDesc(); // Z-A
            case 0:
            default: return yournalDao.getAllActiveEntriesDesc(); // Date Newest
        }
    }

    public LiveData<List<YournalEntry>> searchEntries(String query) {
        return yournalDao.searchEntries(query);
    }

    public LiveData<List<YournalEntry>> getEntriesByType(String type, int sortMode) {
        switch (sortMode) {
            case 1:
                return yournalDao.getEntriesByTypeAsc(type);
            case 2:
                return yournalDao.getEntriesByTypeDesc(type);
            case 3:
                return yournalDao.getEntriesByTypeDesc(type);
            case 0:
            default:
                return yournalDao.getEntriesByTypeDesc(type);
        }
    }

    public LiveData<List<YournalEntry>> getDeletedEntries() {
        return yournalDao.getDeletedEntries();
    }

    public LiveData<Integer> getDeletedEntriesCount() {
        return yournalDao.getDeletedEntriesCount();
    }

    public LiveData<Integer> getRecordingsCount() {
        return yournalDao.getRecordingsCount();
    }

    public long insert(YournalEntry entry) {
        return yournalDao.insertEntry(entry);
    }

    public void update(YournalEntry entry) {
        databaseWriteExecutor.execute(() -> yournalDao.updateEntry(entry));
    }

    public void restore(int id) {
        databaseWriteExecutor.execute(() -> yournalDao.restoreEntry(id));
    }

    public void deletePermanently(YournalEntry entry) {
        databaseWriteExecutor.execute(() -> yournalDao.deletePermanently(entry));
    }

    public void emptyRecycleBin() {
        databaseWriteExecutor.execute(() -> yournalDao.emptyRecycleBin());
    }

    public io.reactivex.rxjava3.core.Flowable<YournalEntry> getNoteById(int id) {
        return yournalDao.getNoteByIdFlowable(id);
    }

    public YournalEntry getNoteByIdSync(int id) {
        return yournalDao.getNoteById(id);
    }

    public void updateSync(YournalEntry entry) {
        yournalDao.updateEntry(entry);
    }

    public void save(YournalEntry entry, Runnable onComplete) {
        databaseWriteExecutor.execute(() -> {
            persistEntry(entry);
            if (onComplete != null) onComplete.run();
        });
    }

    public void saveRecording(YournalEntry entry, MutableLiveData<Boolean> result) {
        databaseWriteExecutor.execute(() -> {
            persistEntry(entry);
            result.postValue(true);
        });
    }

    private void persistEntry(YournalEntry entry) {
        if (entry != null && entry.id > 0) {
            yournalDao.updateEntry(entry);
        } else {
            yournalDao.insertEntry(entry);
        }
    }



    // -------------------------
    // TAGS (FIXED)
    // -------------------------

    public LiveData<List<String>> getAllTags() {
        return Transformations.map(
                yournalDao.getAllActiveEntriesDesc(),
                entries -> {
                    List<String> tags = new ArrayList<>();
                    if (entries != null) {
                        for (YournalEntry entry : entries) {
                            if (entry.tags != null) {
                                tags.addAll(entry.tags);
                            }
                        }
                    }
                    return tags;
                }
        );
    }

    public LiveData<List<List<String>>> getAllTagsLists() {
        return Transformations.map(yournalDao.getAllActiveEntriesDesc(), entries -> {
            List<List<String>> tagLists = new ArrayList<>();
            if (entries != null) {
                for (com.yournal.model.YournalEntry entry : entries) {
                    if (entry.tags != null) {
                        tagLists.add(entry.tags);
                    }
                }
            }
            return tagLists;
        });
    }
}
