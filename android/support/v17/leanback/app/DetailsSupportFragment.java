/* This file is auto-generated from DetailsFragment.java.  DO NOT MODIFY. */

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

import android.support.v17.leanback.R;
import android.support.v17.leanback.widget.BrowseFrameLayout;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ItemAlignmentFacet;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.TitleHelper;
import android.support.v17.leanback.widget.TitleView;
import android.support.v17.leanback.widget.VerticalGridView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment for creating Leanback details screens.
 *
 * <p>
 * A DetailsSupportFragment renders the elements of its {@link ObjectAdapter} as a set
 * of rows in a vertical list. The elements in this adapter must be subclasses
 * of {@link Row}, the Adapter's {@link PresenterSelector} must maintains subclasses
 * of {@link RowPresenter}.
 * </p>
 *
 * When {@link FullWidthDetailsOverviewRowPresenter} is found in adapter,  DetailsSupportFragment will
 * setup default behavior of the DetailsOverviewRow:
 * <li>
 * The alignment of FullWidthDetailsOverviewRowPresenter is setup in
 * {@link #setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter)}.
 * </li>
 * <li>
 * The view status switching of FullWidthDetailsOverviewRowPresenter is done in
 * {@link #onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter,
 * FullWidthDetailsOverviewRowPresenter.ViewHolder, int, int, int)}.
 * </li>
 *
 * <p>
 * The recommended activity themes to use with a DetailsSupportFragment are
 * <li>
 * {@link android.support.v17.leanback.R.style#Theme_Leanback_Details} with activity
 * shared element transition for {@link FullWidthDetailsOverviewRowPresenter}.
 * </li>
 * <li>
 * {@link android.support.v17.leanback.R.style#Theme_Leanback_Details_NoSharedElementTransition}
 * if shared element transition is not needed, for example if first row is not rendered by
 * {@link FullWidthDetailsOverviewRowPresenter}.
 * </li>
 * </p>
 */
public class DetailsSupportFragment extends BaseSupportFragment {
    private static final String TAG = "DetailsSupportFragment";
    private static boolean DEBUG = false;

    private class SetSelectionRunnable implements Runnable {
        int mPosition;
        boolean mSmooth = true;

        @Override
        public void run() {
            mRowsSupportFragment.setSelectedPosition(mPosition, mSmooth);
        }
    }

    private RowsSupportFragment mRowsSupportFragment;

    private ObjectAdapter mAdapter;
    private int mContainerListAlignTop;
    private OnItemViewSelectedListener mExternalOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;

    private Object mSceneAfterEntranceTransition;

    private final SetSelectionRunnable mSetSelectionRunnable = new SetSelectionRunnable();

    private final OnItemViewSelectedListener mOnItemViewSelectedListener =
            new OnItemViewSelectedListener() {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            int position = mRowsSupportFragment.getVerticalGridView().getSelectedPosition();
            int subposition = mRowsSupportFragment.getVerticalGridView().getSelectedSubPosition();
            if (DEBUG) Log.v(TAG, "row selected position " + position
                    + " subposition " + subposition);
            onRowSelected(position, subposition);
            if (mExternalOnItemViewSelectedListener != null) {
                mExternalOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                        rowViewHolder, row);
            }
        }
    };

    /**
     * Sets the list of rows for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        Presenter[] presenters = adapter.getPresenterSelector().getPresenters();
        if (presenters != null) {
            for (int i = 0; i < presenters.length; i++) {
                setupPresenter(presenters[i]);
            }
        } else {
            Log.e(TAG, "PresenterSelector.getPresenters() not implemented");
        }
        if (mRowsSupportFragment != null) {
            mRowsSupportFragment.setAdapter(adapter);
        }
    }

    /**
     * Returns the list of rows.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mExternalOnItemViewSelectedListener = listener;
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        if (mOnItemViewClickedListener != listener) {
            mOnItemViewClickedListener = listener;
            if (mRowsSupportFragment != null) {
                mRowsSupportFragment.setOnItemViewClickedListener(listener);
            }
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContainerListAlignTop =
            getResources().getDimensionPixelSize(R.dimen.lb_details_rows_align_top);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.lb_details_fragment, container, false);
        ViewGroup fragment_root = (ViewGroup) view.findViewById(R.id.details_fragment_root);
        View titleView = inflateTitle(inflater, fragment_root, savedInstanceState);
        if (titleView != null) {
            fragment_root.addView(titleView);
        }
        mRowsSupportFragment = (RowsSupportFragment) getChildFragmentManager().findFragmentById(
                R.id.details_rows_dock);
        if (mRowsSupportFragment == null) {
            mRowsSupportFragment = new RowsSupportFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.details_rows_dock, mRowsSupportFragment).commit();
        }
        mRowsSupportFragment.setAdapter(mAdapter);
        mRowsSupportFragment.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        mRowsSupportFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);

        if (titleView != null) {
            View titleGroup = titleView.findViewById(R.id.browse_title_group);
            if (titleGroup instanceof TitleView) {
                setTitleView((TitleView) titleGroup);
            } else {
                setTitleView(null);
            }
        }

        mSceneAfterEntranceTransition = sTransitionHelper.createScene(
                (ViewGroup) view, new Runnable() {
            @Override
            public void run() {
                mRowsSupportFragment.setEntranceTransitionState(true);
            }
        });
        return view;
    }

    /**
     * Called by {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)} to inflate
     * TitleView.  Default implementation uses layout file lb_browse_title.
     * Subclass may override and use its own layout or return null if no title is needed.
     */
    protected View inflateTitle(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.lb_browse_title, parent, false);
    }

    void setVerticalGridViewLayout(VerticalGridView listview) {
        // align the top edge of item to a fixed position
        listview.setItemAlignmentOffset(-mContainerListAlignTop);
        listview.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignmentOffset(0);
        listview.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
    }

    /**
     * Called to setup each Presenter of Adapter passed in {@link #setAdapter(ObjectAdapter)}.  Note
     * that setup should only change the Presenter behavior that is meaningful in DetailsSupportFragment.  For
     * example how a row is aligned in details Fragment.   The default implementation invokes
     * {@link #setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter)}
     * 
     */
    protected void setupPresenter(Presenter rowPresenter) {
        if (rowPresenter instanceof FullWidthDetailsOverviewRowPresenter) {
            setupDetailsOverviewRowPresenter((FullWidthDetailsOverviewRowPresenter) rowPresenter);
        }
    }

    /**
     * Called to setup {@link FullWidthDetailsOverviewRowPresenter}.  The default implementation
     * adds two aligment positions({@link ItemAlignmentFacet}) for ViewHolder of
     * FullWidthDetailsOverviewRowPresenter to align in fragment.
     */
    protected void setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter presenter) {
        ItemAlignmentFacet facet = new ItemAlignmentFacet();
        // by default align details_frame to half window height
        ItemAlignmentFacet.ItemAlignmentDef alignDef1 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef1.setItemAlignmentViewId(R.id.details_frame);
        alignDef1.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions));
        alignDef1.setItemAlignmentOffsetPercent(0);
        // when description is selected, align details_frame to top edge
        ItemAlignmentFacet.ItemAlignmentDef alignDef2 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef2.setItemAlignmentViewId(R.id.details_frame);
        alignDef2.setItemAlignmentFocusViewId(R.id.details_overview_description);
        alignDef2.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_description));
        alignDef2.setItemAlignmentOffsetPercent(0);
        ItemAlignmentFacet.ItemAlignmentDef[] defs =
                new ItemAlignmentFacet.ItemAlignmentDef[] {alignDef1, alignDef2};
        facet.setAlignmentDefs(defs);
        presenter.setFacet(ItemAlignmentFacet.class, facet);
    }

    VerticalGridView getVerticalGridView() {
        return mRowsSupportFragment == null ? null : mRowsSupportFragment.getVerticalGridView();
    }

    RowsSupportFragment getRowsSupportFragment() {
        return mRowsSupportFragment;
    }

    /**
     * Setup dimensions that are only meaningful when the child Fragments are inside
     * DetailsSupportFragment.
     */
    private void setupChildFragmentLayout() {
        setVerticalGridViewLayout(mRowsSupportFragment.getVerticalGridView());
    }

    private void setupFocusSearchListener() {
        TitleHelper titleHelper = getTitleHelper();
        if (titleHelper != null) {
            BrowseFrameLayout browseFrameLayout = (BrowseFrameLayout) getView().findViewById(
                    R.id.details_fragment_root);
            browseFrameLayout.setOnFocusSearchListener(titleHelper.getOnFocusSearchListener());
        }
    }

    /**
     * Sets the selected row position with smooth animation.
     */
    public void setSelectedPosition(int position) {
        setSelectedPosition(position, true);
    }

    /**
     * Sets the selected row position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        mSetSelectionRunnable.mPosition = position;
        mSetSelectionRunnable.mSmooth = smooth;
        if (getView() != null && getView().getHandler() != null) {
            getView().getHandler().post(mSetSelectionRunnable);
        }
    }

    private void onRowSelected(int selectedPosition, int selectedSubPosition) {
        ObjectAdapter adapter = getAdapter();
        if (adapter == null || adapter.size() == 0 ||
                (selectedPosition == 0 && selectedSubPosition == 0)) {
            showTitle(true);
        } else {
            showTitle(false);
        }
        if (adapter != null && adapter.size() > selectedPosition) {
            final VerticalGridView gridView = getVerticalGridView();
            final int count = gridView.getChildCount();
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder bridgeViewHolder = (ItemBridgeAdapter.ViewHolder)
                        gridView.getChildViewHolder(gridView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) bridgeViewHolder.getPresenter();
                onSetRowStatus(rowPresenter,
                        rowPresenter.getRowViewHolder(bridgeViewHolder.getViewHolder()),
                        bridgeViewHolder.getAdapterPosition(),
                        selectedPosition, selectedSubPosition);
            }
        }
    }

    /**
     * Called on every visible row to change view status when current selected row position
     * or selected sub position changed.  Subclass may override.   The default
     * implementation calls {@link #onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter,
     * FullWidthDetailsOverviewRowPresenter.ViewHolder, int, int, int)} if presenter is
     * instance of {@link FullWidthDetailsOverviewRowPresenter}.
     *
     * @param presenter   The presenter used to create row ViewHolder.
     * @param viewHolder  The visible (attached) row ViewHolder, note that it may or may not
     *                    be selected.
     * @param adapterPosition  The adapter position of viewHolder inside adapter.
     * @param selectedPosition The adapter position of currently selected row.
     * @param selectedSubPosition The sub position within currently selected row.  This is used
     *                            When a row has multiple alignment positions.
     */
    protected void onSetRowStatus(RowPresenter presenter, RowPresenter.ViewHolder viewHolder, int
            adapterPosition, int selectedPosition, int selectedSubPosition) {
        if (presenter instanceof FullWidthDetailsOverviewRowPresenter) {
            onSetDetailsOverviewRowStatus((FullWidthDetailsOverviewRowPresenter) presenter,
                    (FullWidthDetailsOverviewRowPresenter.ViewHolder) viewHolder,
                    adapterPosition, selectedPosition, selectedSubPosition);
        }
    }

    /**
     * Called to change DetailsOverviewRow view status when current selected row position
     * or selected sub position changed.  Subclass may override.   The default
     * implementation switches between three states based on the positions:
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_HALF},
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_FULL} and
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_SMALL}.
     *
     * @param presenter   The presenter used to create row ViewHolder.
     * @param viewHolder  The visible (attached) row ViewHolder, note that it may or may not
     *                    be selected.
     * @param adapterPosition  The adapter position of viewHolder inside adapter.
     * @param selectedPosition The adapter position of currently selected row.
     * @param selectedSubPosition The sub position within currently selected row.  This is used
     *                            When a row has multiple alignment positions.
     */
    protected void onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter presenter,
            FullWidthDetailsOverviewRowPresenter.ViewHolder viewHolder, int adapterPosition,
            int selectedPosition, int selectedSubPosition) {
        if (selectedPosition > adapterPosition) {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        } else if (selectedPosition == adapterPosition && selectedSubPosition == 1) {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        } else if (selectedPosition == adapterPosition && selectedSubPosition == 0){
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_FULL);
        } else {
            presenter.setState(viewHolder,
                    FullWidthDetailsOverviewRowPresenter.STATE_SMALL);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setupChildFragmentLayout();
        setupFocusSearchListener();
        mRowsSupportFragment.getView().requestFocus();
        if (isEntranceTransitionEnabled()) {
            // make sure recycler view animation is disabled
            mRowsSupportFragment.onTransitionPrepare();
            mRowsSupportFragment.onTransitionStart();
            mRowsSupportFragment.setEntranceTransitionState(false);
        }
    }

    @Override
    protected Object createEntranceTransition() {
        return sTransitionHelper.loadTransition(getActivity(),
                R.transition.lb_details_enter_transition);
    }

    @Override
    protected void runEntranceTransition(Object entranceTransition) {
        sTransitionHelper.runTransition(mSceneAfterEntranceTransition,
                entranceTransition);
    }

    @Override
    protected void onEntranceTransitionEnd() {
        mRowsSupportFragment.onTransitionEnd();
    }

}
