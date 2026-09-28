package com.example;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class GenericAdapter extends RecyclerView.Adapter<GenericAdapter.ViewHolder> {

    public static class Item {
        public String id;
        public String title;
        public String subtitle;
        public String emoji;
        public String time;
        public long badge;
        public boolean showRequests;
        public int type; // 0 = Chat, 1 = Update/Request/Story, 2 = Call
        public boolean isLocked;
        public boolean hasStoryRing;
        public String extraData; // For story text or media

        public Item() {}

        public Item(String id, String title, String subtitle, String emoji, String time, long badge, boolean showRequests, int type) {
            this(id, title, subtitle, emoji, time, badge, showRequests, type, false, false, "");
        }

        public Item(String id, String title, String subtitle, String emoji, String time, long badge, boolean showRequests, int type, boolean isLocked, boolean hasStoryRing, String extraData) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.emoji = emoji;
            this.time = time;
            this.badge = badge;
            this.showRequests = showRequests;
            this.type = type;
            this.isLocked = isLocked;
            this.hasStoryRing = hasStoryRing;
            this.extraData = extraData;
        }
    }

    public interface OnItemClick {
        void onItemClick(Item item);
    }

    public interface OnItemLongClick {
        void onItemLongClick(Item item);
    }

    public interface OnAction {
        void onAccept(Item item);
        void onReject(Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private final OnItemClick itemClickListener;
    private OnItemLongClick itemLongClickListener;
    private final OnAction actionListener;

    public GenericAdapter(OnItemClick itemClickListener, OnAction actionListener) {
        this.itemClickListener = itemClickListener;
        this.actionListener = actionListener;
    }

    public void setOnItemLongClickListener(OnItemLongClick listener) {
        this.itemLongClickListener = listener;
    }

    public void setItems(List<Item> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.tvTitle.setText(item.title != null ? item.title : "");
        holder.tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");
        holder.tvEmoji.setText(item.emoji != null && !item.emoji.isEmpty() ? item.emoji : "👤");

        // Status ring styling
        if (item.hasStoryRing) {
            holder.tvEmoji.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.status_ring_bg));
        } else {
            holder.tvEmoji.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.emoji_bg));
        }

        // Lock icon
        if (holder.ivLock != null) {
            holder.ivLock.setVisibility(item.isLocked ? View.VISIBLE : View.GONE);
        }

        if (item.showRequests) {
            // Show Accept / Reject buttons
            holder.layoutTimeBadge.setVisibility(View.GONE);
            holder.layoutRequestActions.setVisibility(View.VISIBLE);

            holder.btnAccept.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onAccept(item);
            });
            holder.btnReject.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onReject(item);
            });
        } else {
            // Show Time & Badge
            holder.layoutRequestActions.setVisibility(View.GONE);
            holder.layoutTimeBadge.setVisibility(View.VISIBLE);

            if (item.time != null && !item.time.isEmpty()) {
                holder.tvTime.setVisibility(View.VISIBLE);
                holder.tvTime.setText(item.time);
            } else {
                holder.tvTime.setVisibility(View.GONE);
            }

            if (item.badge > 0) {
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText(item.badge > 99 ? "99+" : String.valueOf(item.badge));
            } else {
                holder.tvBadge.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (itemLongClickListener != null) {
                itemLongClickListener.onItemLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmoji, tvTitle, tvSubtitle, tvTime, tvBadge;
        ImageView ivLock;
        LinearLayout layoutTimeBadge, layoutRequestActions;
        MaterialButton btnAccept, btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tv_item_emoji);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            tvSubtitle = itemView.findViewById(R.id.tv_item_subtitle);
            tvTime = itemView.findViewById(R.id.tv_item_time);
            tvBadge = itemView.findViewById(R.id.tv_item_badge);
            ivLock = itemView.findViewById(R.id.iv_item_lock);
            layoutTimeBadge = itemView.findViewById(R.id.layout_time_badge);
            layoutRequestActions = itemView.findViewById(R.id.layout_request_actions);
            btnAccept = itemView.findViewById(R.id.btn_item_accept);
            btnReject = itemView.findViewById(R.id.btn_item_reject);
        }
    }
}
