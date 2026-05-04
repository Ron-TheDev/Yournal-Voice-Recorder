package com.yournal.model;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    @TypeConverter
    public static List<String> fromString(String value) {
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> list = new Gson().fromJson(value, listType);
        return list == null ? new ArrayList<>() : list;
    }

    @TypeConverter
    public static String fromList(List<String> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<Float> fromFloatString(String value) {
        Type listType = new TypeToken<List<Float>>() {}.getType();
        List<Float> list = new Gson().fromJson(value, listType);
        return list == null ? new ArrayList<>() : list;
    }

    @TypeConverter
    public static String fromFloatList(List<Float> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<NoteAttachment> fromAttachmentString(String value) {
        Type listType = new TypeToken<List<NoteAttachment>>() {}.getType();
        List<NoteAttachment> list = new Gson().fromJson(value, listType);
        return list == null ? new ArrayList<>() : list;
    }

    @TypeConverter
    public static String fromAttachmentList(List<NoteAttachment> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }
}
