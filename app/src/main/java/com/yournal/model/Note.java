package com.yournal.model;

public class Note {
    public int id;
    public String title;
    public String content;
    public String date;
    public boolean isFavorite;

    public Note(int id, String title, String content, String date, boolean isFavorite) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.date = date;
        this.isFavorite = isFavorite;
    }
}
