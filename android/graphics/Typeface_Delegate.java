/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.graphics;

import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.annotation.NonNull;
import android.graphics.FontFamily_Delegate.FontVariant;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.FontFamily_Delegate.getFontLocation;

/**
 * Delegate implementing the native methods of android.graphics.Typeface
 *
 * Through the layoutlib_create tool, the original native methods of Typeface have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 * This class behaves like the original native implementation, but in Java, keeping previously
 * native data into its own objects and mapping them to int that are sent back and forth between
 * it and the original Typeface class.
 *
 * @see DelegateManager
 *
 */
public final class Typeface_Delegate {

    public static final String SYSTEM_FONTS = "/system/fonts/";

    // ---- delegate manager ----
    private static final DelegateManager<Typeface_Delegate> sManager =
            new DelegateManager<Typeface_Delegate>(Typeface_Delegate.class);


    // ---- delegate data ----

    @NonNull
    private final FontFamily_Delegate[] mFontFamilies;  // the reference to FontFamily_Delegate.
    /** @see Font#getStyle() */
    private final int mStyle;
    private final int mWeight;

    private static long sDefaultTypeface;


    // ---- Public Helper methods ----

    public static Typeface_Delegate getDelegate(long nativeTypeface) {
        return sManager.getDelegate(nativeTypeface);
    }

    /**
     * Return a list of fonts that match the style and variant. The list is ordered according to
     * preference of fonts.
     *
     * The list may contain null when the font failed to load. If null is reached when trying to
     * render with this list of fonts, then a warning should be logged letting the user know that
     * some font failed to load.
     *
     * @param variant The variant preferred. Can only be {@link FontVariant#COMPACT} or
     *                {@link FontVariant#ELEGANT}
     */
    @NonNull
    public List<Font> getFonts(FontVariant variant) {
        assert variant != FontVariant.NONE;

        // Calculate the required weight based on style and weight of this typeface.
        int weight = mWeight + ((mStyle & Font.BOLD) == 0 ? 0 : FontFamily_Delegate.BOLD_FONT_WEIGHT_DELTA);
        if (weight > 900) {
            weight = 900;
        }
        final boolean isItalic = (mStyle & Font.ITALIC) != 0;
        List<Font> fonts = new ArrayList<Font>(mFontFamilies.length);
        for (int i = 0; i < mFontFamilies.length; i++) {
            FontFamily_Delegate ffd = mFontFamilies[i];
            if (ffd != null && ffd.isValid()) {
                Font font = ffd.getFont(weight, isItalic);
                if (font != null) {
                    FontVariant ffdVariant = ffd.getVariant();
                    if (ffdVariant == FontVariant.NONE) {
                        fonts.add(font);
                        continue;
                    }
                    // We cannot open each font and get locales supported, etc to match the fonts.
                    // As a workaround, we hardcode certain assumptions like Elegant and Compact
                    // always appear in pairs.
                    assert i < mFontFamilies.length - 1;
                    FontFamily_Delegate ffd2 = mFontFamilies[++i];
                    assert ffd2 != null;
                    FontVariant ffd2Variant = ffd2.getVariant();
                    Font font2 = ffd2.getFont(weight, isItalic);
                    assert ffd2Variant != FontVariant.NONE && ffd2Variant != ffdVariant
                            && font2 != null;
                    // Add the font with the matching variant to the list.
                    if (variant == ffd.getVariant()) {
                        fonts.add(font);
                    } else {
                        fonts.add(font2);
                    }
                } else {
                    // The FontFamily is valid but doesn't contain any matching font. This means
                    // that the font failed to load. We add null to the list of fonts. Don't throw
                    // the warning just yet. If this is a non-english font, we don't want to warn
                    // users who are trying to render only english text.
                    fonts.add(null);
                }
            }
        }
        return fonts;
    }

    /**
     * Clear the default typefaces when disposing bridge.
     */
    public static void resetDefaults() {
        // Sometimes this is called before the Bridge is initialized. In that case, we don't want to
        // initialize Typeface because the SDK fonts location hasn't been set.
        if (FontFamily_Delegate.getFontLocation() != null) {
            Typeface.sDefaults = null;
        }
    }


    // ---- native methods ----

    @LayoutlibDelegate
    /*package*/ static synchronized long nativeCreateFromTypeface(long native_instance, int style) {
        Typeface_Delegate delegate = sManager.getDelegate(native_instance);
        if (delegate == null) {
            delegate = sManager.getDelegate(sDefaultTypeface);
        }
        if (delegate == null) {
            return 0;
        }

        return sManager.addNewDelegate(new Typeface_Delegate(delegate.mFontFamilies, style,
                delegate.mWeight));
    }

    @LayoutlibDelegate
    /*package*/ static long nativeCreateWeightAlias(long native_instance, int weight) {
        Typeface_Delegate delegate = sManager.getDelegate(native_instance);
        if (delegate == null) {
            delegate = sManager.getDelegate(sDefaultTypeface);
        }
        if (delegate == null) {
            return 0;
        }
        Typeface_Delegate weightAlias =
                new Typeface_Delegate(delegate.mFontFamilies, delegate.mStyle, weight);
        return sManager.addNewDelegate(weightAlias);
    }

    @LayoutlibDelegate
    /*package*/ static synchronized long nativeCreateFromArray(long[] familyArray) {
        FontFamily_Delegate[] fontFamilies = new FontFamily_Delegate[familyArray.length];
        for (int i = 0; i < familyArray.length; i++) {
            fontFamilies[i] = FontFamily_Delegate.getDelegate(familyArray[i]);
        }
        Typeface_Delegate delegate = new Typeface_Delegate(fontFamilies, Typeface.NORMAL);
        return sManager.addNewDelegate(delegate);
    }

    @LayoutlibDelegate
    /*package*/ static void nativeUnref(long native_instance) {
        sManager.removeJavaReferenceFor(native_instance);
    }

    @LayoutlibDelegate
    /*package*/ static int nativeGetStyle(long native_instance) {
        Typeface_Delegate delegate = sManager.getDelegate(native_instance);
        if (delegate == null) {
            return 0;
        }

        return delegate.mStyle;
    }

    @LayoutlibDelegate
    /*package*/ static void nativeSetDefault(long native_instance) {
        sDefaultTypeface = native_instance;
    }

    @LayoutlibDelegate
    /*package*/ static File getSystemFontConfigLocation() {
        return new File(getFontLocation());
    }

    // ---- Private delegate/helper methods ----

    private Typeface_Delegate(@NonNull FontFamily_Delegate[] fontFamilies, int style) {
        this(fontFamilies, style, FontFamily_Delegate.DEFAULT_FONT_WEIGHT);
    }

    public Typeface_Delegate(@NonNull FontFamily_Delegate[] fontFamilies, int style, int weight) {
        mFontFamilies = fontFamilies;
        mStyle = style;
        mWeight = weight;
    }
}
