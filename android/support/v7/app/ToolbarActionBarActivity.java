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
 * limitations under the License.
 */

package android.support.v7.app;

import android.support.v7.appcompat.test.R;
import android.support.v7.widget.Toolbar;

public class ToolbarActionBarActivity extends BaseTestActivity {

    private Toolbar mToolbar;

    @Override
    protected int getContentViewLayoutResId() {
        return R.layout.toolbar_decor_content;
    }

    @Override
    protected void onContentViewSet() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
    }
}
