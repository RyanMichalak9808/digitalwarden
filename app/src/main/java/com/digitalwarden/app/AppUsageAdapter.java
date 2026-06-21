package com.digitalwarden.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {

    public static class Row {
        public final ApplicationInfo info;
        public final long totalForegroundMillis;

        public Row(ApplicationInfo info, long totalForegroundMillis) {
            this.info = info;
            this.totalForegroundMillis = totalForegroundMillis;
        }
    }

    private final List<Row> rows;
    private final PackageManager pm;

    public AppUsageAdapter(List<Row> rows, PackageManager pm) {
        this.rows = rows;
        this.pm = pm;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.name.setText(pm.getApplicationLabel(row.info));
        holder.icon.setImageDrawable(pm.getApplicationIcon(row.info));
        holder.duration.setText(AppUsageStats.formatDuration(row.totalForegroundMillis));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        TextView duration;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.usageAppIcon);
            name = itemView.findViewById(R.id.usageAppName);
            duration = itemView.findViewById(R.id.usageAppDuration);
        }
    }
}
