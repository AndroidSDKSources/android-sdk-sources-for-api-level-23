/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.support.v17.preference;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

/**
 * This fragment provides a preference fragment with leanback-style behavior, suitable for
 * embedding into broader UI elements.
 */
public abstract class BaseLeanbackPreferenceFragment extends PreferenceFragment {

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        VerticalGridView verticalGridView = (VerticalGridView) inflater
                .inflate(R.layout.leanback_preferences_list, parent, false);
        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        verticalGridView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED);
        return verticalGridView;
    }

    /**
     * @hide
     */
    @Override
    public Fragment getCallbackFragment() {
        return getParentFragment();
    }
}
