package com.yournal.model;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile YournalDao _yournalDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(7) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `yournal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteTitle` TEXT, `filePath` TEXT, `noteType` TEXT, `noteContent` TEXT, `dateCreated` INTEGER NOT NULL, `dateDeleted` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `tags` TEXT, `amplitudes` TEXT, `attachments` TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_yournal_entries_isDeleted_isPinned_dateCreated` ON `yournal_entries` (`isDeleted`, `isPinned`, `dateCreated`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_yournal_entries_isDeleted_noteType_dateCreated` ON `yournal_entries` (`isDeleted`, `noteType`, `dateCreated`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_yournal_entries_dateDeleted` ON `yournal_entries` (`dateDeleted`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_yournal_entries_noteTitle` ON `yournal_entries` (`noteTitle`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7589460c9118f56eca1e98fc4e0326d9')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `yournal_entries`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsYournalEntries = new HashMap<String, TableInfo.Column>(13);
        _columnsYournalEntries.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("noteTitle", new TableInfo.Column("noteTitle", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("filePath", new TableInfo.Column("filePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("noteType", new TableInfo.Column("noteType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("noteContent", new TableInfo.Column("noteContent", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("dateCreated", new TableInfo.Column("dateCreated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("dateDeleted", new TableInfo.Column("dateDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("isPinned", new TableInfo.Column("isPinned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("isFavorite", new TableInfo.Column("isFavorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("tags", new TableInfo.Column("tags", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("amplitudes", new TableInfo.Column("amplitudes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsYournalEntries.put("attachments", new TableInfo.Column("attachments", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysYournalEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesYournalEntries = new HashSet<TableInfo.Index>(4);
        _indicesYournalEntries.add(new TableInfo.Index("index_yournal_entries_isDeleted_isPinned_dateCreated", false, Arrays.asList("isDeleted", "isPinned", "dateCreated"), Arrays.asList("ASC", "ASC", "ASC")));
        _indicesYournalEntries.add(new TableInfo.Index("index_yournal_entries_isDeleted_noteType_dateCreated", false, Arrays.asList("isDeleted", "noteType", "dateCreated"), Arrays.asList("ASC", "ASC", "ASC")));
        _indicesYournalEntries.add(new TableInfo.Index("index_yournal_entries_dateDeleted", false, Arrays.asList("dateDeleted"), Arrays.asList("ASC")));
        _indicesYournalEntries.add(new TableInfo.Index("index_yournal_entries_noteTitle", false, Arrays.asList("noteTitle"), Arrays.asList("ASC")));
        final TableInfo _infoYournalEntries = new TableInfo("yournal_entries", _columnsYournalEntries, _foreignKeysYournalEntries, _indicesYournalEntries);
        final TableInfo _existingYournalEntries = TableInfo.read(db, "yournal_entries");
        if (!_infoYournalEntries.equals(_existingYournalEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "yournal_entries(com.yournal.model.YournalEntry).\n"
                  + " Expected:\n" + _infoYournalEntries + "\n"
                  + " Found:\n" + _existingYournalEntries);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "7589460c9118f56eca1e98fc4e0326d9", "e089925610a7818c126f1f59b241c787");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "yournal_entries");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `yournal_entries`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(YournalDao.class, YournalDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public YournalDao yournalDao() {
    if (_yournalDao != null) {
      return _yournalDao;
    } else {
      synchronized(this) {
        if(_yournalDao == null) {
          _yournalDao = new YournalDao_Impl(this);
        }
        return _yournalDao;
      }
    }
  }
}
