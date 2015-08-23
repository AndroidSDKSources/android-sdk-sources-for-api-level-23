/* This file is auto-generated from HeadersFragment.java.  DO NOT MODIFY. */

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.v17.leanback.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v17.leanback.R;
import android.support.v17.leanback.widget.FocusHighlightHelper;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.SinglePresenterSelector;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnLayoutChangeListener;
import android.widget.FrameLayout;

/**
 * An internal fragment containing a list of row headers.
 */
public class HeadersSupportFragment extends BaseRowSupportFragment {

    interface OnHeaderClickedListener {
        void onHeaderClicked();
    }

    interface OnHeaderViewSelectedListener {
        void onHeaderSelected(RowHeaderPresenter.ViewHolder viewHolder, Row row);
    }

    private OnHeaderViewSelectedListener mOnHeaderViewSelectedListener;
    private OnHeaderClickedListener mOnHeaderClickedListener;
    private boolean mHeadersEnabled = true;
    private boolean mHeadersGone = false;
    private int mBackgroundColor;
    private boolean mBackgroundColorSet;

    private static final PresenterSelector sHeaderPresenter = new SinglePresenterSelector(
            new RowHeaderPresenter(R.layout.lb_header));

    public HeadersSupportFragment() {
        setPresenterSelector(sHeaderPresenter);
    }

    public void setOnHeaderClickedListener(OnHeaderClickedListener listener) {
        mOnHeaderClickedListener = listener;
    }

    public void setOnHeaderViewSelectedListener(OnHeaderViewSelectedListener listener) {
        mOnHeaderViewSelectedListener = listener;
    }

    @Override
    VerticalGridView findGridViewFromRoot(View view) {
        return (VerticalGridView) view.findViewById(R.id.browse_headers);
    }

    @Override
    void onRowSelected(RecyclerView parent, RecyclerView.ViewHolder viewHolder,
            int position, int subposition) {
        if (mOnHeaderViewSelectedListener != null) {
            if (viewHolder != null && position >= 0) {
                Row row = (Row) getAdapter().get(position);
                ItemBridgeAdapter.ViewHolder vh = (ItemBridgeAdapter.ViewHolder) viewHolder;
                mOnHeaderViewSelectedListener.onHeaderSelected(
                        (RowHeaderPresenter.ViewHolder) vh.getViewHolder(), row);
            } else {
                mOnHeaderViewSelectedListener.onHeaderSelected(null, null);
            }
        }
    }

    private final ItemBridgeAdapter.AdapterListener mAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
        @Override
        public void onCreate(ItemBridgeAdapter.ViewHolder viewHolder) {
            View headerView = viewHolder.getViewHolder().view;
            headerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnHeaderClickedListener != null) {
                        mOnHeaderClickedListener.onHeaderClicked();
                    }
                }
            });
            headerView.setFocusable(true);
            headerView.setFocusableInTouchMode(true);
            if (mWrapper != null) {
                viewHolder.itemView.addOnLayoutChangeListener(sLayoutChangeListener);
            } else {
                headerView.addOnLayoutChangeListener(sLayoutChangeListener);
            }
        }

    };

    private static OnLayoutChangeListener sLayoutChangeListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
            v.setPivotX(v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? v.getWidth() : 0);
            v.setPivotY(v.getMeasuredHeight() / 2);
        }
    };

    @Override
    int getLayoutResourceId() {
        return R.layout.lb_headers_fragment;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final VerticalGridView listView = getVerticalGridView();
        if (listView == null) {
            return;
        }
        if (getBridgeAdapter() != null) {
            FocusHighlightHelper.setupHeaderItemFocusHighlight(listView);
        }
        view.setBackgroundColor(getBackgroundColor());
        updateFadingEdgeToBrandColor(getBackgroundColor());
        updateListViewVisibility();
    }

    private void updateListViewVisibility() {
        final VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            getView().setVisibility(mHeadersGone ? View.GONE : View.VISIBLE);
            if (!mHeadersGone) {
                if (mHeadersEnabled) {
                    listView.setChildrenVisibility(View.VISIBLE);
                } else {
                    listView.setChildrenVisibility(View.INVISIBLE);
                }
            }
        }
    }

    void setHeadersEnabled(boolean enabled) {
        mHeadersEnabled = enabled;
        updateListViewVisibility();
    }

    void setHeadersGone(boolean gone) {
        mHeadersGone = gone;
        updateListViewVisibility();
    }

    static class NoOverlappingFrameLayout extends FrameLayout {

        public NoOverlappingFrameLayout(Context context) {
            super(context);
        }

        /**
         * Avoid creating hardware layer for header dock.
         */
        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    // Wrapper needed because of conflict between RecyclerView's use of alpha
    // for ADD animations, and RowHeaderPresnter's use of alpha for selected level.
    private final ItemBridgeAdapter.Wrapper mWrapper = new ItemBridgeAdapter.Wrapper() {
        @Override
        public void wrap(View wrapper, View wrapped) {
            ((FrameLayout) wrapper).addView(wrapped);
        }

        @Override
        public View createWrapper(View root) {
            return new NoOverlappingFrameLayout(root.getContext());
        }
    };
    @Override
    void updateAdapter() {
        super.updateAdapter();
        ItemBridgeAdapter adapter = getBridgeAdapter();
        if (adapter != null) {
            adapter.setAdapterListener(mAdapterListener);
            adapter.setWrapper(mWrapper);
        }
        if (adapter != null && getVerticalGridView() != null) {
            FocusHighlightHelper.setupHeaderItemFocusHighlight(getVerticalGridView());
        }
    }

    void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundColorSet = true;

        if (getView() != null) {
            getView().setBackgroundColor(mBackgroundColor);
            updateFadingEdgeToBrandColor(mBackgroundColor);
        }
    }

    private void updateFadingEdgeToBrandColor(int backgroundColor) {
        View fadingView = getView().findViewById(R.id.fade_out_edge);
        Drawable background = fadingView.getBackground();
        if (background instanceof GradientDrawable) {
            background.mutate();
            ((GradientDrawable) background).setColors(
                    new int[] {Color.TRANSPARENT, backgroundColor});
        }
    }

    int getBackgroundColor() {
        if (getActivity() == null) {
            throw new IllegalStateException("Activity must be attached");
        }

        if (mBackgroundColorSet) {
            return mBackgroundColor;
        }

        TypedValue outValue = new TypedValue();
        if (getActivity().getTheme().resolveAttribute(R.attr.defaultBrandColor, outValue, true)) {
            return getResources().getColor(outValue.resourceId);
        }
        return getResources().getColor(R.color.lb_default_brand_color);
    }

    @Override
    void onTransitionStart() {
        super.onTransitionStart();
        if (!mHeadersEnabled) {
            // When enabling headers fragment,  the RowHeaderView gets a focus but
            // isShown() is still false because its parent is INVSIBILE, accessibility
            // event is not sent.
            // Workaround is: prevent focus to a child view during transition and put
            // focus on it after transition is done.
            final VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                listView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                if (listView.hasFocus()) {
                    listView.requestFocus();
                }
            }
        }
    }

    @Override
    void onTransitionEnd() {
        if (mHeadersEnabled) {
            final VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                listView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (listView.hasFocus()) {
                    listView.requestFocus();
                }
            }
        }
        super.onTransitionEnd();
    }
}
