package com.yournal.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.yournal.model.YournalEntry;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class ShareUtils {

    public static void shareNote(Context context, YournalEntry note) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, note.noteTitle);
        intent.putExtra(Intent.EXTRA_TEXT, note.noteTitle + "\n\n" + note.noteContent);
        context.startActivity(Intent.createChooser(intent, "Share Note"));
    }

    public static void shareRecording(Context context, YournalEntry note) {
        if (note.filePath == null) {
            Toast.makeText(context, "No recording found to share", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse(note.filePath);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Share Recording"));
    }

    public static void exportEntry(Context context, YournalEntry note, Uri destinationUri) {
        try (OutputStream os = context.getContentResolver().openOutputStream(destinationUri)) {
            if ("note".equals(note.noteType)) {
                String content = note.noteTitle + "\n\n" + (note.noteContent != null ? note.noteContent : "");
                os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if (note.filePath != null) {
                try (InputStream is = context.getContentResolver().openInputStream(Uri.parse(note.filePath))) {
                    if (is == null) {
                        Toast.makeText(context, "Source file not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
            } else {
                Toast.makeText(context, "No content found to export", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Export failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
