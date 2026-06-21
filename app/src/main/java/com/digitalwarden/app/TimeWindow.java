package com.digitalwarden.app;

import java.util.HashSet;
import java.util.Set;

public class TimeWindow {
    public String id = java.util.UUID.randomUUID().toString();
    public String label = "";
    public int startHour = 22;
    public int startMinute = 0;
    public int endHour = 7;
    public int endMinute = 0;
    public Set<Integer> daysOfWeek = new HashSet<>(); // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
    public boolean enabled = true;
}
