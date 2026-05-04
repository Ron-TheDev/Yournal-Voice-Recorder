package com.yournal.model;

public class NoteAttachment {

    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_RECORDING = "recording";

    public String displayName;
    public String uriString;
    public String mimeType;
    public String attachmentType;
    public int sourceNoteId = -1;

    public NoteAttachment() {
    }

    public NoteAttachment(String displayName, String uriString, String mimeType, String attachmentType) {
        this.displayName = displayName;
        this.uriString = uriString;
        this.mimeType = mimeType;
        this.attachmentType = attachmentType;
    }

    public boolean isRecording() {
        return TYPE_RECORDING.equals(attachmentType);
    }
}
