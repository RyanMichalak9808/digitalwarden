package com.digitalwarden.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Usage" tab: shows every app's screen time today (since the most recent 2am boundary),
 * sorted by most used first. Pulls straight from Android's own UsageStatsManager - the same
 * data source Digital Wellbeing uses, just surfaced directly since GrapheneOS doesn't ship
 * Digital Wellbeing.
 */
public class UsageFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_usage, container, false);

        RecyclerView list = root.findViewById(R.id.usageList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        TextView emptyText = root.findViewById(R.id.usageEmptyText);

        PackageManager pm = requireContext().getPackageManager();
        Map<String, Long> usageMap = AppUsageStats.queryTodayAsMap(requireContext());

        List<AppUsageAdapter.Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(entry.getKey(), 0);
                if (pm.getLaunchIntentForPackage(entry.getKey()) == null) continue; // skip background/system services
                if (entry.getKey().equals(requireContext().getPackageName())) continue;
                rows.add(new AppUsageAdapter.Row(info, entry.getValue()));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App was uninstalled since the usage was recorded; skip it.
            }
        }
        rows.sort((a, b) -> Long.compare(b.totalForegroundMillis, a.totalForegroundMillis));

        if (rows.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No usage recorded yet today, or usage access isn't available. "
                    + "If this stays empty, check Settings > Apps > Lockdown > Usage access.");
        } else {
            emptyText.setVisibility(View.GONE);
        }

        list.setAdapter(new AppUsageAdapter(rows, pm));
        return root;
    }
}
