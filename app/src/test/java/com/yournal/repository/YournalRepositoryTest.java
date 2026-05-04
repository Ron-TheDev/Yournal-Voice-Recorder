package com.yournal.repository;

import android.database.Cursor;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.yournal.model.AppDatabase;
import com.yournal.model.YournalDao;
import com.yournal.model.YournalEntry;
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
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class YournalRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private YournalDao dao;
    private YournalRepository repository;

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.yournalDao();
        repository = new YournalRepository(dao);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void getAllActiveEntriesSupportsSortModes() throws Exception {
        insertEntry("Charlie", "note", 1000L, false, false, null);
        insertEntry("Alpha", "note", 2000L, false, false, null);
        insertEntry("Bravo", "note", 1500L, true, false, null);

        assertEquals(Arrays.asList("Bravo", "Alpha", "Charlie"),
                titles(LiveDataTestUtil.getOrAwaitValue(repository.getAllActiveEntries(0))));
        assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha"),
                titles(LiveDataTestUtil.getOrAwaitValue(repository.getAllActiveEntries(1))));
        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"),
                titles(LiveDataTestUtil.getOrAwaitValue(repository.getAllActiveEntries(2))));
        assertEquals(Arrays.asList("Charlie", "Bravo", "Alpha"),
                titles(LiveDataTestUtil.getOrAwaitValue(repository.getAllActiveEntries(3))));
    }

    @Test
    public void getAllTagsOnlyIncludesActiveEntries() throws Exception {
        insertEntry("Active", "note", 1000L, false, false, Arrays.asList("alpha", "beta"));
        insertEntry("Deleted", "recording", 2000L, false, true, Arrays.asList("hidden"));
        insertEntry("Another active", "note", 1500L, false, false, Arrays.asList("beta", "gamma"));

        List<String> tags = LiveDataTestUtil.getOrAwaitValue(repository.getAllTags());
        assertEquals(Arrays.asList("beta", "gamma", "alpha", "beta"), tags);

        List<List<String>> tagLists = LiveDataTestUtil.getOrAwaitValue(repository.getAllTagsLists());
        assertEquals(2, tagLists.size());
        assertEquals(Arrays.asList("beta", "gamma"), tagLists.get(0));
        assertEquals(Arrays.asList("alpha", "beta"), tagLists.get(1));
    }

    @Test
    public void saveUpdateRestoreAndDeletePermanentlyWork() throws Exception {
        YournalEntry entry = new YournalEntry();
        entry.noteTitle = "Draft";
        entry.noteType = "recording";
        entry.noteContent = "original";
        entry.dateCreated = 1000L;
        entry.filePath = "/tmp/draft.m4a";
        entry.tags = Arrays.asList("recording");
        entry.amplitudes = Arrays.asList(0.1f, 0.2f);

        CountDownLatch saveLatch = new CountDownLatch(1);
        repository.save(entry, saveLatch::countDown);
        waitFor(() -> saveLatch.getCount() == 0);
        int entryId = findSingleEntryId();

        YournalEntry stored = dao.getNoteById(entryId);
        assertNotNull(stored);
        assertEquals("Draft", stored.noteTitle);

        stored.noteContent = "updated";
        repository.update(stored);
        waitFor(() -> "updated".equals(dao.getNoteById(entryId).noteContent));
        assertEquals("updated", repository.getNoteByIdSync(entryId).noteContent);

        stored.isDeleted = true;
        stored.dateDeleted = System.currentTimeMillis();
        repository.update(stored);
        waitFor(() -> dao.getNoteById(entryId).isDeleted);

        repository.restore(entryId);
        waitFor(() -> !dao.getNoteById(entryId).isDeleted);

        repository.deletePermanently(stored);
        waitFor(() -> dao.getNoteById(entryId) == null);
    }

    @Test
    public void emptyRecycleBinRemovesDeletedEntries() throws Exception {
        insertEntry("Deleted one", "recording", 1000L, false, true, Arrays.asList("trash"));
        insertEntry("Deleted two", "note", 2000L, false, true, Arrays.asList("trash"));
        insertEntry("Active", "note", 3000L, false, false, Arrays.asList("keep"));

        repository.emptyRecycleBin();
        waitFor(() -> countDeletedRows() == 0);
        assertEquals(Integer.valueOf(0), LiveDataTestUtil.getOrAwaitValue(repository.getDeletedEntriesCount()));
        assertEquals(Integer.valueOf(0), countDeletedRows());
    }

    private long insertEntry(String title, String type, long createdAt, boolean pinned, boolean deleted, List<String> tags) {
        YournalEntry entry = new YournalEntry();
        entry.noteTitle = title;
        entry.noteType = type;
        entry.noteContent = title + " content";
        entry.dateCreated = createdAt;
        entry.dateDeleted = deleted ? createdAt + 500L : 0L;
        entry.isPinned = pinned;
        entry.isDeleted = deleted;
        entry.isFavorite = false;
        entry.filePath = title + ".m4a";
        entry.tags = tags;
        entry.amplitudes = Arrays.asList(0.25f, 0.5f);
        return repository.insert(entry);
    }

    private int findSingleEntryId() {
        Cursor cursor = database.getOpenHelper().getWritableDatabase()
                .query("SELECT id FROM yournal_entries LIMIT 1");
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private void waitFor(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.isSatisfied()) {
                return;
            }
            Thread.sleep(20L);
        }
        assertTrue("Condition was not satisfied before timeout", condition.isSatisfied());
    }

    private Integer countDeletedRows() {
        Cursor cursor = database.getOpenHelper().getWritableDatabase()
                .query("SELECT COUNT(*) FROM yournal_entries WHERE isDeleted = 1");
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private List<String> titles(List<YournalEntry> entries) {
        List<String> titles = new java.util.ArrayList<>();
        for (YournalEntry entry : entries) {
            titles.add(entry.noteTitle);
        }
        return titles;
    }

    private interface Condition {
        boolean isSatisfied();
    }
}
