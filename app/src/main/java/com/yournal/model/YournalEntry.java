package com.yournal.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import java.util.List;

@Entity(
        tableName = "yournal_entries",
        indices = {
                @Index(value = {"isDeleted", "isPinned", "dateCreated"}),
                @Index(value = {"isDeleted", "noteType", "dateCreated"}),
                @Index(value = {"dateDeleted"}),
                @Index(value = {"noteTitle"})
        }
)
public class YournalEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String noteTitle;
    public String filePath;
    
    // "note", "recording", "drawing", "audionote"
    public String noteType; 
    
    public String noteContent;
    
    public long dateCreated;
    public long dateDeleted;
    
    public boolean isPinned;
    public boolean isDeleted;
    public boolean isFavorite;
    
    @TypeConverters(Converters.class)
    public List<String> tags;

    @TypeConverters(Converters.class)
    public List<Float> amplitudes;

    @TypeConverters(Converters.class)
    public List<NoteAttachment> attachments;

    // Legacy fields - temporarily kept for easier transition if needed, 
    // but mostly replaced by the above.
    // public String notename; 
    // public String type; 
    // public String tag;
    // public String note;
}
