package com.yournal.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {YournalEntry.class}, version = 7, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract YournalDao yournalDao();
    
    private static volatile AppDatabase INSTANCE;
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_yournal_entries_isDeleted_isPinned_dateCreated ON yournal_entries(isDeleted, isPinned, dateCreated)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_yournal_entries_isDeleted_noteType_dateCreated ON yournal_entries(isDeleted, noteType, dateCreated)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_yournal_entries_dateDeleted ON yournal_entries(dateDeleted)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_yournal_entries_noteTitle ON yournal_entries(noteTitle)");
        }
    };
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE yournal_entries ADD COLUMN attachments TEXT");
        }
    };
    
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "yournal_database")
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
