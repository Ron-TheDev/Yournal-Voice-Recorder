package com.yournal.util;

import android.content.Context;

import androidx.annotation.NonNull;

import io.noties.markwon.Markwon;

public final class MarkdownRendererFactory {

    private MarkdownRendererFactory() {
    }

    @NonNull
    public static Markwon create(@NonNull Context context) {
        return Markwon.builder(context)
                .usePlugin(new AttachmentMarkwonPlugin())
                .build();
    }
}
