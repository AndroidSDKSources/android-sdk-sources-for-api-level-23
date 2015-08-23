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
package android.support.v17.leanback.transition;

import android.R;
import android.content.Context;
import android.transition.ChangeTransform;
import android.view.Window;
import android.view.animation.AnimationUtils;

final class TransitionHelperApi21 {

    TransitionHelperApi21() {
    }

    public static Object getSharedElementEnterTransition(Window window) {
        return window.getSharedElementEnterTransition();
    }

    public static Object getSharedElementReturnTransition(Window window) {
        return window.getSharedElementReturnTransition();
    }

    public static Object getSharedElementExitTransition(Window window) {
        return window.getSharedElementExitTransition();
    }

    public static Object getSharedElementReenterTransition(Window window) {
        return window.getSharedElementReenterTransition();
    }

    public static Object getEnterTransition(Window window) {
        return window.getEnterTransition();
    }

    public static Object getReturnTransition(Window window) {
        return window.getReturnTransition();
    }

    public static Object getExitTransition(Window window) {
        return window.getExitTransition();
    }

    public static Object getReenterTransition(Window window) {
        return window.getReenterTransition();
    }

    public static Object createScale() {
        return new ChangeTransform();
    }

    public static Object createDefaultInterpolator(Context context) {
        return AnimationUtils.loadInterpolator(context, R.interpolator.fast_out_linear_in);
    }
}
