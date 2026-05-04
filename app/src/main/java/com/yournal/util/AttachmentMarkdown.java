package com.yournal.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.yournal.model.NoteAttachment;

import java.io.UnsupportedEncodingException;
import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AttachmentMarkdown {

    private static final String PREFIX = "attachment://";
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((attachment://[^\\)]+)\\)");
    private static final Gson GSON = new Gson();

    private AttachmentMarkdown() {
    }

    public static String toMarkdown(@NonNull NoteAttachment attachment) {
        String label = attachment.displayName != null ? attachment.displayName : "Attachment";
        return "[" + escapeLabel(label) + "](" + PREFIX + encode(attachment) + ")";
    }

    public static boolean isAttachmentLink(String destination) {
        return destination != null && destination.startsWith(PREFIX);
    }

    public static NoteAttachment fromDestination(String destination) {
        if (!isAttachmentLink(destination)) return null;
        String encoded = destination.substring(PREFIX.length());
        return decode(encoded);
    }

    public static List<NoteAttachment> extractAttachments(String markdown) {
        List<NoteAttachment> attachments = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return attachments;

        Matcher matcher = MARKDOWN_PATTERN.matcher(markdown);
        while (matcher.find()) {
            NoteAttachment attachment = fromDestination(matcher.group(2));
            if (attachment != null) {
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    public static List<PreviewSegment> splitIntoPreviewSegments(String markdown) {
        List<PreviewSegment> segments = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return segments;
        }

        Matcher matcher = MARKDOWN_PATTERN.matcher(markdown);
        int lastIndex = 0;
        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                String text = markdown.substring(lastIndex, matcher.start());
                if (!text.isEmpty()) {
                    segments.add(PreviewSegment.text(text));
                }
            }

            NoteAttachment attachment = fromDestination(matcher.group(2));
            if (attachment != null) {
                segments.add(PreviewSegment.attachment(attachment));
            } else {
                segments.add(PreviewSegment.text(matcher.group(0)));
            }
            lastIndex = matcher.end();
        }

        if (lastIndex < markdown.length()) {
            String trailing = markdown.substring(lastIndex);
            if (!trailing.isEmpty()) {
                segments.add(PreviewSegment.text(trailing));
            }
        }

        return segments;
    }

    public static String insertInlineToken(CharSequence content, int cursorStart, int cursorEnd, String token) {
        String text = content == null ? "" : content.toString();
        int start = Math.max(0, Math.min(cursorStart, text.length()));
        int end = Math.max(0, Math.min(cursorEnd, text.length()));
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        String before = text.substring(0, start);
        String after = text.substring(end);
        StringBuilder insertion = new StringBuilder();
        if (!before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1))) {
            insertion.append(' ');
        }
        insertion.append(token);
        if (!after.isEmpty() && !Character.isWhitespace(after.charAt(0))) {
            insertion.append(' ');
        }
        return before + insertion + after;
    }

    public static void openAttachment(Context context, NoteAttachment attachment) {
        if (context == null || attachment == null || attachment.uriString == null) return;

        try {
            Uri uri = toPlayableUri(attachment);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, attachment.mimeType != null ? attachment.mimeType : "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Open Attachment"));
        } catch (Exception e) {
            Toast.makeText(context, "Unable to open attachment", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    public static Uri toPlayableUri(NoteAttachment attachment) {
        if (attachment == null || attachment.uriString == null) {
            return Uri.EMPTY;
        }

        String value = attachment.uriString;
        if (value.startsWith("content://") || value.startsWith("file://") || value.startsWith("android.resource://")) {
            return Uri.parse(value);
        }

        File file = new File(value);
        if (file.exists()) {
            return Uri.fromFile(file);
        }

        return Uri.parse(value);
    }

    private static String escapeLabel(String label) {
        return label.replace("[", "\\[").replace("]", "\\]");
    }

    private static String encode(NoteAttachment attachment) {
        try {
            return URLEncoder.encode(GSON.toJson(attachment), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static NoteAttachment decode(String encoded) {
        try {
            String json = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
            return GSON.fromJson(json, NoteAttachment.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static final class PreviewSegment {
        public final String markdownText;
        public final NoteAttachment attachment;

        private PreviewSegment(String markdownText, NoteAttachment attachment) {
            this.markdownText = markdownText;
            this.attachment = attachment;
        }

        public static PreviewSegment text(String markdownText) {
            return new PreviewSegment(markdownText, null);
        }

        public static PreviewSegment attachment(NoteAttachment attachment) {
            return new PreviewSegment(null, attachment);
        }

        public boolean isAttachment() {
            return attachment != null;
        }
    }
}
