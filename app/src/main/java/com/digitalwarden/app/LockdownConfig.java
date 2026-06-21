package com.digitalwarden.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LockdownConfig {
    public Set<String> blockedPackages = new HashSet<>();
    public Set<String> activeRestrictions = new HashSet<>(); // UserManager.DISALLOW_* constant strings
    public String pinHash = "";
    public String pinSalt = "";
    public List<TimeWindow> schedules = new ArrayList<>();
    public boolean privateDnsEnforced = false;
    public String privateDnsHostname = "";
    public boolean scheduleActiveNow = false; // tracks whether a schedule window currently has apps suspended

    // package name -> daily usage quota config for apps that are allowed X minutes/day
    // then blocked from launching until the next 2am reset.
    public Map<String, TimedAppQuota> timedApps = new HashMap<>();
}
