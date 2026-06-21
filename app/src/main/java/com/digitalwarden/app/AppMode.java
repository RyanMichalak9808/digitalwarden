package com.digitalwarden.app;

/**
 * The three mutually exclusive states an app can be in, as exposed in the consolidated Apps
 * tab. This is a UI-level convenience - under the hood LockdownConfig still stores
 * blockedPackages (a Set) and timedApps (a Map) separately, since those are what
 * LockdownService/UsageTrackerService actually enforce against. AppMode is just how
 * AppsFragment decides which of those two stores a given package belongs in, and enforces the
 * rule that a package can only be in one of them at a time.
 */
public enum AppMode {
    ALLOW,
    BLOCK,
    TIME_LIMIT
}
