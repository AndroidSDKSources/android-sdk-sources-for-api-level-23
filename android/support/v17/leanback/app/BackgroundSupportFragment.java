/* This file is auto-generated from BackgroundFragment.java.  DO NOT MODIFY. */

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

import android.support.v4.app.Fragment;

/**
 * Fragment used by the background manager.
 * @hide
 */
public final class BackgroundSupportFragment extends Fragment implements
        BackgroundManager.FragmentStateQueriable {
    private BackgroundManager mBackgroundManager;

    void setBackgroundManager(BackgroundManager backgroundManager) {
        mBackgroundManager = backgroundManager;
    }

    BackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    public void onStart() {
        super.onStart();
        // mBackgroundManager might be null:
        // if BackgroundSupportFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.onActivityStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // mBackgroundManager might be null:
        // if BackgroundSupportFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.onResume();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // mBackgroundManager might be null:
        // if BackgroundSupportFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.detach();
        }
    }
}
