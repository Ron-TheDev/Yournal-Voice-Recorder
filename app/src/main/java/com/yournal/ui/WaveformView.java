package com.yournal.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WaveformView extends View {
    private Paint paint;
    private List<Float> amplitudes = new ArrayList<>();
    private static final int MAX_AMPLITUDES = 100;
    private float barWidth = 4f;
    private float space = 4f;
    private boolean isStatic = false;
    private float playbackProgress = -1f; // 0.0 to 1.0

    public interface OnSeekListener {
        void onSeek(float progress);
    }
    private OnSeekListener seekListener;

    public void setOnSeekListener(OnSeekListener listener) {
        this.seekListener = listener;
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(barWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void addAmplitude(float amplitude) {
        isStatic = false;
        amplitudes.add(amplitude);
        if (amplitudes.size() > MAX_AMPLITUDES) {
            amplitudes.remove(0);
        }
        invalidate();
    }

    public void setAmplitudes(List<Float> amps) {
        isStatic = true;
        this.amplitudes = new ArrayList<>(amps);
        invalidate();
    }

    public void setPlaybackProgress(float progress) {
        this.playbackProgress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (amplitudes.isEmpty()) return;

        float midY = getHeight() / 2f;
        
        if (isStatic) {
            float totalWidth = getWidth();
            int count = amplitudes.size();
            if (count == 0) return;
            
            float step = totalWidth / count;
            for (int i = 0; i < count; i++) {
                float h = amplitudes.get(i) * getHeight() * 0.8f;
                if (h < 4) h = 4;
                
                float x = i * step;
                
                // Draw played part differently
                if (playbackProgress >= 0 && (float)i/count <= playbackProgress) {
                    paint.setAlpha(255);
                } else if (playbackProgress >= 0) {
                    paint.setAlpha(100);
                }
                
                canvas.drawLine(x, midY - h / 2f, x, midY + h / 2f, paint);
            }
            paint.setAlpha(255);
        } else {
            float currentX = getWidth();
            for (int i = amplitudes.size() - 1; i >= 0; i--) {
                float h = amplitudes.get(i) * getHeight() * 0.8f;
                if (h < 4) h = 4; // Minimum visible bar

                canvas.drawLine(currentX, midY - h / 2f, currentX, midY + h / 2f, paint);
                currentX -= (barWidth + space);
                
                if (currentX < 0) break;
            }
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (!isStatic || seekListener == null) return super.onTouchEvent(event);
        
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN || event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            float progress = event.getX() / getWidth();
            progress = Math.max(0, Math.min(1, progress));
            seekListener.onSeek(progress);
            return true;
        }
        return super.onTouchEvent(event);
    }
}
