package com.digitalwarden.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ScheduleFragment extends Fragment {

    private PrefsRepository repo;
    private LockdownConfig config;
    private ScheduleListAdapter adapter;

    private static final String[] DAY_LABELS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    private static final int[] DAY_VALUES = {
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_schedule, container, false);
        repo = new PrefsRepository(requireContext());
        config = repo.load();

        RecyclerView list = root.findViewById(R.id.scheduleList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScheduleListAdapter(config.schedules, new ScheduleListAdapter.Listener() {
            @Override
            public void onToggle(TimeWindow window, boolean enabled) {
                repo.save(config);
                if (enabled) {
                    ScheduleManager.arm(requireContext(), window);
                } else {
                    ScheduleManager.cancel(requireContext(), window);
                }
                ServiceStarter.refresh(requireContext());
            }

            @Override
            public void onDelete(TimeWindow window) {
                ScheduleManager.cancel(requireContext(), window);
                config.schedules.remove(window);
                repo.save(config);
                adapter.notifyDataSetChanged();
                ServiceStarter.refresh(requireContext());
            }
        });
        list.setAdapter(adapter);

        Button addBtn = root.findViewById(R.id.addScheduleBtn);
        addBtn.setOnClickListener(v -> showAddScheduleDialog());

        return root;
    }

    private void showAddScheduleDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_schedule, null);

        EditText labelField = dialogView.findViewById(R.id.labelField);
        LinearLayout dayRow = dialogView.findViewById(R.id.dayCheckboxRow);
        TimePicker startPicker = dialogView.findViewById(R.id.startTimePicker);
        TimePicker endPicker = dialogView.findViewById(R.id.endTimePicker);

        startPicker.setIs24HourView(true);
        endPicker.setIs24HourView(true);

        Map<Integer, CheckBox> dayBoxes = new HashMap<>();
        for (int i = 0; i < DAY_LABELS.length; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setText(DAY_LABELS[i]);
            dayRow.addView(cb);
            dayBoxes.put(DAY_VALUES[i], cb);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("New schedule")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    TimeWindow w = new TimeWindow();
                    w.label = labelField.getText().toString().trim();
                    w.startHour = startPicker.getHour();
                    w.startMinute = startPicker.getMinute();
                    w.endHour = endPicker.getHour();
                    w.endMinute = endPicker.getMinute();
                    w.enabled = true;
                    for (Map.Entry<Integer, CheckBox> entry : dayBoxes.entrySet()) {
                        if (entry.getValue().isChecked()) {
                            w.daysOfWeek.add(entry.getKey());
                        }
                    }
                    if (w.daysOfWeek.isEmpty()) {
                        Toast.makeText(requireContext(), "Select at least one day", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    config.schedules.add(w);
                    repo.save(config);
                    ScheduleManager.arm(requireContext(), w);
                    ServiceStarter.refresh(requireContext());
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
