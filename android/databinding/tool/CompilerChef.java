/*
 * Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding.tool;

import android.databinding.tool.store.ResourceBundle;
import android.databinding.tool.util.L;
import android.databinding.tool.writer.BRWriter;
import android.databinding.tool.writer.DataBinderWriter;
import android.databinding.tool.writer.JavaFileWriter;

import java.util.Set;

/**
 * Chef class for compiler.
 *
 * Different build systems can initiate a version of this to handle their work
 */
public class CompilerChef {
    private JavaFileWriter mFileWriter;
    private ResourceBundle mResourceBundle;
    private DataBinder mDataBinder;

    private CompilerChef() {
    }

    public static CompilerChef createChef(ResourceBundle bundle, JavaFileWriter fileWriter) {
        CompilerChef chef = new CompilerChef();

        chef.mResourceBundle = bundle;
        chef.mFileWriter = fileWriter;
        chef.mResourceBundle.validateMultiResLayouts();
        return chef;
    }

    public ResourceBundle getResourceBundle() {
        return mResourceBundle;
    }

    public void ensureDataBinder() {
        if (mDataBinder == null) {
            mDataBinder = new DataBinder(mResourceBundle);
            mDataBinder.setFileWriter(mFileWriter);
        }
    }

    public boolean hasAnythingToGenerate() {
        L.d("checking if we have anything to generate. bundle size: %s",
                mResourceBundle == null ? -1 : mResourceBundle.getLayoutBundles().size());
        return mResourceBundle != null && mResourceBundle.getLayoutBundles().size() > 0;
    }

    public void writeDataBinderMapper(int minSdk, BRWriter brWriter) {
        ensureDataBinder();
        final String pkg = "android.databinding";
        DataBinderWriter dbr = new DataBinderWriter(pkg, mResourceBundle.getAppPackage(),
                "DataBinderMapper", mDataBinder.getLayoutBinders(), minSdk);
        mFileWriter.writeToFile(pkg + "." + dbr.getClassName(), dbr.write(brWriter));
    }

    /**
     * Adds variables to list of Bindables.
     */
    public void addBRVariables(BindableHolder bindables) {
        ensureDataBinder();
        for (LayoutBinder layoutBinder : mDataBinder.mLayoutBinders) {
            for (String variableName : layoutBinder.getUserDefinedVariables().keySet()) {
                bindables.addVariable(variableName, layoutBinder.getClassName());
            }
        }
    }

    public void sealModels() {
        ensureDataBinder();
        mDataBinder.sealModels();
    }
    
    public void writeViewBinderInterfaces(boolean isLibrary) {
        ensureDataBinder();
        mDataBinder.writerBaseClasses(isLibrary);
    }

    public void writeViewBinders(int minSdk) {
        ensureDataBinder();
        mDataBinder.writeBinders(minSdk);
    }

    public void writeComponent() {
        ensureDataBinder();
        mDataBinder.writeComponent();
    }

    public Set<String> getWrittenClassNames() {
        ensureDataBinder();
        return mDataBinder.getWrittenClassNames();
    }

    public interface BindableHolder {
        void addVariable(String variableName, String containingClassName);
    }
}
