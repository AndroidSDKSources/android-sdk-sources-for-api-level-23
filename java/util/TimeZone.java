/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package java.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import libcore.icu.TimeZoneNames;
import libcore.io.IoUtils;
import libcore.util.ZoneInfoDB;

// TODO: repackage this class, used by frameworks/base.
import org.apache.harmony.luni.internal.util.TimezoneGetter;

/**
 * {@code TimeZone} represents a time zone, primarily used for configuring a {@link Calendar} or
 * {@link java.text.SimpleDateFormat} instance.
 *
 * <p>Most applications will use {@link #getDefault} which returns a {@code TimeZone} based on
 * the time zone where the program is running.
 *
 * <p>You can also get a specific {@code TimeZone} {@link #getTimeZone by Olson ID}.
 *
 * <p>It is highly unlikely you'll ever want to use anything but the factory methods yourself.
 * Let classes like {@link Calendar} and {@link java.text.SimpleDateFormat} do the date
 * computations for you.
 *
 * <p>If you do need to do date computations manually, there are two common cases to take into
 * account:
 * <ul>
 * <li>Somewhere like California, where daylight time is used.
 * The {@link #useDaylightTime} method will always return true, and {@link #inDaylightTime}
 * must be used to determine whether or not daylight time applies to a given {@code Date}.
 * The {@link #getRawOffset} method will return a raw offset of (in this case) -8 hours from UTC,
 * which isn't usually very useful. More usefully, the {@link #getOffset} methods return the
 * actual offset from UTC <i>for a given point in time</i>; this is the raw offset plus (if the
 * point in time is {@link #inDaylightTime in daylight time}) the applicable
 * {@link #getDSTSavings DST savings} (usually, but not necessarily, 1 hour).
 * <li>Somewhere like Japan, where daylight time is not used.
 * The {@link #useDaylightTime} and {@link #inDaylightTime} methods both always return false,
 * and the raw and actual offsets will always be the same.
 * </ul>
 *
 * <p>Note the type returned by the factory methods {@link #getDefault} and {@link #getTimeZone} is
 * implementation dependent. This may introduce serialization incompatibility issues between
 * different implementations, or different versions of Android.
 *
 * @see Calendar
 * @see GregorianCalendar
 * @see java.text.SimpleDateFormat
 */
public abstract class TimeZone implements Serializable, Cloneable {
    private static final long serialVersionUID = 3581463369166924961L;

    /**
     * Helper class to parse a custom timezone. This is in a separate class as regular expressions
     * cannot be compile-time initialized, so that static field is separated out from TimeZone
     * proper.
     */
    private final static class CustomTimeZoneParser {
        private static final Pattern CUSTOM_ZONE_ID_PATTERN =
                Pattern.compile("^GMT[-+](\\d{1,2})(:?(\\d\\d))?$");

        private CustomTimeZoneParser() {}

        /**
         * Returns a new SimpleTimeZone for an ID of the form "GMT[+|-]hh[[:]mm]", or null.
         */
        private static TimeZone getCustomTimeZone(String id) {
            Matcher m = CUSTOM_ZONE_ID_PATTERN.matcher(id);
            if (!m.matches()) {
                return null;
            }

            int hour;
            int minute = 0;
            try {
                hour = Integer.parseInt(m.group(1));
                if (m.group(3) != null) {
                    minute = Integer.parseInt(m.group(3));
                }
            } catch (NumberFormatException impossible) {
                throw new AssertionError(impossible);
            }

            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }

            char sign = id.charAt(3);
            int raw = (hour * 3600000) + (minute * 60000);
            if (sign == '-') {
                raw = -raw;
            }

