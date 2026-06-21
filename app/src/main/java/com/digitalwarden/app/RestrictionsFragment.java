package com.digitalwarden.app;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import java.util.LinkedHashMap;
import java.util.Map;

public class RestrictionsFragment extends Fragment {

    private static final Map<String, String> RESTRICTIONS = new LinkedHashMap<>();
    static {
        RESTRICTIONS.put(UserManager.DISALLOW_CONFIG_VPN, "Block VPN configuration changes");
        RESTRICTIONS.put(UserManager.DISALLOW_DEBUGGING_FEATURES, "Block USB debugging / ADB");
        RESTRICTIONS.put(UserManager.DISALLOW_FACTORY_RESET, "Block factory reset");
        RESTRICTIONS.put(UserManager.DISALLOW_SAFE_BOOT, "Block safe boot");
        RESTRICTIONS.put(UserManager.DISALLOW_UNINSTALL_APPS, "Block app uninstall");
        RESTRICTIONS.put(UserManager.DISALLOW_APPS_CONTROL, "Block app force-stop / clear data");
        RESTRICTIONS.put(UserManager.DISALLOW_CONFIG_WIFI, "Block Wi-Fi configuration changes");
        RESTRICTIONS.put(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "Block install from unknown sources");
    }

    private PrefsRepository repo;
    private LockdownConfig config;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_restrictions, container, false);
        repo = new PrefsRepository(requireContext());
        config = repo.load();

        buildRestrictionSwitches(root);
        bindDnsControls(root);
        bindResetPin(root);
        bindEscapeHatch(root);

        return root;
    }

    private void buildRestrictionSwitches(View root) {
        LinearLayout container = root.findViewById(R.id.restrictionList);
        for (Map.Entry<String, String> entry : RESTRICTIONS.entrySet()) {
            SwitchCompat sw = new SwitchCompat(requireContext());
            sw.setText(entry.getValue());
            sw.setPadding(0, 12, 0, 12);
            sw.setChecked(config.activeRestrictions.contains(entry.getKey()));
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) config.activeRestrictions.add(entry.getKey());
                else config.activeRestrictions.remove(entry.getKey());
                repo.save(config);
                ServiceStarter.refresh(requireContext());
                if (isChecked) {
                    removeRestrictionDirectly(entry.getKey(), false);
                } else {
                    removeRestrictionDirectly(entry.getKey(), true);
                }
            });
            container.addView(sw);
        }
    }

    /**
     * Restrictions persist at the OS level once added; clearing the toggle in the UI needs to
     * actually call clearUserRestriction, not just stop re-applying it next refresh.
     */
    private void removeRestrictionDirectly(String restriction, boolean remove) {
        if (!remove) return;
        DevicePolicyManager dpm = (DevicePolicyManager) requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(requireContext(), AdminReceiver.class);
        try {
            if (dpm.isDeviceOwnerApp(requireContext().getPackageName())) {
                dpm.clearUserRestriction(admin, restriction);
            }
        } catch (Exception ignored) {
        }
    }

    private void bindDnsControls(View root) {
        SwitchCompat dnsSwitch = root.findViewById(R.id.dnsEnforceSwitch);
        EditText hostnameField = root.findViewById(R.id.dnsHostnameField);
        Button saveBtn = root.findViewById(R.id.saveDnsBtn);

        dnsSwitch.setChecked(config.privateDnsEnforced);
        hostnameField.setText(config.privateDnsHostname);

        saveBtn.setOnClickListener(v -> {
            String hostname = hostnameField.getText().toString().trim();
            boolean enforce = dnsSwitch.isChecked();

            if (enforce && hostname.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a DNS hostname first", Toast.LENGTH_SHORT).show();
                return;
            }

            config.privateDnsEnforced = enforce;
            config.privateDnsHostname = hostname;
            repo.save(config);
            ServiceStarter.refresh(requireContext());

            // Give immediate feedback rather than waiting for the service to pick it up next
            // cycle - confirm the DPM call actually succeeds right now, on this tap.
            String result = applyDnsNow(enforce, hostname);
            Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Calls setGlobalPrivateDns() / clears the lock immediately when Save is tapped, instead of
     * only relying on the background service's next refresh. Returns a short status string for
     * the toast so you know right away whether it actually took effect.
     */
    private String applyDnsNow(boolean enforce, String hostname) {
        DevicePolicyManager dpm = (DevicePolicyManager) requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(requireContext(), AdminReceiver.class);

        if (dpm == null || !dpm.isDeviceOwnerApp(requireContext().getPackageName())) {
            return "Not device owner - DNS setting saved but not enforced";
        }

        try {
            if (enforce) {
                dpm.setGlobalPrivateDns(admin,
                        DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, hostname);
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                return "DNS set to " + hostname + " and locked";
            } else {
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                return "DNS enforcement turned off";
            }
        } catch (Exception e) {
            return "Failed to apply DNS setting: " + e.getMessage();
        }
    }

    /**
     * "Change PIN" - only reachable from inside the already-unlocked app (you have to pass
     * MainActivity's PIN gate to get to this tab at all). Launches PinAuthActivity in reset
     * mode, which itself re-verifies the CURRENT PIN before letting you set a new one - so
     * someone who picks up an already-unlocked phone still can't silently take over the PIN
     * without knowing the existing one.
     */
    private void bindResetPin(View root) {
        Button resetBtn = root.findViewById(R.id.resetPinBtn);
        resetBtn.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PinAuthActivity.class);
            intent.putExtra(PinAuthActivity.EXTRA_MODE, PinAuthActivity.MODE_RESET);
            startActivity(intent);
        });
    }

    private void bindEscapeHatch(View root) {
        Button removeAllBtn = root.findViewById(R.id.removeAllBtn);
        removeAllBtn.setOnClickListener(v -> showPinConfirmDialog());
    }

    private void showPinConfirmDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_pin, null);
        EditText pinField = dialogView.findViewById(R.id.confirmPinField);

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String entered = pinField.getText().toString();
                    if (PinUtil.verify(entered, config.pinSalt, config.pinHash)) {
                        removeAllRestrictions();
                    } else {
                        Toast.makeText(requireContext(), "Incorrect PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeAllRestrictions() {
        DevicePolicyManager dpm = (DevicePolicyManager) requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(requireContext(), AdminReceiver.class);

        try {
            if (dpm.isDeviceOwnerApp(requireContext().getPackageName())) {
                for (String restriction : config.activeRestrictions) {
                    dpm.clearUserRestriction(admin, restriction);
                }
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                dpm.setPackagesSuspended(admin, config.blockedPackages.toArray(new String[0]), false);
                dpm.setPackagesSuspended(admin, config.timedApps.keySet().toArray(new String[0]), false);
            }
        } catch (Exception ignored) {
        }

        config.activeRestrictions.clear();
        config.blockedPackages.clear();
        config.privateDnsEnforced = false;
        config.schedules.clear();
        config.timedApps.clear();
        repo.save(config);

        Toast.makeText(requireContext(), "All restrictions removed", Toast.LENGTH_LONG).show();
        requireActivity().recreate();
    }
}
