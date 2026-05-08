package com.yournal.model;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.rxjava3.RxRoom;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class YournalDao_Impl implements YournalDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<YournalEntry> __insertionAdapterOfYournalEntry;

  private final EntityDeletionOrUpdateAdapter<YournalEntry> __deletionAdapterOfYournalEntry;

  private final EntityDeletionOrUpdateAdapter<YournalEntry> __updateAdapterOfYournalEntry;

  private final SharedSQLiteStatement __preparedStmtOfEmptyRecycleBin;

  private final SharedSQLiteStatement __preparedStmtOfRestoreEntry;

  public YournalDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfYournalEntry = new EntityInsertionAdapter<YournalEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `yournal_entries` (`id`,`noteTitle`,`filePath`,`noteType`,`noteContent`,`dateCreated`,`dateDeleted`,`isPinned`,`isDeleted`,`isFavorite`,`tags`,`amplitudes`,`attachments`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final YournalEntry entity) {
        statement.bindLong(1, entity.id);
        if (entity.noteTitle == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.noteTitle);
        }
        if (entity.filePath == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.filePath);
        }
        if (entity.noteType == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.noteType);
        }
        if (entity.noteContent == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.noteContent);
        }
        statement.bindLong(6, entity.dateCreated);
        statement.bindLong(7, entity.dateDeleted);
        final int _tmp = entity.isPinned ? 1 : 0;
        statement.bindLong(8, _tmp);
        final int _tmp_1 = entity.isDeleted ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final int _tmp_2 = entity.isFavorite ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final String _tmp_3 = Converters.fromList(entity.tags);
        if (_tmp_3 == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, _tmp_3);
        }
        final String _tmp_4 = Converters.fromFloatList(entity.amplitudes);
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final String _tmp_5 = Converters.fromAttachmentList(entity.attachments);
        if (_tmp_5 == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, _tmp_5);
        }
      }
    };
    this.__deletionAdapterOfYournalEntry = new EntityDeletionOrUpdateAdapter<YournalEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `yournal_entries` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final YournalEntry entity) {
        statement.bindLong(1, entity.id);
      }
    };
    this.__updateAdapterOfYournalEntry = new EntityDeletionOrUpdateAdapter<YournalEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `yournal_entries` SET `id` = ?,`noteTitle` = ?,`filePath` = ?,`noteType` = ?,`noteContent` = ?,`dateCreated` = ?,`dateDeleted` = ?,`isPinned` = ?,`isDeleted` = ?,`isFavorite` = ?,`tags` = ?,`amplitudes` = ?,`attachments` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final YournalEntry entity) {
        statement.bindLong(1, entity.id);
        if (entity.noteTitle == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.noteTitle);
        }
        if (entity.filePath == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.filePath);
        }
        if (entity.noteType == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.noteType);
        }
        if (entity.noteContent == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.noteContent);
        }
        statement.bindLong(6, entity.dateCreated);
        statement.bindLong(7, entity.dateDeleted);
        final int _tmp = entity.isPinned ? 1 : 0;
        statement.bindLong(8, _tmp);
        final int _tmp_1 = entity.isDeleted ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final int _tmp_2 = entity.isFavorite ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final String _tmp_3 = Converters.fromList(entity.tags);
        if (_tmp_3 == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, _tmp_3);
        }
        final String _tmp_4 = Converters.fromFloatList(entity.amplitudes);
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final String _tmp_5 = Converters.fromAttachmentList(entity.attachments);
        if (_tmp_5 == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, _tmp_5);
        }
        statement.bindLong(14, entity.id);
      }
    };
    this.__preparedStmtOfEmptyRecycleBin = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM yournal_entries WHERE isDeleted = 1";
        return _query;
      }
    };
    this.__preparedStmtOfRestoreEntry = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE yournal_entries SET isDeleted = 0 WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public long insertEntry(final YournalEntry entry) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfYournalEntry.insertAndReturnId(entry);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteEntry(final YournalEntry entry) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfYournalEntry.handle(entry);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deletePermanently(final YournalEntry entry) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfYournalEntry.handle(entry);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateEntry(final YournalEntry entry) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfYournalEntry.handle(entry);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void emptyRecycleBin() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfEmptyRecycleBin.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfEmptyRecycleBin.release(_stmt);
    }
  }

  @Override
  public void restoreEntry(final int id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfRestoreEntry.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfRestoreEntry.release(_stmt);
    }
  }

  @Override
  public LiveData<List<YournalEntry>> getAllActiveEntriesDesc() {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY isPinned DESC, dateCreated DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getAllActiveEntriesAsc() {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY isPinned DESC, dateCreated ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getAllActiveEntriesByTitleAsc() {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY noteTitle ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getAllActiveEntriesByTitleDesc() {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 ORDER BY noteTitle DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> searchEntries(final String searchQuery) {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 AND (noteTitle LIKE '%' || ? || '%' OR noteContent LIKE '%' || ? || '%') ORDER BY dateCreated DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (searchQuery == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchQuery);
    }
    _argIndex = 2;
    if (searchQuery == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchQuery);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getEntriesByTypeDesc(final String type) {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 AND noteType = ? ORDER BY isPinned DESC, dateCreated DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getEntriesByTypeAsc(final String type) {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 0 AND noteType = ? ORDER BY isPinned DESC, dateCreated ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<YournalEntry>> getDeletedEntries() {
    final String _sql = "SELECT * FROM yournal_entries WHERE isDeleted = 1 ORDER BY dateDeleted DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<List<YournalEntry>>() {
      @Override
      @Nullable
      public List<YournalEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<YournalEntry> _result = new ArrayList<YournalEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final YournalEntry _item;
            _item = new YournalEntry();
            _item.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _item.noteTitle = null;
            } else {
              _item.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _item.filePath = null;
            } else {
              _item.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _item.noteType = null;
            } else {
              _item.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _item.noteContent = null;
            } else {
              _item.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _item.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _item.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _item.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _item.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _item.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _item.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _item.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _item.attachments = Converters.fromAttachmentString(_tmp_5);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<Integer> getDeletedEntriesCount() {
    final String _sql = "SELECT COUNT(*) FROM yournal_entries WHERE isDeleted = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<Integer> getRecordingsCount() {
    final String _sql = "SELECT COUNT(*) FROM yournal_entries WHERE isDeleted = 0 AND noteType = 'recording'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"yournal_entries"}, false, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flowable<YournalEntry> getNoteByIdFlowable(final int id) {
    final String _sql = "SELECT * FROM yournal_entries WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return RxRoom.createFlowable(__db, false, new String[] {"yournal_entries"}, new Callable<YournalEntry>() {
      @Override
      public YournalEntry call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
          final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
          final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
          final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final YournalEntry _result;
          if (_cursor.moveToFirst()) {
            _result = new YournalEntry();
            _result.id = _cursor.getInt(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
              _result.noteTitle = null;
            } else {
              _result.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
            }
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _result.filePath = null;
            } else {
              _result.filePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            if (_cursor.isNull(_cursorIndexOfNoteType)) {
              _result.noteType = null;
            } else {
              _result.noteType = _cursor.getString(_cursorIndexOfNoteType);
            }
            if (_cursor.isNull(_cursorIndexOfNoteContent)) {
              _result.noteContent = null;
            } else {
              _result.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
            }
            _result.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
            _result.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _result.isPinned = _tmp != 0;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _result.isDeleted = _tmp_1 != 0;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _result.isFavorite = _tmp_2 != 0;
            final String _tmp_3;
            if (_cursor.isNull(_cursorIndexOfTags)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getString(_cursorIndexOfTags);
            }
            _result.tags = Converters.fromString(_tmp_3);
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
            }
            _result.amplitudes = Converters.fromFloatString(_tmp_4);
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfAttachments)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
            }
            _result.attachments = Converters.fromAttachmentString(_tmp_5);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public YournalEntry getNoteById(final int id) {
    final String _sql = "SELECT * FROM yournal_entries WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfNoteTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "noteTitle");
      final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
      final int _cursorIndexOfNoteType = CursorUtil.getColumnIndexOrThrow(_cursor, "noteType");
      final int _cursorIndexOfNoteContent = CursorUtil.getColumnIndexOrThrow(_cursor, "noteContent");
      final int _cursorIndexOfDateCreated = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCreated");
      final int _cursorIndexOfDateDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "dateDeleted");
      final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
      final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
      final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final int _cursorIndexOfAmplitudes = CursorUtil.getColumnIndexOrThrow(_cursor, "amplitudes");
      final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
      final YournalEntry _result;
      if (_cursor.moveToFirst()) {
        _result = new YournalEntry();
        _result.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfNoteTitle)) {
          _result.noteTitle = null;
        } else {
          _result.noteTitle = _cursor.getString(_cursorIndexOfNoteTitle);
        }
        if (_cursor.isNull(_cursorIndexOfFilePath)) {
          _result.filePath = null;
        } else {
          _result.filePath = _cursor.getString(_cursorIndexOfFilePath);
        }
        if (_cursor.isNull(_cursorIndexOfNoteType)) {
          _result.noteType = null;
        } else {
          _result.noteType = _cursor.getString(_cursorIndexOfNoteType);
        }
        if (_cursor.isNull(_cursorIndexOfNoteContent)) {
          _result.noteContent = null;
        } else {
          _result.noteContent = _cursor.getString(_cursorIndexOfNoteContent);
        }
        _result.dateCreated = _cursor.getLong(_cursorIndexOfDateCreated);
        _result.dateDeleted = _cursor.getLong(_cursorIndexOfDateDeleted);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
        _result.isPinned = _tmp != 0;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfIsDeleted);
        _result.isDeleted = _tmp_1 != 0;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfIsFavorite);
        _result.isFavorite = _tmp_2 != 0;
        final String _tmp_3;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_3 = null;
        } else {
          _tmp_3 = _cursor.getString(_cursorIndexOfTags);
        }
        _result.tags = Converters.fromString(_tmp_3);
        final String _tmp_4;
        if (_cursor.isNull(_cursorIndexOfAmplitudes)) {
          _tmp_4 = null;
        } else {
          _tmp_4 = _cursor.getString(_cursorIndexOfAmplitudes);
        }
        _result.amplitudes = Converters.fromFloatString(_tmp_4);
        final String _tmp_5;
        if (_cursor.isNull(_cursorIndexOfAttachments)) {
          _tmp_5 = null;
        } else {
          _tmp_5 = _cursor.getString(_cursorIndexOfAttachments);
        }
        _result.attachments = Converters.fromAttachmentString(_tmp_5);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
