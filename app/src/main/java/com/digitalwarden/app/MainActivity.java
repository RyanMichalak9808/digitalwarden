package com.digitalwarden.app;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            Toast.makeText(this,
                "Warning: this app is not set as device owner. Restrictions will not apply until you run 'adb shell dpm set-device-owner'.",
                Toast.LENGTH_LONG).show();
        }

        ViewPager2 pager = findViewById(R.id.pager);
        TabLayout tabs = findViewById(R.id.tabs);

        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new AppsFragment();
                    case 1: return new RestrictionsFragment();
                    case 2: return new ScheduleFragment();
                    default: return new UsageFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        });

        String[] titles = {"Apps", "Settings", "Schedule", "Usage"};
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();
    }

    /**
     * Re-checked every time this activity comes to the foreground, not just on first creation.
     * Covers: cold launch (handled by PinAuthActivity being the actual launcher), returning from
     * recents/another app, and unlocking the phone screen while this was the last-open activity.
     * If the session isn't authenticated, immediately bounce to the PIN screen instead of
     * letting any of MainActivity's content flash on screen first.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionState.isAuthenticated()) {
            startActivity(new Intent(this, PinAuthActivity.class));
            finish();
        }
    }

    /**
     * Clear the session the moment this activity leaves the foreground (home button, app
     * switcher, screen lock, etc.) so the next onResume() - whenever that happens - requires
     * the PIN again. This is deliberately aggressive: even a brief backgrounding re-locks.
     */
    @Override
    protected void onPause() {
        super.onPause();
        SessionState.clear();
    }
}
