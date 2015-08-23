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

package android.databinding.tool.expr;

import android.databinding.tool.reflection.ModelAnalyzer;
import android.databinding.tool.reflection.ModelClass;

import java.util.List;

public class GroupExpr extends Expr {
    public GroupExpr(Expr wrapped) {
        super(wrapped);
    }

    @Override
    protected ModelClass resolveType(ModelAnalyzer modelAnalyzer) {
        return getWrapped().getResolvedType();
    }

    @Override
    protected List<Dependency> constructDependencies() {
        return getWrapped().constructDependencies();
    }

    public Expr getWrapped() {
        return getChildren().get(0);
    }
}
