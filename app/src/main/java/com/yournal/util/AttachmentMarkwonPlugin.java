package com.yournal.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.commonmark.node.Link;

import com.yournal.model.NoteAttachment;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.spans.LinkSpan;

public class AttachmentMarkwonPlugin extends AbstractMarkwonPlugin {

    @Override
    public void configureSpansFactory(@NonNull io.noties.markwon.MarkwonSpansFactory.Builder builder) {
        builder.setFactory(Link.class, (MarkwonConfiguration configuration, RenderProps props) -> {
            String destination = props.get(CoreProps.LINK_DESTINATION);
            if (AttachmentMarkdown.isAttachmentLink(destination)) {
                NoteAttachment attachment = AttachmentMarkdown.fromDestination(destination);
                if (attachment == null) {
                    return null;
                }
                return new Object[] {
                        new AttachmentChipSpan(),
                        new AttachmentClickableSpan(attachment)
                };
            }

            MarkwonTheme theme = configuration.theme();
            return new LinkSpan(theme, destination, configuration.linkResolver());
        });
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull android.text.Spanned markdown) {
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        textView.setHighlightColor(android.graphics.Color.TRANSPARENT);
    }

    private static final class AttachmentClickableSpan extends ClickableSpan {
        private final NoteAttachment attachment;

        private AttachmentClickableSpan(NoteAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public void onClick(@NonNull View widget) {
            AttachmentMarkdown.openAttachment(widget.getContext(), attachment);
        }
    }

    private static final class AttachmentChipSpan extends ReplacementSpan {
        private final float paddingHorizontal;
        private final float paddingVertical;
        private final float cornerRadius;

        private AttachmentChipSpan() {
            this.paddingHorizontal = 24f;
            this.paddingVertical = 10f;
            this.cornerRadius = 28f;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            Paint workingPaint = new Paint(paint);
            float width = workingPaint.measureText(text, start, end);
            if (fm != null) {
                paint.getFontMetricsInt(fm);
                int extra = (int) (paddingVertical * 2);
                fm.top -= extra;
                fm.ascent -= extra;
                fm.descent += extra;
                fm.bottom += extra;
            }
            return (int) Math.ceil(width + paddingHorizontal * 2);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chipPaint.setColor(ColorUtils.setAlphaComponent(paint.getColor(), 24));

            TextPaint textPaint = new TextPaint(paint);
            textPaint.setColor(paint.getColor());
            textPaint.setFakeBoldText(true);

            float textWidth = textPaint.measureText(text, start, end);
            float textHeight = textPaint.descent() - textPaint.ascent();
            float rectLeft = x;
            float rectTop = y + textPaint.ascent() - paddingVertical;
            float rectRight = x + textWidth + paddingHorizontal * 2;
            float rectBottom = y + textPaint.descent() + paddingVertical;

            RectF rect = new RectF(rectLeft, rectTop, rectRight, rectBottom);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, chipPaint);

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1f);
            borderPaint.setColor(ColorUtils.setAlphaComponent(paint.getColor(), 90));
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint);

            canvas.drawText(text, start, end, x + paddingHorizontal, y, textPaint);
        }
    }
}
