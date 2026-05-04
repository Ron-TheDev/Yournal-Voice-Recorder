package com.yournal.model;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yournal.testutil.LiveDataTestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseInstrumentedTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private YournalDao dao;

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.yournalDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void insertsAndQueriesActiveEntries() throws Exception {
        insertEntry("Alpha", "note", 1000L, false, false, "alpha content");
        insertEntry("Beta", "recording", 2000L, true, false, "beta content");

        List<YournalEntry> entries = LiveDataTestUtil.getOrAwaitValue(dao.getAllActiveEntriesDesc());
        assertEquals(2, entries.size());
        assertEquals("Beta", entries.get(0).noteTitle);
        assertEquals("Alpha", entries.get(1).noteTitle);
    }

    @Test
    public void deletedCountsAndRecycleBinQueriesMatchState() throws Exception {
        insertEntry("Active", "note", 1000L, false, false, "active content");
        insertEntry("Deleted", "recording", 2000L, false, true, "deleted content");

        List<YournalEntry> deleted = LiveDataTestUtil.getOrAwaitValue(dao.getDeletedEntries());
        assertEquals(1, deleted.size());
        assertEquals("Deleted", deleted.get(0).noteTitle);
        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(dao.getDeletedEntriesCount()));
        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(dao.getRecordingsCount()));
    }

    @Test
    public void restoreEntryClearsDeletedFlag() {
        int id = (int) insertEntry("Deleted", "recording", 2000L, false, true, "deleted content");
        dao.restoreEntry(id);

        YournalEntry restored = dao.getNoteById(id);
        assertNotNull(restored);
        assertEquals(false, restored.isDeleted);
    }

    private long insertEntry(String title, String type, long createdAt, boolean pinned, boolean deleted, String content) {
        YournalEntry entry = new YournalEntry();
        entry.noteTitle = title;
        entry.noteType = type;
        entry.noteContent = content;
        entry.dateCreated = createdAt;
        entry.dateDeleted = deleted ? createdAt + 500L : 0L;
        entry.isPinned = pinned;
        entry.isDeleted = deleted;
        entry.isFavorite = false;
        entry.filePath = title + ".m4a";
        entry.tags = Arrays.asList("tag-a", "tag-b");
        entry.amplitudes = Arrays.asList(0.1f, 0.2f);
        return dao.insertEntry(entry);
    }
}
