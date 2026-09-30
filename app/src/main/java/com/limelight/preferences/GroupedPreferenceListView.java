package com.limelight.preferences;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.WrapperListAdapter;

import com.limelight.R;

/**
 * Preference list that draws each category as one rounded card: rows get a top, middle,
 * bottom or single background depending on their position within the category, and rows
 * inside a card are separated by an inset hairline drawn by the background itself.
 *
 * List dividers are always disabled because PreferenceFragment re-applies the theme divider
 * in onViewCreated() on Android 7.0+, which would draw lines across the cards.
 */
public class GroupedPreferenceListView extends ListView {
    public GroupedPreferenceListView(Context context) {
        super(context);
    }

    public GroupedPreferenceListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GroupedPreferenceListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setDivider(Drawable divider) {
        super.setDivider(null);
    }

    @Override
    public void setDividerHeight(int height) {
        super.setDividerHeight(0);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter == null || adapter instanceof GroupedCardAdapter ?
                adapter : new GroupedCardAdapter(adapter));
    }

    private static final class GroupedCardAdapter implements WrapperListAdapter {
        private final ListAdapter inner;

        GroupedCardAdapter(ListAdapter inner) {
            this.inner = inner;
        }

        private boolean isCategory(int position) {
            return position < 0 || position >= inner.getCount() ||
                    inner.getItem(position) instanceof PreferenceCategory;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = inner.getView(position, convertView, parent);
            if (view == null || isCategory(position)) {
                return view;
            }

            boolean first = isCategory(position - 1);
            boolean last = isCategory(position + 1);
            int background;
            if (first && last) {
                background = R.drawable.settings_card_single;
            }
            else if (first) {
                background = R.drawable.settings_card_top;
            }
            else if (last) {
                background = R.drawable.settings_card_bottom;
            }
            else {
                background = R.drawable.settings_card_middle;
            }

            // Keep the row's own padding; setting a background can reset it on older releases
            int start = view.getPaddingStart();
            int top = view.getPaddingTop();
            int end = view.getPaddingEnd();
            int bottom = view.getPaddingBottom();
            view.setBackgroundResource(background);
            view.setPaddingRelative(start, top, end, bottom);
            return view;
        }

        @Override
        public ListAdapter getWrappedAdapter() {
            return inner;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return inner.areAllItemsEnabled();
        }

        @Override
        public boolean isEnabled(int position) {
            return inner.isEnabled(position);
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            inner.registerDataSetObserver(observer);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            inner.unregisterDataSetObserver(observer);
        }

        @Override
        public int getCount() {
            return inner.getCount();
        }

        @Override
        public Object getItem(int position) {
            return inner.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return inner.getItemId(position);
        }

        @Override
        public boolean hasStableIds() {
            return inner.hasStableIds();
        }

        @Override
        public int getItemViewType(int position) {
            return inner.getItemViewType(position);
        }

        @Override
        public int getViewTypeCount() {
            return inner.getViewTypeCount();
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }
    }
}
