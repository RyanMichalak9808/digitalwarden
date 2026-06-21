package com.digitalwarden.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ScheduleListAdapter extends RecyclerView.Adapter<ScheduleListAdapter.ViewHolder> {

    public interface Listener {
        void onToggle(TimeWindow window, boolean enabled);
        void onDelete(TimeWindow window);
    }

    private final List<TimeWindow> windows;
    private final Listener listener;

    private static final String[] DAY_ABBR = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    public ScheduleListAdapter(List<TimeWindow> windows, Listener listener) {
        this.windows = windows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeWindow w = windows.get(position);

        String label = w.label.isEmpty() ? "Schedule" : w.label;
        holder.label.setText(String.format(Locale.US, "%s  %02d:%02d - %02d:%02d",
                label, w.startHour, w.startMinute, w.endHour, w.endMinute));

        StringBuilder days = new StringBuilder();
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if (w.daysOfWeek.contains(d)) {
                if (days.length() > 0) days.append(", ");
                days.append(DAY_ABBR[d]);
            }
        }
        holder.days.setText(days.length() > 0 ? days.toString() : "No days selected");

        holder.switchView.setOnCheckedChangeListener(null);
        holder.switchView.setChecked(w.enabled);
        holder.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            w.enabled = isChecked;
            listener.onToggle(w, isChecked);
        });

        holder.deleteBtn.setOnClickListener(v -> listener.onDelete(w));
    }

    @Override
    public int getItemCount() {
        return windows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView label, days;
        SwitchCompat switchView;
        ImageButton deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.windowLabel);
            days = itemView.findViewById(R.id.windowDays);
            switchView = itemView.findViewById(R.id.windowSwitch);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}
