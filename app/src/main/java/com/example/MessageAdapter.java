package com.example;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    public static class Msg {
        public String id;
        public String text;
        public String sender;
        public long timestamp;
        public boolean outgoing;
        public String type = "text"; // "text", "image", "document", "video", "voice"
        public String mediaUrl;
        public String fileName;
        public String fileSize;
        public boolean seen = false;
        public boolean delivered = true;

        public Msg() {}

        public Msg(String id, String text, String sender, long timestamp, boolean outgoing) {
            this(id, text, sender, timestamp, outgoing, "text", null, null, null, false, true);
        }

        public Msg(String id, String text, String sender, long timestamp, boolean outgoing,
                   String type, String mediaUrl, String fileName, String fileSize, boolean seen, boolean delivered) {
            this.id = id;
            this.text = text;
            this.sender = sender;
            this.timestamp = timestamp;
            this.outgoing = outgoing;
            this.type = type != null ? type : "text";
            this.mediaUrl = mediaUrl;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.seen = seen;
            this.delivered = delivered;
        }
    }

    public interface OnMediaClickListener {
        void onMediaClick(Msg msg);
    }

    private final List<Msg> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnMediaClickListener mediaClickListener;

    public void setOnMediaClickListener(OnMediaClickListener listener) {
        this.mediaClickListener = listener;
    }

    public void setMessages(List<Msg> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(Msg msg) {
        this.messages.add(msg);
        notifyItemInserted(this.messages.size() - 1);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Msg msg = messages.get(position);

        // Bind Text
        if (msg.text != null && !msg.text.isEmpty()) {
            holder.tvText.setVisibility(View.VISIBLE);
            holder.tvText.setText(msg.text);
        } else {
            holder.tvText.setVisibility(View.GONE);
        }

        // Time
        String formattedTime = "";
        if (msg.timestamp > 0) {
            formattedTime = timeFormat.format(new Date(msg.timestamp));
        }
        holder.tvTime.setText(formattedTime);

        // Alignment & Bubble BG
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleContainer.getLayoutParams();
        if (msg.outgoing) {
            holder.layoutRoot.setGravity(Gravity.END);
            params.gravity = Gravity.END;
            holder.bubbleContainer.setLayoutParams(params);
            holder.bubbleContainer.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.msg_out_bg));
        } else {
            holder.layoutRoot.setGravity(Gravity.START);
            params.gravity = Gravity.START;
            holder.bubbleContainer.setLayoutParams(params);
            holder.bubbleContainer.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.msg_in_bg));
        }

        // Delivery & Seen Ticks (Only on outgoing messages)
        if (msg.outgoing) {
            holder.ivStatus.setVisibility(View.VISIBLE);
            if (msg.seen) {
                // Double Blue Tick (Seen)
                holder.ivStatus.setImageResource(R.drawable.ic_double_tick);
                holder.ivStatus.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.tick_blue));
            } else if (msg.delivered) {
                // Double Grey Tick (Delivered)
                holder.ivStatus.setImageResource(R.drawable.ic_double_tick);
                holder.ivStatus.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.tick_grey));
            } else {
                // Single Grey Tick (Sent)
                holder.ivStatus.setImageResource(R.drawable.ic_single_tick);
                holder.ivStatus.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.tick_grey));
            }
        } else {
            holder.ivStatus.setVisibility(View.GONE);
        }

        // Reset visibility for media containers
        holder.cardImage.setVisibility(View.GONE);
        holder.cardVideo.setVisibility(View.GONE);
        holder.layoutDoc.setVisibility(View.GONE);
        holder.layoutVoice.setVisibility(View.GONE);

        // Handle Media Types
        if ("image".equalsIgnoreCase(msg.type)) {
            holder.cardImage.setVisibility(View.VISIBLE);
            if (msg.mediaUrl != null && msg.mediaUrl.startsWith("data:image")) {
                try {
                    String base64Data = msg.mediaUrl.substring(msg.mediaUrl.indexOf(",") + 1);
                    byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    holder.ivImage.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.ivImage.setImageResource(R.drawable.ic_camera);
                }
            } else {
                holder.ivImage.setImageResource(R.drawable.ic_camera);
            }
            holder.cardImage.setOnClickListener(v -> {
                if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
            });
        } else if ("video".equalsIgnoreCase(msg.type)) {
            holder.cardVideo.setVisibility(View.VISIBLE);
            holder.cardVideo.setOnClickListener(v -> {
                if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
            });
            holder.btnPlayVideo.setOnClickListener(v -> {
                if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
            });
        } else if ("document".equalsIgnoreCase(msg.type)) {
            holder.layoutDoc.setVisibility(View.VISIBLE);
            holder.tvDocName.setText(msg.fileName != null ? msg.fileName : "Attachment.pdf");
            holder.tvDocSize.setText(msg.fileSize != null ? msg.fileSize : "1.2 MB");
            holder.layoutDoc.setOnClickListener(v -> {
                if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
            });
        } else if ("voice".equalsIgnoreCase(msg.type)) {
            holder.layoutVoice.setVisibility(View.VISIBLE);
            holder.tvVoiceDuration.setText(msg.fileSize != null ? msg.fileSize : "0:12");
            holder.btnPlayVoice.setOnClickListener(v -> {
                if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutRoot;
        LinearLayout bubbleContainer;
        TextView tvText;
        TextView tvTime;
        ImageView ivStatus;

        // Media elements
        CardView cardImage;
        ImageView ivImage;

        CardView cardVideo;
        ImageView ivVideoThumb;
        ImageButton btnPlayVideo;

        LinearLayout layoutDoc;
        TextView tvDocName;
        TextView tvDocSize;

        LinearLayout layoutVoice;
        ImageButton btnPlayVoice;
        ProgressBar pbVoiceProgress;
        TextView tvVoiceDuration;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutRoot = itemView.findViewById(R.id.layout_message_root);
            bubbleContainer = itemView.findViewById(R.id.bubble_container);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            ivStatus = itemView.findViewById(R.id.iv_message_status);

            cardImage = itemView.findViewById(R.id.card_message_image);
            ivImage = itemView.findViewById(R.id.iv_message_image);

            cardVideo = itemView.findViewById(R.id.card_message_video);
            ivVideoThumb = itemView.findViewById(R.id.iv_message_video_thumb);
            btnPlayVideo = itemView.findViewById(R.id.btn_play_video);

            layoutDoc = itemView.findViewById(R.id.layout_message_doc);
            tvDocName = itemView.findViewById(R.id.tv_doc_name);
            tvDocSize = itemView.findViewById(R.id.tv_doc_size);

            layoutVoice = itemView.findViewById(R.id.layout_message_voice);
            btnPlayVoice = itemView.findViewById(R.id.btn_play_voice);
            pbVoiceProgress = itemView.findViewById(R.id.pb_voice_progress);
            tvVoiceDuration = itemView.findViewById(R.id.tv_voice_duration);
        }
    }
}
