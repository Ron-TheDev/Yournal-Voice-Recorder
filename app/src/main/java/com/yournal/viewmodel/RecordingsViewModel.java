package com.yournal.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.yournal.model.YournalEntry;
import com.yournal.repository.YournalRepository;

import java.util.List;

public class RecordingsViewModel extends AndroidViewModel {

    private YournalRepository repository;
    private LiveData<List<YournalEntry>> allRecordings;

    public RecordingsViewModel(@NonNull Application application) {
        super(application);
        repository = new YournalRepository(application);
        allRecordings = repository.getEntriesByType("recording", 0);
    }

    public LiveData<List<YournalEntry>> getAllRecordings() {
        return allRecordings;
    }

    public void insert(YournalEntry recording) {
        repository.insert(recording);
    }

    public void update(YournalEntry recording) {
        repository.update(recording);
    }
}
