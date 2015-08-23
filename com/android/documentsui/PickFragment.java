/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.documentsui;

import android.R.string;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.documentsui.model.DocumentInfo;

import java.util.Locale;

/**
 * Display pick confirmation bar, usually for selecting a directory.
 */
public class PickFragment extends Fragment {
    public static final String TAG = "PickFragment";

    private int mAction;
    private DocumentInfo mPickTarget;
    private View mContainer;
    private Button mPick;
    private Button mCancel;

    public static void show(FragmentManager fm) {
        // Fragment can be restored by FragmentManager automatically.
        if (get(fm) != null) {
            return;
        }

        final PickFragment fragment = new PickFragment();
        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_save, fragment, TAG);
        ft.commitAllowingStateLoss();
    }

    public static PickFragment get(FragmentManager fm) {
        return (PickFragment) fm.findFragmentByTag(TAG);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContainer = inflater.inflate(R.layout.fragment_pick, container, false);

        mPick = (Button) mContainer.findViewById(android.R.id.button1);
        mPick.setOnClickListener(mPickListener);

        mCancel = (Button) mContainer.findViewById(android.R.id.button2);
        mCancel.setOnClickListener(mCancelListener);

        updateView();
        return mContainer;
    }

    private View.OnClickListener mPickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final DocumentsActivity activity = DocumentsActivity.get(PickFragment.this);
            activity.onPickRequested(mPickTarget);
        }
    };

    private View.OnClickListener mCancelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final BaseActivity activity = BaseActivity.get(PickFragment.this);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }
    };

    /**
     * @param action Which action defined in BaseActivity.State is the picker shown for.
     */
    public void setPickTarget(int action, DocumentInfo pickTarget) {
        mAction = action;
        mPickTarget = pickTarget;
        if (mContainer != null) {
            updateView();
        }
    }

    /**
     * Applies the state of fragment to the view components.
     */
    private void updateView() {
        switch (mAction) {
            case BaseActivity.State.ACTION_OPEN_TREE:
                mPick.setText(R.string.button_select);
                mCancel.setVisibility(View.GONE);
                break;
            case BaseActivity.State.ACTION_OPEN_COPY_DESTINATION:
                mPick.setText(R.string.button_copy);
                mCancel.setVisibility(View.VISIBLE);
                break;
            default:
                mContainer.setVisibility(View.GONE);
                return;
        }

        if (mPickTarget != null && (
                mAction == BaseActivity.State.ACTION_OPEN_TREE ||
                mPickTarget.isCreateSupported())) {
            mContainer.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.GONE);
        }
    }
}
