package com.yournal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.yournal.databinding.ItemNoteBinding;
import com.yournal.databinding.ItemRecordingBinding;
import com.yournal.model.YournalEntry;
import com.yournal.util.MarkdownRendererFactory;
import com.yournal.util.MotionConfig;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.noties.markwon.Markwon;

public class NoteAdapter extends ListAdapter<YournalEntry, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_NOTE = 0;
    private static final int VIEW_TYPE_RECORDING = 1;

    private final Set<Integer> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;
    private int accentColor = 0;
    private Markwon markwon;

    public interface OnNoteClickListener {
        void onNoteClick(YournalEntry note, View view);
    }

    public interface OnNoteLongClickListener {
        void onNoteLongClick(YournalEntry note, View view);
    }

    public interface OnOverflowClickListener {
        void onOverflowClick(YournalEntry note, View view);
    }

    public interface OnTagsClickListener {
        void onTagsClick(YournalEntry note, View view);
    }

    private final OnNoteClickListener noteClickListener;
    private final OnNoteLongClickListener noteLongClickListener;
    private final OnOverflowClickListener noteOverflowListener;
    private OnTagsClickListener tagsClickListener;

    public NoteAdapter(OnNoteClickListener listener, OnNoteLongClickListener longListener, OnOverflowClickListener overflowListener) {
        super(DIFF_CALLBACK);
        this.noteClickListener = listener;
        this.noteLongClickListener = longListener;
        this.noteOverflowListener = overflowListener;
    }

    public void setOnTagsClickListener(OnTagsClickListener listener) {
        this.tagsClickListener = listener;
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(int noteId) {
        if (selectedIds.contains(noteId)) {
            selectedIds.remove(noteId);
        } else {
            selectedIds.add(noteId);
        }
        notifyDataSetChanged();
    }

    public Set<Integer> getSelectedIds() {
        return selectedIds;
    }

    @Override
    public int getItemViewType(int position) {
        YournalEntry item = getItem(position);
        return "recording".equals(item.noteType) ? VIEW_TYPE_RECORDING : VIEW_TYPE_NOTE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_RECORDING) {
            ItemRecordingBinding binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new RecordingViewHolder(binding);
        }

        ItemNoteBinding binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (markwon == null) {
            markwon = MarkdownRendererFactory.create(parent.getContext());
        }
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        YournalEntry note = getItem(position);
        if (holder instanceof RecordingViewHolder) {
            bindRecording((RecordingViewHolder) holder, note);
        } else if (holder instanceof NoteViewHolder) {
            bindNote((NoteViewHolder) holder, note);
        }
    }

    private void bindNote(NoteViewHolder holder, YournalEntry note) {
        View root = holder.binding.getRoot();
        ViewCompat.setTransitionName(root, MotionConfig.noteTransitionName(note.id));
        holder.binding.tvTitle.setText(note.noteTitle);
        holder.binding.tvTypeLabel.setText(formatTypeLabel(note.noteType));

        String dateStr = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new java.util.Date(note.dateCreated));
        holder.binding.tvDate.setText(dateStr);
        holder.binding.tvDate.setVisibility(View.VISIBLE);

        markwon.setMarkdown(holder.binding.tvContent, note.noteContent != null ? note.noteContent : "");

        bindTags(holder.binding.tvTags, note.tags);
        holder.binding.ivFavorite.setVisibility(note.isFavorite ? View.VISIBLE : View.GONE);
        holder.binding.tvTags.setOnClickListener(v -> {
            if (tagsClickListener != null) tagsClickListener.onTagsClick(note, v);
        });

        if (accentColor != 0) {
            holder.binding.tvTags.setTextColor(accentColor);
        }

        bindDeleteCountdown(holder.binding.tvDeleteCountdown, note);
        bindSelection(holder.binding.cbSelect, holder.binding.btnOverflow, note.id);

        holder.binding.btnOverflow.setOnClickListener(v -> {
            if (noteOverflowListener != null) noteOverflowListener.onOverflowClick(note, v);
        });

        bindRootInteractions(root, note);
    }

    private void bindRecording(RecordingViewHolder holder, YournalEntry note) {
        View root = holder.binding.getRoot();
        ViewCompat.setTransitionName(root, MotionConfig.recordingTransitionName(note.id));
        holder.binding.tvTitle.setText(note.noteTitle);
        holder.binding.tvTypeLabel.setText(formatTypeLabel(note.noteType));

        String dateStr = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new java.util.Date(note.dateCreated));
        holder.binding.tvDate.setText(dateStr);
        holder.binding.tvDate.setVisibility(View.VISIBLE);

        bindTags(holder.binding.tvTags, note.tags);
        holder.binding.ivFavorite.setVisibility(note.isFavorite ? View.VISIBLE : View.GONE);
        holder.binding.ivRecordingOverlay.setVisibility(View.VISIBLE);
        holder.binding.ivRecordingThumbnail.setImageResource(android.R.drawable.ic_btn_speak_now);
        holder.binding.tvTags.setOnClickListener(v -> {
            if (tagsClickListener != null) tagsClickListener.onTagsClick(note, v);
        });

        if (accentColor != 0) {
            holder.binding.tvTags.setTextColor(accentColor);
        }

        bindDeleteCountdown(holder.binding.tvDeleteCountdown, note);
        bindSelection(holder.binding.cbSelect, holder.binding.btnOverflow, note.id);

        holder.binding.btnOverflow.setOnClickListener(v -> {
            if (noteOverflowListener != null) noteOverflowListener.onOverflowClick(note, v);
        });

        bindRootInteractions(root, note);
    }

    private void bindSelection(android.widget.CheckBox selectBox, View overflowButton, int noteId) {
        selectBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        selectBox.setChecked(selectedIds.contains(noteId));
        overflowButton.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
    }

    private void bindRootInteractions(View root, YournalEntry note) {
        root.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(note.id);
            } else if (noteClickListener != null) {
                noteClickListener.onNoteClick(note, v);
            }
        });

        root.setOnLongClickListener(v -> {
            if (noteLongClickListener != null) {
                noteLongClickListener.onNoteLongClick(note, v);
                return true;
            }
            return false;
        });
    }

    private void bindTags(android.widget.TextView tvTags, java.util.List<String> tags) {
        if (tags != null && !tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String tag : tags) {
                sb.append("#").append(tag).append(" ");
            }
            tvTags.setText(sb.toString().trim());
            tvTags.setVisibility(View.VISIBLE);
            tvTags.setClickable(true);
            tvTags.setFocusable(true);
        } else {
            tvTags.setVisibility(View.GONE);
            tvTags.setOnClickListener(null);
            tvTags.setClickable(false);
            tvTags.setFocusable(false);
        }
    }

    private void bindDeleteCountdown(android.widget.TextView countdownView, YournalEntry note) {
        if (note.isDeleted) {
            long diff = System.currentTimeMillis() - note.dateDeleted;
            long daysPassed = diff / (1000 * 60 * 60 * 24);
            long daysLeft = Math.max(0, 30 - daysPassed);
            countdownView.setText(daysLeft + " days left");
            countdownView.setVisibility(View.VISIBLE);
        } else {
            countdownView.setVisibility(View.GONE);
        }
    }

    private String formatTypeLabel(String type) {
        if (type == null || type.trim().isEmpty()) return "Note";
        if ("audionote".equals(type)) return "Audio Note";
        if ("recording".equals(type)) return "Recording";
        if ("drawing".equals(type)) return "Drawing";
        if ("note".equals(type)) return "Note";
        String normalized = type.replace('_', ' ').trim();
        if (normalized.isEmpty()) return "Note";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static final DiffUtil.ItemCallback<YournalEntry> DIFF_CALLBACK = new DiffUtil.ItemCallback<YournalEntry>() {
        @Override
        public boolean areItemsTheSame(@NonNull YournalEntry oldItem, @NonNull YournalEntry newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull YournalEntry oldItem, @NonNull YournalEntry newItem) {
            boolean tagsSame = Objects.equals(oldItem.tags, newItem.tags);
            boolean amplitudesSame = Objects.equals(oldItem.amplitudes, newItem.amplitudes);
            boolean attachmentsSame = Objects.equals(oldItem.attachments, newItem.attachments);
            return tagsSame
                    && amplitudesSame
                    && attachmentsSame
                    && Objects.equals(oldItem.noteType, newItem.noteType)
                    && Objects.equals(oldItem.noteTitle, newItem.noteTitle)
                    && Objects.equals(oldItem.noteContent, newItem.noteContent)
                    && Objects.equals(oldItem.filePath, newItem.filePath)
                    && oldItem.isDeleted == newItem.isDeleted
                    && oldItem.isFavorite == newItem.isFavorite
                    && oldItem.isPinned == newItem.isPinned
                    && oldItem.dateDeleted == newItem.dateDeleted
                    && oldItem.dateCreated == newItem.dateCreated;
        }
    };

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final ItemNoteBinding binding;

        NoteViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        final ItemRecordingBinding binding;

        RecordingViewHolder(ItemRecordingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
