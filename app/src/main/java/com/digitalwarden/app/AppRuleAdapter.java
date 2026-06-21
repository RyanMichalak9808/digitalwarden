package com.digitalwarden.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * One row per app with a three-way mode selector (Allow / Block / Time Limit) plus a minutes
 * field that's only relevant in Time Limit mode. Block always wins if somehow both are set on
 * the underlying config (defensive only - the UI here only ever lets one mode be active at a
 * time per app), matching the rule that an always-blocked app should never accidentally also
 * count toward a quota.
 */
public class AppRuleAdapter extends RecyclerView.Adapter<AppRuleAdapter.ViewHolder> {

    public interface OnRuleChangeListener {
        void onModeChanged(String packageName, AppMode mode, int dailyLimitMinutes);
    }

    public static class RowState {
        public final ApplicationInfo info;
        public AppMode mode;
        public int dailyLimitMinutes;
        public long usedMillisToday;
        public boolean blockedForToday;

        public RowState(ApplicationInfo info, AppMode mode, int dailyLimitMinutes,
                         long usedMillisToday, boolean blockedForToday) {
            this.info = info;
            this.mode = mode;
            this.dailyLimitMinutes = dailyLimitMinutes;
            this.usedMillisToday = usedMillisToday;
            this.blockedForToday = blockedForToday;
        }
    }

    private final List<RowState> rows;
    private final PackageManager pm;
    private final OnRuleChangeListener listener;

    public AppRuleAdapter(List<RowState> rows, PackageManager pm, OnRuleChangeListener listener) {
        this.rows = rows;
        this.pm = pm;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_rule, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RowState row = rows.get(position);
        String pkg = row.info.packageName;

        holder.name.setText(pm.getApplicationLabel(row.info));
        holder.icon.setImageDrawable(pm.getApplicationIcon(row.info));

        if (holder.currentWatcher != null) {
            holder.minutesField.removeTextChangedListener(holder.currentWatcher);
        }
        holder.minutesField.setText(row.dailyLimitMinutes > 0 ? String.valueOf(row.dailyLimitMinutes) : "30");

        updateButtonStyles(holder, row.mode);
        updateMinutesVisibility(holder, row.mode);
        updateStatusText(holder, row);

        holder.allowBtn.setOnClickListener(v -> {
            row.mode = AppMode.ALLOW;
            updateButtonStyles(holder, row.mode);
            updateMinutesVisibility(holder, row.mode);
            holder.statusText.setVisibility(View.GONE);
            listener.onModeChanged(pkg, AppMode.ALLOW, row.dailyLimitMinutes);
        });

        holder.blockBtn.setOnClickListener(v -> {
            row.mode = AppMode.BLOCK;
            updateButtonStyles(holder, row.mode);
            updateMinutesVisibility(holder, row.mode);
            holder.statusText.setVisibility(View.GONE);
            listener.onModeChanged(pkg, AppMode.BLOCK, row.dailyLimitMinutes);
        });

        holder.timeLimitBtn.setOnClickListener(v -> {
            row.mode = AppMode.TIME_LIMIT;
            int minutes = parseMinutesOrDefault(holder.minutesField.getText().toString(), 30);
            row.dailyLimitMinutes = minutes;
            updateButtonStyles(holder, row.mode);
            updateMinutesVisibility(holder, row.mode);
            updateStatusText(holder, row);
            listener.onModeChanged(pkg, AppMode.TIME_LIMIT, minutes);
        });

        holder.currentWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (row.mode != AppMode.TIME_LIMIT) return;
                int minutes = parseMinutesOrDefault(s.toString(), -1);
                if (minutes > 0) {
                    row.dailyLimitMinutes = minutes;
                    listener.onModeChanged(pkg, AppMode.TIME_LIMIT, minutes);
                }
            }
        };
        holder.minutesField.addTextChangedListener(holder.currentWatcher);
    }

    private void updateButtonStyles(ViewHolder holder, AppMode mode) {
        holder.allowBtn.setSelected(mode == AppMode.ALLOW);
        holder.blockBtn.setSelected(mode == AppMode.BLOCK);
        holder.timeLimitBtn.setSelected(mode == AppMode.TIME_LIMIT);
    }

    private void updateMinutesVisibility(ViewHolder holder, AppMode mode) {
        holder.minutesField.setVisibility(mode == AppMode.TIME_LIMIT ? View.VISIBLE : View.GONE);
        holder.minutesLabel.setVisibility(mode == AppMode.TIME_LIMIT ? View.VISIBLE : View.GONE);
    }

    private void updateStatusText(ViewHolder holder, RowState row) {
        if (row.mode != AppMode.TIME_LIMIT) {
            holder.statusText.setVisibility(View.GONE);
            return;
        }
        holder.statusText.setVisibility(View.VISIBLE);
        if (row.blockedForToday) {
            holder.statusText.setText("Blocked for today (resets 2am)");
        } else {
            long usedMin = row.usedMillisToday / 60000;
            holder.statusText.setText(usedMin + " / " + row.dailyLimitMinutes + " min used today");
        }
    }

    private int parseMinutesOrDefault(String text, int fallback) {
        try {
            int val = Integer.parseInt(text.trim());
            return val > 0 ? val : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        TextView statusText;
        TextView minutesLabel;
        Button allowBtn;
        Button blockBtn;
        Button timeLimitBtn;
        EditText minutesField;
        TextWatcher currentWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ruleAppIcon);
            name = itemView.findViewById(R.id.ruleAppName);
            statusText = itemView.findViewById(R.id.ruleStatusText);
            minutesLabel = itemView.findViewById(R.id.ruleMinutesLabel);
            allowBtn = itemView.findViewById(R.id.ruleAllowBtn);
            blockBtn = itemView.findViewById(R.id.ruleBlockBtn);
            timeLimitBtn = itemView.findViewById(R.id.ruleTimeLimitBtn);
            minutesField = itemView.findViewById(R.id.ruleMinutesField);
        }
    }
}