            String cleanId = String.format((Locale) null, "GMT%c%02d:%02d", sign, hour, minute);
            return new SimpleTimeZone(raw, cleanId);
        }
    }

    /**
     * The short display name style, such as {@code PDT}. Requests for this
     * style may yield GMT offsets like {@code GMT-08:00}.
     */
    public static final int SHORT = 0;

    /**
     * The long display name style, such as {@code Pacific Daylight Time}.
     * Requests for this style may yield GMT offsets like {@code GMT-08:00}.
     */
    public static final int LONG = 1;

    private static final TimeZone GMT = new SimpleTimeZone(0, "GMT");
    private static final TimeZone UTC = new SimpleTimeZone(0, "UTC");

    private static TimeZone defaultTimeZone;

    private String ID;

    public TimeZone() {}

    /**
     * Returns a new time zone with the same ID, raw offset, and daylight
     * savings time rules as this time zone.
     */
    @Override public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the system's installed time zone IDs. Any of these IDs can be
     * passed to {@link #getTimeZone} to lookup the corresponding time zone
     * instance.
     */
    public static synchronized String[] getAvailableIDs() {
        return ZoneInfoDB.getInstance().getAvailableIDs();
    }

    /**
     * Returns the IDs of the time zones whose offset from UTC is {@code
     * offsetMillis}. Any of these IDs can be passed to {@link #getTimeZone} to
     * lookup the corresponding time zone instance.
     *
     * @return a possibly-empty array.
     */
    public static synchronized String[] getAvailableIDs(int offsetMillis) {
        return ZoneInfoDB.getInstance().getAvailableIDs(offsetMillis);
    }

    /**
     * Returns the user's preferred time zone. This may have been overridden for
     * this process with {@link #setDefault}.
     *
     * <p>Since the user's time zone changes dynamically, avoid caching this
     * value. Instead, use this method to look it up for each use.
     */
    public static synchronized TimeZone getDefault() {
        if (defaultTimeZone == null) {
            TimezoneGetter tzGetter = TimezoneGetter.getInstance();
            String zoneName = (tzGetter != null) ? tzGetter.getId() : null;
            if (zoneName != null) {
                zoneName = zoneName.trim();
            }
            if (zoneName == null || zoneName.isEmpty()) {
                try {
                    // On the host, we can find the configured timezone here.
                    zoneName = IoUtils.readFileAsString("/etc/timezone");
                } catch (IOException ex) {
                    // "vogar --mode device" can end up here.
                    // TODO: give libcore access to Android system properties and read "persist.sys.timezone".
                    zoneName = "GMT";
                }
            }
            defaultTimeZone = TimeZone.getTimeZone(zoneName);
        }
        return (TimeZone) defaultTimeZone.clone();
    }

    /**
     * Equivalent to {@code getDisplayName(false, TimeZone.LONG, Locale.getDefault())}.
     * <a href="../util/Locale.html#default_locale">Be wary of the default locale</a>.
     */
    public final String getDisplayName() {
        return getDisplayName(false, LONG, Locale.getDefault());
    }

    /**
     * Equivalent to {@code getDisplayName(false, TimeZone.LONG, locale)}.
     */
    public final String getDisplayName(Locale locale) {
        return getDisplayName(false, LONG, locale);
    }

    /**
     * Equivalent to {@code getDisplayName(daylightTime, style, Locale.getDefault())}.
     * <a href="../util/Locale.html#default_locale">Be wary of the default locale</a>.
     */
    public final String getDisplayName(boolean daylightTime, int style) {
        return getDisplayName(daylightTime, style, Locale.getDefault());
    }

    /**
     * Returns the {@link #SHORT short} or {@link #LONG long} name of this time
     * zone with either standard or daylight time, as written in {@code locale}.
     * If the name is not available, the result is in the format
     * {@code GMT[+-]hh:mm}.
     *
     * @param daylightTime true for daylight time, false for standard time.
     * @param style either {@link TimeZone#LONG} or {@link TimeZone#SHORT}.
     * @param locale the display locale.
     */
    public String getDisplayName(boolean daylightTime, int style, Locale locale) {
        if (style != SHORT && style != LONG) {
            throw new IllegalArgumentException("Bad style: " + style);
        }

        String[][] zoneStrings = TimeZoneNames.getZoneStrings(locale);
        String result = TimeZoneNames.getDisplayName(zoneStrings, getID(), daylightTime, style);
        if (result != null) {
            return result;
        }

        // If we get here, it's because icu4c has nothing for us. Most commonly, this is in the
        // case of short names. For Pacific/Fiji, for example, icu4c has nothing better to offer
        // than "GMT+12:00". Why do we re-do this work ourselves? Because we have up-to-date
        // time zone transition data, which icu4c _doesn't_ use --- it uses its own baked-in copy,
        // which only gets updated when we update icu4c. http://b/7955614 and http://b/8026776.

        // TODO: should we generate these once, in TimeZoneNames.getDisplayName? Revisit when we
        // upgrade to icu4c 50 and rewrite the underlying native code. See also the
        // "element[j] != null" check in SimpleDateFormat.parseTimeZone, and the extra work in
        // DateFormatSymbols.getZoneStrings.
        int offsetMillis = getRawOffset();
        if (daylightTime) {
            offsetMillis += getDSTSavings();
        }
        return createGmtOffsetString(true /* includeGmt */, true /* includeMinuteSeparator */,
                offsetMillis);
    }

    /**
     * Returns a string representation of an offset from UTC.
     *
     * <p>The format is "[GMT](+|-)HH[:]MM". The output is not localized.
     *
     * @param includeGmt true to include "GMT", false to exclude
     * @param includeMinuteSeparator true to include the separator between hours and minutes, false
     *     to exclude.
     * @param offsetMillis the offset from UTC
     *
     * @hide used internally by SimpleDateFormat
     */
    public static String createGmtOffsetString(boolean includeGmt,
            boolean includeMinuteSeparator, int offsetMillis) {
        int offsetMinutes = offsetMillis / 60000;
        char sign = '+';
        if (offsetMinutes < 0) {
            sign = '-';
            offsetMinutes = -offsetMinutes;
        }
        StringBuilder builder = new StringBuilder(9);
        if (includeGmt) {
            builder.append("GMT");
        }
        builder.append(sign);
        appendNumber(builder, 2, offsetMinutes / 60);
        if (includeMinuteSeparator) {
            builder.append(':');
        }
        appendNumber(builder, 2, offsetMinutes % 60);
        return builder.toString();
    }

    private static void appendNumber(StringBuilder builder, int count, int value) {
        String string = Integer.toString(value);
        for (int i = 0; i < count - string.length(); i++) {
            builder.append('0');
        }
        builder.append(string);
    }

    /**
     * Returns the ID of this {@code TimeZone}, such as
     * {@code America/Los_Angeles}, {@code GMT-08:00} or {@code UTC}.
     */
    public String getID() {
        return ID;
    }

    /**
     * Returns the latest daylight savings in milliseconds for this time zone, relative
     * to this time zone's regular UTC offset (as returned by {@link #getRawOffset}).
     *
     * <p>This class returns {@code 3600000} (1 hour) for time zones
     * that use daylight savings time and {@code 0} for timezones that do not,
     * leaving it to subclasses to override this method for other daylight savings
     * offsets. (There are time zones, such as {@code Australia/Lord_Howe},
     * that use other values.)
     *
     * <p>Note that this method doesn't tell you whether or not to <i>apply</i> the
     * offset: you need to call {@code inDaylightTime} for the specific time
     * you're interested in. If this method returns a non-zero offset, that only
     * tells you that this {@code TimeZone} sometimes observes daylight savings.
     *
     * <p>Note also that this method doesn't necessarily return the value you need
     * to apply to the time you're working with. This value can and does change over
     * time for a given time zone.
     *
     * <p>It's highly unlikely that you should ever call this method. You
     * probably want {@link #getOffset} instead, which tells you the offset
     * for a specific point in time, and takes daylight savings into account for you.
     */
    public int getDSTSavings() {
        return useDaylightTime() ? 3600000 : 0;
    }

    /**
     * Returns the offset in milliseconds from UTC for this time zone at {@code
     * time}. The offset includes daylight savings time if the specified
     * date is within the daylight savings time period.
     *
     * @param time the date in milliseconds since January 1, 1970 00:00:00 UTC
     */
    public int getOffset(long time) {
        if (inDaylightTime(new Date(time))) {
            return getRawOffset() + getDSTSavings();
        }
        return getRawOffset();
    }

    /**
     * Returns this time zone's offset in milliseconds from UTC at the specified
     * date and time. The offset includes daylight savings time if the date
     * and time is within the daylight savings time period.
     *
     * <p>This method is intended to be used by {@link Calendar} to compute
     * {@link Calendar#DST_OFFSET} and {@link Calendar#ZONE_OFFSET}. Application
     * code should have no reason to call this method directly. Each parameter
     * is interpreted in the same way as the corresponding {@code Calendar}
     * field. Refer to {@link Calendar} for specific definitions of this
     * method's parameters.
     */
    public abstract int getOffset(int era, int year, int month, int day,
            int dayOfWeek, int timeOfDayMillis);

    /**
     * Returns the offset in milliseconds from UTC of this time zone's standard
     * time.
     */
    public abstract int getRawOffset();

    /**
     * Returns a {@code TimeZone} corresponding to the given {@code id}, or {@code GMT}
     * for unknown ids.
     *
     * <p>An ID can be an Olson name of the form <i>Area</i>/<i>Location</i>, such
     * as {@code America/Los_Angeles}. The {@link #getAvailableIDs} method returns
     * the supported names.
     *
     * <p>This method can also create a custom {@code TimeZone} given an ID with the following
     * syntax: {@code GMT[+|-]hh[[:]mm]}. For example, {@code "GMT+05:00"}, {@code "GMT+0500"},
     * {@code "GMT+5:00"}, {@code "GMT+500"}, {@code "GMT+05"}, and {@code "GMT+5"} all return
     * an object with a raw offset of +5 hours from UTC, and which does <i>not</i> use daylight
     * savings. These are rarely useful, because they don't correspond to time zones actually
     * in use by humans.
     *
     * <p>Other than the special cases "UTC" and "GMT" (which are synonymous in this context,
     * both corresponding to UTC), Android does not support the deprecated three-letter time
     * zone IDs used in Java 1.1.
     */
    public static synchronized TimeZone getTimeZone(String id) {
        if (id == null) {
            throw new NullPointerException("id == null");
        }

        // Special cases? These can clone an existing instance.
        if (id.length() == 3) {
            if (id.equals("GMT")) {
                return (TimeZone) GMT.clone();
            }
            if (id.equals("UTC")) {
                return (TimeZone) UTC.clone();
            }
        }

        // In the database?
        TimeZone zone = null;
        try {
            zone = ZoneInfoDB.getInstance().makeTimeZone(id);
        } catch (IOException ignored) {
        }

        // Custom time zone?
        if (zone == null && id.length() > 3 && id.startsWith("GMT")) {
            zone = CustomTimeZoneParser.getCustomTimeZone(id);
        }

        // We never return null; on failure we return the equivalent of "GMT".
        return (zone != null) ? zone : (TimeZone) GMT.clone();
    }

    /**
     * Returns true if {@code timeZone} has the same rules as this time zone.
     *
     * <p>The base implementation returns true if both time zones have the same
     * raw offset.
     */
    public boolean hasSameRules(TimeZone timeZone) {
        if (timeZone == null) {
            return false;
        }
        return getRawOffset() == timeZone.getRawOffset();
    }

    /**
     * Returns true if {@code time} is in a daylight savings time period for
     * this time zone.
     */
    public abstract boolean inDaylightTime(Date time);

    /**
     * Overrides the default time zone for the current process only.
     *
     * <p><strong>Warning</strong>: avoid using this method to use a custom time
     * zone in your process. This value may be cleared or overwritten at any
     * time, which can cause unexpected behavior. Instead, manually supply a
     * custom time zone as needed.
     *
     * @param timeZone a custom time zone, or {@code null} to set the default to
     *     the user's preferred value.
     */
    public static synchronized void setDefault(TimeZone timeZone) {
        defaultTimeZone = timeZone != null ? (TimeZone) timeZone.clone() : null;
    }

    /**
     * Sets the ID of this {@code TimeZone}.
     */
    public void setID(String id) {
        if (id == null) {
            throw new NullPointerException("id == null");
        }
        ID = id;
    }

    /**
     * Sets the offset in milliseconds from UTC of this time zone's standard
     * time.
     */
    public abstract void setRawOffset(int offsetMillis);

    /**
     * Returns true if this time zone has a future transition to or from
     * daylight savings time.
     *
     * <p><strong>Warning:</strong> this returns false for time zones like
     * {@code Asia/Kuala_Lumpur} that have previously used DST but do not
     * currently. A hypothetical country that has never observed daylight
     * savings before but plans to start next year would return true.
     *
     * <p><strong>Warning:</strong> this returns true for time zones that use
     * DST, even when it is not active.
     *
     * <p>Use {@link #inDaylightTime} to find out whether daylight savings is
     * in effect at a specific time.
     *
     * <p>Most applications should not use this method.
     */
    public abstract boolean useDaylightTime();
}
