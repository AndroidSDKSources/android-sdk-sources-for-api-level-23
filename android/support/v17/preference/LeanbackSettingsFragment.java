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
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Space;

public abstract class LeanbackSettingsFragment extends Fragment
        implements PreferenceFragment.OnPreferenceStartFragmentCallback,
        PreferenceFragment.OnPreferenceStartScreenCallback,
        PreferenceFragment.OnPreferenceDisplayDialogCallback {

    private static final String PREFERENCE_FRAGMENT_TAG =
            "android.support.v17.preference.LeanbackSettingsFragment.PREFERENCE_FRAGMENT";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.leanback_settings_fragment, container, false);

        // Trap back button presses
        ((LeanbackSettingsRootView) v).setOnBackKeyListener(new RootViewOnKeyListener());

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            onPreferenceStartInitialScreen();
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragment caller, Preference pref) {
        final Fragment f;
        if (pref instanceof ListPreference) {
            final ListPreference listPreference = (ListPreference) pref;
            f = LeanbackListPreferenceDialogFragment.newInstanceSingle(listPreference.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);
        } else if (pref instanceof MultiSelectListPreference) {
            MultiSelectListPreference listPreference = (MultiSelectListPreference) pref;
            f = LeanbackListPreferenceDialogFragment.newInstanceMulti(listPreference.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);
        }
        // TODO
//        else if (pref instanceof EditTextPreference) {
//
//        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Called to instantiate the initial {@link android.support.v14.preference.PreferenceFragment}
     * to be shown in this fragment. Implementations are expected to call
     * {@link #startPreferenceFragment(android.app.Fragment)}.
     */
    public abstract void onPreferenceStartInitialScreen();

    /**
     * Displays a preference fragment to the user. This method can also be used to display
     * list-style fragments on top of the stack of preference fragments.
     *
     * @param fragment Fragment instance to be added.
     */
    public void startPreferenceFragment(@NonNull Fragment fragment) {
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        final Fragment prevFragment =
                getChildFragmentManager().findFragmentByTag(PREFERENCE_FRAGMENT_TAG);
        if (prevFragment != null) {
            transaction
                    .addToBackStack(null)
                    .replace(R.id.settings_preference_fragment_container, fragment,
                            PREFERENCE_FRAGMENT_TAG);
        } else {
            transaction
                    .add(R.id.settings_preference_fragment_container, fragment,
                            PREFERENCE_FRAGMENT_TAG);
        }
        transaction.commit();
    }

    /**
     * Displays a fragment to the user, temporarily replacing the contents of this fragment.
     *
     * @param fragment Fragment instance to be added.
     */
    public void startImmersiveFragment(@NonNull Fragment fragment) {
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        final Fragment preferenceFragment =
                getChildFragmentManager().findFragmentByTag(PREFERENCE_FRAGMENT_TAG);
        if (preferenceFragment != null && !preferenceFragment.isHidden()) {
            if (Build.VERSION.SDK_INT < 23) {
                // b/22631964
                transaction.add(R.id.settings_preference_fragment_container, new DummyFragment());
            }
            transaction.remove(preferenceFragment);
        }
        transaction
                .add(R.id.settings_dialog_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private class RootViewOnKeyListener implements View.OnKeyListener {

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return getChildFragmentManager().popBackStackImmediate();
            } else {
                return false;
            }
        }
    }

    /**
     * @hide
     */
    public static class DummyFragment extends Fragment {

        @Override
        public @Nullable View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            final View v = new Space(inflater.getContext());
            v.setVisibility(View.GONE);
            return v;
        }
    }
}
