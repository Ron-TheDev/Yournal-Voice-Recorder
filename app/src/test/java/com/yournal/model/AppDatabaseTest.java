package com.yournal.model;

import android.database.Cursor;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.yournal.testutil.LiveDataTestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppDatabaseTest {

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
    public void daoReturnsEntriesInPinnedThenNewestOrder() throws Exception {
        insertEntry("Old note", "note", 1000L, false, false, null, null);
        insertEntry("Pinned note", "note", 2000L, true, false, null, null);
        insertEntry("Newest note", "note", 3000L, false, false, null, null);

        List<YournalEntry> entries = LiveDataTestUtil.getOrAwaitValue(dao.getAllActiveEntriesDesc());

        assertEquals(Arrays.asList("Pinned note", "Newest note", "Old note"), titles(entries));
    }

    @Test
    public void searchAndRecycleBinQueriesRespectDeletionState() throws Exception {
        insertEntry("Alpha title", "note", 1000L, false, false, "find me", null);
        insertEntry("Deleted title", "recording", 2000L, false, true, "hidden content", 2500L);
        insertEntry("Recording title", "recording", 3000L, false, false, "transcript", null);

        List<YournalEntry> searchResults = LiveDataTestUtil.getOrAwaitValue(dao.searchEntries("find"));
        assertEquals(1, searchResults.size());
        assertEquals("Alpha title", searchResults.get(0).noteTitle);

        List<YournalEntry> deletedEntries = LiveDataTestUtil.getOrAwaitValue(dao.getDeletedEntries());
        assertEquals(1, deletedEntries.size());
        assertEquals("Deleted title", deletedEntries.get(0).noteTitle);

        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(dao.getDeletedEntriesCount()));
        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(dao.getRecordingsCount()));
    }

    @Test
    public void restoreAndEmptyRecycleBinUpdateCounts() throws Exception {
        int deletedId = (int) insertEntry("Deleted title", "recording", 2000L, false, true, "hidden content", 2500L);
        insertEntry("Active title", "note", 1000L, false, false, "active content", null);

        dao.restoreEntry(deletedId);
        YournalEntry restored = dao.getNoteById(deletedId);
        assertNotNull(restored);
        assertTrue(!restored.isDeleted);

        dao.emptyRecycleBin();
        assertEquals(Integer.valueOf(0), LiveDataTestUtil.getOrAwaitValue(dao.getDeletedEntriesCount()));
    }

    @Test
    public void schemaCreatesExpectedIndices() {
        Cursor cursor = database.getOpenHelper().getWritableDatabase()
                .query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='yournal_entries' ORDER BY name");
        try {
            assertTrue(cursor.moveToFirst());
            List<String> indexNames = new java.util.ArrayList<>();
            do {
                indexNames.add(cursor.getString(0));
            } while (cursor.moveToNext());

            assertTrue(indexNames.contains("index_yournal_entries_isDeleted_isPinned_dateCreated"));
            assertTrue(indexNames.contains("index_yournal_entries_isDeleted_noteType_dateCreated"));
            assertTrue(indexNames.contains("index_yournal_entries_dateDeleted"));
            assertTrue(indexNames.contains("index_yournal_entries_noteTitle"));
        } finally {
            cursor.close();
        }
    }

    private long insertEntry(String title, String type, long createdAt, boolean pinned, boolean deleted, String content, Long deletedAt) {
        YournalEntry entry = new YournalEntry();
        entry.noteTitle = title;
        entry.noteType = type;
        entry.noteContent = content;
        entry.dateCreated = createdAt;
        entry.dateDeleted = deletedAt == null ? 0L : deletedAt;
        entry.isPinned = pinned;
        entry.isDeleted = deleted;
        entry.isFavorite = false;
        entry.filePath = title + ".m4a";
        entry.tags = Arrays.asList("tag-a", "tag-b");
        entry.amplitudes = Arrays.asList(0.1f, 0.2f);
        return dao.insertEntry(entry);
    }

    private List<String> titles(List<YournalEntry> entries) {
        List<String> titles = new java.util.ArrayList<>();
        for (YournalEntry entry : entries) {
            titles.add(entry.noteTitle);
        }
        return titles;
    }
}
