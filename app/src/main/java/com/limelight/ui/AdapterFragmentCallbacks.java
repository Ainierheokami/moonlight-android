package com.limelight.ui;

import android.widget.AbsListView;

import androidx.recyclerview.widget.RecyclerView;

public interface AdapterFragmentCallbacks {
    int getAdapterFragmentLayoutId();
    void receiveAbsListView(AbsListView gridView);
    void receiveRecyclerView(RecyclerView recyclerView);
}
