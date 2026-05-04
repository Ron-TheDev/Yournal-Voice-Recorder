package com.yournal.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConvertersTest {

    @Test
    public void stringListRoundTrips() {
        List<String> values = Arrays.asList("alpha", "beta", "gamma");

        String encoded = Converters.fromList(values);
        List<String> decoded = Converters.fromString(encoded);

        assertEquals(values, decoded);
    }

    @Test
    public void floatListRoundTrips() {
        List<Float> values = Arrays.asList(0.0f, 1.25f, -3.5f, 42.0f);

        String encoded = Converters.fromFloatList(values);
        List<Float> decoded = Converters.fromFloatString(encoded);

        assertEquals(values, decoded);
    }

    @Test
    public void attachmentListRoundTrips() {
        NoteAttachment attachment = new NoteAttachment("Example PDF", "content://example/doc", "application/pdf", NoteAttachment.TYPE_PDF);
        attachment.sourceNoteId = 42;

        String encoded = Converters.fromAttachmentList(Collections.singletonList(attachment));
        List<NoteAttachment> decoded = Converters.fromAttachmentString(encoded);

        assertEquals(1, decoded.size());
        assertEquals(attachment.displayName, decoded.get(0).displayName);
        assertEquals(attachment.uriString, decoded.get(0).uriString);
        assertEquals(attachment.mimeType, decoded.get(0).mimeType);
        assertEquals(attachment.attachmentType, decoded.get(0).attachmentType);
    }

    @Test
    public void nullAndEmptyInputsReturnEmptyLists() {
        assertTrue(Converters.fromString(null).isEmpty());
        assertTrue(Converters.fromFloatString(null).isEmpty());
        assertTrue(Converters.fromAttachmentString(null).isEmpty());
        assertEquals("[]", Converters.fromList(Collections.emptyList()));
        assertEquals("[]", Converters.fromFloatList(Collections.emptyList()));
        assertEquals("[]", Converters.fromAttachmentList(Collections.emptyList()));
    }
}
