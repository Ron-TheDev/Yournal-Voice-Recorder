package com.yournal.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface YournalDao {

    @Insert
    long insertEntry(YournalEntry entry);
    
    @Update
    void updateEntry(YournalEntry entry);
    
    @Delete
    void deleteEntry(YournalEntry entry);

    // Get all Active Notes (not deleted)
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY isPinned DESC, dateCreated DESC")
    LiveData<List<YournalEntry>> getAllActiveEntriesDesc();
    
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY isPinned DESC, dateCreated ASC")
    LiveData<List<YournalEntry>> getAllActiveEntriesAsc();

    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY noteTitle ASC")
    LiveData<List<YournalEntry>> getAllActiveEntriesByTitleAsc();

    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY noteTitle DESC")
    LiveData<List<YournalEntry>> getAllActiveEntriesByTitleDesc();

    // Case insensitive text search
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 AND (noteTitle LIKE '%' || :searchQuery || '%' OR noteContent LIKE '%' || :searchQuery || '%') ORDER BY dateCreated DESC")
    LiveData<List<YournalEntry>> searchEntries(String searchQuery);

    // Filter by noteType
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 AND noteType = :type ORDER BY isPinned DESC, dateCreated DESC")
    LiveData<List<YournalEntry>> getEntriesByTypeDesc(String type);
    
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 0 AND noteType = :type ORDER BY isPinned DESC, dateCreated ASC")
    LiveData<List<YournalEntry>> getEntriesByTypeAsc(String type);

    // Recycle bin items
    @Query("SELECT * FROM yournal_entries WHERE isDeleted = 1 ORDER BY dateDeleted DESC")
    LiveData<List<YournalEntry>> getDeletedEntries();

    @Query("SELECT COUNT(*) FROM yournal_entries WHERE isDeleted = 1")
    LiveData<Integer> getDeletedEntriesCount();

    @Query("SELECT COUNT(*) FROM yournal_entries WHERE isDeleted = 0 AND noteType = 'recording'")
    LiveData<Integer> getRecordingsCount();

    // Permanent deletion
    @Delete
    void deletePermanently(YournalEntry entry);

    @Query("DELETE FROM yournal_entries WHERE isDeleted = 1")
    void emptyRecycleBin();

    @Query("UPDATE yournal_entries SET isDeleted = 0 WHERE id = :id")
    void restoreEntry(int id);

    @Query("SELECT * FROM yournal_entries WHERE id = :id")
    io.reactivex.rxjava3.core.Flowable<YournalEntry> getNoteByIdFlowable(int id);

    @Query("SELECT * FROM yournal_entries WHERE id = :id")
    YournalEntry getNoteById(int id);
}
