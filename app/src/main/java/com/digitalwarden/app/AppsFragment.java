package com.digitalwarden.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated "Apps" tab: one list, one row per app, three-way mode (Allow / Block / Time
 * Limit) instead of two separate tabs and lists. Under the hood, LockdownConfig still has two
 * separate stores - blockedPackages (a Set) and timedApps (a Map) - because that's what
 * LockdownService and UsageTrackerService actually read when enforcing. This fragment's job is
 * just to keep a package in exactly one of those stores at a time, with BLOCK always winning:
 * setting an app to Block removes it from timedApps (and un-suspends/re-suspends appropriately),
 * and setting an app to Time Limit or Allow removes it from blockedPackages.
 */
public class AppsFragment extends Fragment {

    private PrefsRepository repo;
    private LockdownConfig config;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_apps, container, false);
        repo = new PrefsRepository(requireContext());
        config = repo.load();

        RecyclerView list = root.findViewById(R.id.appList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        PackageManager pm = requireContext().getPackageManager();
        List<AppRuleAdapter.RowState> rows = buildRows(pm);

        AppRuleAdapter adapter = new AppRuleAdapter(rows, pm, this::onModeChanged);
        list.setAdapter(adapter);

        return root;
    }

    private List<AppRuleAdapter.RowState> buildRows(PackageManager pm) {
        List<AppRuleAdapter.RowState> rows = new ArrayList<>();
        for (ApplicationInfo info : getUserFacingApps(pm)) {
            String pkg = info.packageName;
            AppMode mode;
            int minutes = 0;
            long usedMillis = 0;
            boolean blockedForToday = false;

            // BLOCK takes priority: if a package somehow ended up in both stores (shouldn't
            // happen given how onModeChanged enforces exclusivity, but defend against stale
            // data from before this consolidation), treat it as BLOCK for display purposes.
            if (config.blockedPackages.contains(pkg)) {
                mode = AppMode.BLOCK;
            } else if (config.timedApps.containsKey(pkg)) {
                mode = AppMode.TIME_LIMIT;
                TimedAppQuota quota = config.timedApps.get(pkg);
                minutes = quota.dailyLimitMinutes;
                usedMillis = quota.usedMillisToday;
                blockedForToday = quota.blockedForToday;
            } else {
                mode = AppMode.ALLOW;
            }

            rows.add(new AppRuleAdapter.RowState(info, mode, minutes, usedMillis, blockedForToday));
        }
        return rows;
    }

    /**
     * Applies a mode change: updates LockdownConfig's two underlying stores so they stay
     * mutually exclusive for this package, persists, and immediately calls DevicePolicyManager
     * directly (rather than waiting for the background service's next refresh) so suspending/
     * un-suspending takes effect right away when you tap a button.
     */
    private void onModeChanged(String pkg, AppMode mode, int dailyLimitMinutes) {
        boolean wasBlocked = config.blockedPackages.contains(pkg);
        TimedAppQuota existingQuota = config.timedApps.get(pkg);
        boolean wasSuspendedByQuota = existingQuota != null && existingQuota.blockedForToday;

        switch (mode) {
            case ALLOW:
                config.blockedPackages.remove(pkg);
                config.timedApps.remove(pkg);
                break;
            case BLOCK:
                // BLOCK always wins: remove any time-limit tracking for this package entirely,
                // so it can never simultaneously be "blocked for today by quota" and also in
                // the permanent blocklist - one source of truth, no ambiguity about why it's
                // blocked.
                config.timedApps.remove(pkg);
                config.blockedPackages.add(pkg);
                break;
            case TIME_LIMIT:
                config.blockedPackages.remove(pkg);
                TimedAppQuota quota = config.timedApps.get(pkg);
                if (quota == null) {
                    quota = new TimedAppQuota(pkg, dailyLimitMinutes);
                    config.timedApps.put(pkg, quota);
                } else {
                    quota.dailyLimitMinutes = dailyLimitMinutes;
                    // Raising the limit above today's already-used time should un-suspend
                    // immediately rather than waiting for the 2am reset.
                    if (quota.blockedForToday && quota.usedMillisToday < quota.dailyLimitMillis()) {
                        quota.blockedForToday = false;
                    }
                }
                break;
        }

        repo.save(config);
        applyImmediately(pkg, mode, wasBlocked, wasSuspendedByQuota);
        ServiceStarter.refresh(requireContext());
    }

    /**
     * Direct DevicePolicyManager call so suspend/un-suspend state changes the instant a button
     * is tapped, rather than only on the background service's next periodic refresh.
     */
    private void applyImmediately(String pkg, AppMode newMode, boolean wasBlocked, boolean wasSuspendedByQuota) {
        DevicePolicyManager dpm = (DevicePolicyManager) requireContext()
                .getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(requireContext(), AdminReceiver.class);
        if (dpm == null || !dpm.isDeviceOwnerApp(requireContext().getPackageName())) return;

        try {
            if (newMode == AppMode.BLOCK) {
                dpm.setPackagesSuspended(admin, new String[]{pkg}, true);
            } else {
                // Moving to ALLOW or TIME_LIMIT: the package should not be suspended right now
                // unless a time-limit quota immediately re-suspends it (handled below by
                // checking blockedForToday after the config update above).
                if (wasBlocked || wasSuspendedByQuota) {
                    dpm.setPackagesSuspended(admin, new String[]{pkg}, false);
                }
                if (newMode == AppMode.TIME_LIMIT) {
                    TimedAppQuota quota = config.timedApps.get(pkg);
                    if (quota != null && quota.blockedForToday) {
                        dpm.setPackagesSuspended(admin, new String[]{pkg}, true);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns installed apps, filtering out ones with no launcher entry (so you're not
     * offered the chance to set a rule on things like system services that wouldn't make sense
     * to). Your own app is excluded so you can't accidentally lock yourself out of the control
     * panel.
     */
    private List<ApplicationInfo> getUserFacingApps(PackageManager pm) {
        List<ApplicationInfo> result = new ArrayList<>();
        String selfPackage = requireContext().getPackageName();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (info.packageName.equals(selfPackage)) continue;
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;
            result.add(info);
        }
        result.sort((a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        return result;
    }
}
