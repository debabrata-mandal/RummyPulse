package com.example.rummypulse.ui.dashboard;

import com.example.rummypulse.R;

/** Reporting periods offered by the dashboard performance hero. */
public enum StatsPeriod {
    ALL_TIME(R.string.dashboard_period_all_time, R.string.dashboard_net_label_all_time),
    THIS_MONTH(R.string.dashboard_period_this_month, R.string.dashboard_net_label_this_month),
    LAST_MONTH(R.string.dashboard_period_last_month, R.string.dashboard_net_label_last_month),
    THIS_WEEK(R.string.dashboard_period_this_week, R.string.dashboard_net_label_this_week);

    private final int tabLabelRes;
    private final int netLabelRes;

    StatsPeriod(int tabLabelRes, int netLabelRes) {
        this.tabLabelRes = tabLabelRes;
        this.netLabelRes = netLabelRes;
    }

    public int getTabLabelRes() {
        return tabLabelRes;
    }

    public int getNetLabelRes() {
        return netLabelRes;
    }
}
