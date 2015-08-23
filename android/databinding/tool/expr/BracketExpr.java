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

public class BracketExpr extends Expr {

    public static enum BracketAccessor {
        ARRAY,
        LIST,
        MAP,
    }

    private BracketAccessor mAccessor;

    BracketExpr(Expr target, Expr arg) {
        super(target, arg);
    }

    @Override
    protected ModelClass resolveType(ModelAnalyzer modelAnalyzer) {
        ModelClass targetType = getTarget().getResolvedType();
        if (targetType.isArray()) {
            mAccessor = BracketAccessor.ARRAY;
        } else if (targetType.isList()) {
            mAccessor = BracketAccessor.LIST;
        } else if (targetType.isMap()) {
            mAccessor = BracketAccessor.MAP;
        } else {
            throw new IllegalArgumentException("Cannot determine variable type used in [] " +
                    "expression. Cast the value to List, Map, " +
                    "or array. Type detected: " + targetType.toJavaCode());
        }
        return targetType.getComponentType();
    }

    @Override
    protected List<Dependency> constructDependencies() {
        return constructDynamicChildrenDependencies();
    }

    protected String computeUniqueKey() {
        return join(getTarget().computeUniqueKey(), "$", getArg().computeUniqueKey(), "$");
    }

    public Expr getTarget() {
        return getChildren().get(0);
    }

    public Expr getArg() {
        return getChildren().get(1);
    }

    public BracketAccessor getAccessor() {
        return mAccessor;
    }

    public boolean argCastsInteger() {
        return Object.class.equals(getArg().getResolvedType());
    }
}
