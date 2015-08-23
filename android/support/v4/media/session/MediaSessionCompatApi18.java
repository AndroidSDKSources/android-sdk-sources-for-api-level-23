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
package android.support.v4.media.session;

import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.os.SystemClock;

public class MediaSessionCompatApi18 {
    /***** PlaybackState actions *****/
    private static final long ACTION_SEEK_TO = 1 << 8;

    public static Object createPlaybackPositionUpdateListener(
            MediaSessionCompatApi14.Callback callback) {
        return new OnPlaybackPositionUpdateListener<MediaSessionCompatApi14.Callback>(callback);
    }

    public static void registerMediaButtonEventReceiver(Context context, PendingIntent pi) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.registerMediaButtonEventReceiver(pi);
    }

    public static void unregisterMediaButtonEventReceiver(Context context, PendingIntent pi) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.unregisterMediaButtonEventReceiver(pi);
    }

    public static void setState(Object rccObj, int state, long position, float speed,
            long updateTime) {
        long currTime = SystemClock.elapsedRealtime();
        if (state == MediaSessionCompatApi14.STATE_PLAYING && position > 0) {
            long diff = 0;
            if (updateTime > 0) {
                diff = currTime - updateTime;
                if (speed > 0 && speed != 1f) {
                    diff *= speed;
                }
            }
            position += diff;
        }
        state = MediaSessionCompatApi14.getRccStateFromState(state);
        ((RemoteControlClient) rccObj).setPlaybackState(state, position, speed);
    }

    public static void setTransportControlFlags(Object rccObj, long actions) {
        ((RemoteControlClient) rccObj).setTransportControlFlags(
                getRccTransportControlFlagsFromActions(actions));
    }

    public static void setOnPlaybackPositionUpdateListener(Object rccObj,
            Object onPositionUpdateObj) {
        ((RemoteControlClient) rccObj).setPlaybackPositionUpdateListener(
                (RemoteControlClient.OnPlaybackPositionUpdateListener) onPositionUpdateObj);
    }

    static int getRccTransportControlFlagsFromActions(long actions) {
        int transportControlFlags =
                MediaSessionCompatApi14.getRccTransportControlFlagsFromActions(actions);
        if ((actions & ACTION_SEEK_TO) != 0) {
            transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;
        }
        return transportControlFlags;
    }

    static class OnPlaybackPositionUpdateListener<T extends MediaSessionCompatApi14.Callback>
            implements RemoteControlClient.OnPlaybackPositionUpdateListener {
        protected final T mCallback;

        public OnPlaybackPositionUpdateListener(T callback) {
            mCallback = callback;
        }

        @Override
        public void onPlaybackPositionUpdate(long newPositionMs) {
            mCallback.onSeekTo(newPositionMs);
        }
    }
}
