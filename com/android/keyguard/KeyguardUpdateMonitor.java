/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;

import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN;
import static android.os.BatteryManager.EXTRA_STATUS;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_HEALTH;

import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.google.android.collect.Lists;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedUnlockAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedUnlockAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor implements TrustManager.TrustListener {

    private static final String TAG = "KeyguardUpdateMonitor";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final boolean DEBUG_SIM_STATES = KeyguardConstants.DEBUG_SIM_STATES;
    private static final boolean DEBUG_FP_WAKELOCK = KeyguardConstants.DEBUG_FP_WAKELOCK;
    private static final int LOW_BATTERY_THRESHOLD = 20;
    private static final long FINGERPRINT_WAKELOCK_TIMEOUT_MS = 15 * 1000;

    private static final String ACTION_FACE_UNLOCK_STARTED
            = "com.android.facelock.FACE_UNLOCK_STARTED";
    private static final String ACTION_FACE_UNLOCK_STOPPED
            = "com.android.facelock.FACE_UNLOCK_STOPPED";
    private static final String FINGERPRINT_WAKE_LOCK_NAME = "wake-and-unlock wakelock";

    /**
     * Mode in which we don't need to wake up the device when we get a fingerprint.
     */
    private static final int FP_WAKE_NONE = 0;

    /**
     * Mode in which we wake up the device, and directly dismiss Keyguard. Active when we acquire
     * a fingerprint while the screen is off and the device was sleeping.
     */
    private static final int FP_WAKE_DIRECT_UNLOCK = 1;

    /**
     * Mode in which we wake up the device, but play the normal dismiss animation. Active when we
     * acquire a fingerprint pulsing in doze mode.
     * */
    private static final int FP_WAKE_WAKE_TO_BOUNCER = 2;

    // Callback messages
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_DPM_STATE_CHANGED = 309;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 311;
    private static final int MSG_KEYGUARD_RESET = 312;
    private static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_USER_SWITCH_COMPLETE = 314;
    private static final int MSG_USER_INFO_CHANGED = 317;
    private static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_STARTED_WAKING_UP = 319;
    private static final int MSG_FINISHED_GOING_TO_SLEEP = 320;
    private static final int MSG_KEYGUARD_BOUNCER_CHANGED = 322;
    private static final int MSG_FACE_UNLOCK_STATE_CHANGED = 327;
    private static final int MSG_SIM_SUBSCRIPTION_INFO_CHANGED = 328;
    private static final int MSG_AIRPLANE_MODE_CHANGED = 329;
    private static final int MSG_SERVICE_STATE_CHANGE = 330;

    private static KeyguardUpdateMonitor sInstance;

    private final Context mContext;
    HashMap<Integer, SimData> mSimDatas = new HashMap<Integer, SimData>();
    HashMap<Integer, ServiceState> mServiceStates = new HashMap<Integer, ServiceState>();

    private int mRingMode;
    private int mPhoneState;
    private boolean mKeyguardIsVisible;
    private boolean mBouncer;
    private boolean mBootCompleted;
    private boolean mUserHasAuthenticatedSinceBoot;

    // Device provisioning state
    private boolean mDeviceProvisioned;

    // Battery status
    private BatteryStatus mBatteryStatus;

    // Password attempts
    private SparseIntArray mFailedAttempts = new SparseIntArray();

    private boolean mClockVisible;

    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mCallbacks = Lists.newArrayList();
    private ContentObserver mDeviceProvisionedObserver;

    private boolean mSwitchingUser;

    private boolean mDeviceInteractive;
    private boolean mScreenOn;
    private SubscriptionManager mSubscriptionManager;
    private List<SubscriptionInfo> mSubscriptionInfo;
    private boolean mFingerprintDetectionRunning;
    private TrustManager mTrustManager;
    private PowerManager mPowerManager;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME_UPDATE:
                    handleTimeUpdate();
                    break;
                case MSG_BATTERY_UPDATE:
                    handleBatteryUpdate((BatteryStatus) msg.obj);
                    break;
                case MSG_SIM_STATE_CHANGE:
                    handleSimStateChange(msg.arg1, msg.arg2, (State) msg.obj);
                    break;
                case MSG_RINGER_MODE_CHANGED:
                    handleRingerModeChange(msg.arg1);
                    break;
                case MSG_PHONE_STATE_CHANGED:
                    handlePhoneStateChanged((String) msg.obj);
                    break;
                case MSG_DEVICE_PROVISIONED:
                    handleDeviceProvisioned();
                    break;
                case MSG_DPM_STATE_CHANGED:
                    handleDevicePolicyManagerStateChanged();
                    break;
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1, (IRemoteCallback) msg.obj);
                    break;
                case MSG_USER_SWITCH_COMPLETE:
                    handleUserSwitchComplete(msg.arg1);
                    break;
                case MSG_KEYGUARD_VISIBILITY_CHANGED:
                    handleKeyguardVisibilityChanged(msg.arg1);
                    break;
                case MSG_KEYGUARD_RESET:
                    handleKeyguardReset();
                    break;
                case MSG_KEYGUARD_BOUNCER_CHANGED:
                    handleKeyguardBouncerChanged(msg.arg1);
                    break;
                case MSG_BOOT_COMPLETED:
                    handleBootCompleted();
                    break;
                case MSG_USER_INFO_CHANGED:
                    handleUserInfoChanged(msg.arg1);
                    break;
                case MSG_REPORT_EMERGENCY_CALL_ACTION:
                    handleReportEmergencyCallAction();
                    break;
                case MSG_FINISHED_GOING_TO_SLEEP:
                    handleFinishedGoingToSleep(msg.arg1);
                    break;
                case MSG_STARTED_WAKING_UP:
                    handleStartedWakingUp();
                    break;
                case MSG_FACE_UNLOCK_STATE_CHANGED:
                    handleFaceUnlockStateChanged(msg.arg1 != 0, msg.arg2);
                    break;
                case MSG_SIM_SUBSCRIPTION_INFO_CHANGED:
                    handleSimSubscriptionInfoChanged();
                    break;
                case MSG_AIRPLANE_MODE_CHANGED:
                    handleAirplaneModeChanged();
                    break;
                case MSG_SERVICE_STATE_CHANGE:
                    handleServiceStateChange(msg.arg1, (ServiceState) msg.obj);
                    break;
            }
        }
    };

    private OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            mHandler.sendEmptyMessage(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
        }
    };

    private SparseBooleanArray mUserHasTrust = new SparseBooleanArray();
    private SparseBooleanArray mUserTrustIsManaged = new SparseBooleanArray();
    private SparseBooleanArray mUserFingerprintAuthenticated = new SparseBooleanArray();
    private SparseBooleanArray mUserFaceUnlockRunning = new SparseBooleanArray();

    private static int sCurrentUser;

    private int mFpWakeMode;

    public synchronized static void setCurrentUser(int currentUser) {
        sCurrentUser = currentUser;
    }

    public synchronized static int getCurrentUser() {
        return sCurrentUser;
    }

    @Override
    public void onTrustChanged(boolean enabled, int userId, int flags) {
        mUserHasTrust.put(userId, enabled);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustChanged(userId);
                if (enabled && flags != 0) {
                    cb.onTrustGrantedWithFlags(flags, userId);
                }
            }
        }
    }

    protected void handleSimSubscriptionInfoChanged() {
        if (DEBUG_SIM_STATES) {
            Log.v(TAG, "onSubscriptionInfoChanged()");
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            if (sil != null) {
                for (SubscriptionInfo subInfo : sil) {
                    Log.v(TAG, "SubInfo:" + subInfo);
                }
            } else {
                Log.v(TAG, "onSubscriptionInfoChanged: list is null");
            }
        }
        List<SubscriptionInfo> subscriptionInfos = getSubscriptionInfo(true /* forceReload */);

        // Hack level over 9000: Because the subscription id is not yet valid when we see the
        // first update in handleSimStateChange, we need to force refresh all all SIM states
        // so the subscription id for them is consistent.
        ArrayList<SubscriptionInfo> changedSubscriptions = new ArrayList<>();
        for (int i = 0; i < subscriptionInfos.size(); i++) {
            SubscriptionInfo info = subscriptionInfos.get(i);
            boolean changed = refreshSimState(info.getSubscriptionId(), info.getSimSlotIndex());
            if (changed) {
                changedSubscriptions.add(info);
            }
        }
        for (int i = 0; i < changedSubscriptions.size(); i++) {
            SimData data = mSimDatas.get(changedSubscriptions.get(i).getSubscriptionId());
            for (int j = 0; j < mCallbacks.size(); j++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
                if (cb != null) {
                    cb.onSimStateChanged(data.subId, data.slotId, data.simState);
                }
            }
        }
        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    private void handleAirplaneModeChanged() {
        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    /** @return List of SubscriptionInfo records, maybe empty but never null */
    List<SubscriptionInfo> getSubscriptionInfo(boolean forceReload) {
        List<SubscriptionInfo> sil = mSubscriptionInfo;
        if (sil == null || forceReload) {
            sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        }
        if (sil == null) {
            // getActiveSubscriptionInfoList was null callers expect an empty list.
            mSubscriptionInfo = new ArrayList<SubscriptionInfo>();
        } else {
            mSubscriptionInfo = sil;
        }
        return mSubscriptionInfo;
    }

    @Override
    public void onTrustManagedChanged(boolean managed, int userId) {
        mUserTrustIsManaged.put(userId, managed);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustManagedChanged(userId);
            }
        }
    }

    private void onFingerprintAuthenticated(int userId, boolean wakeAndUnlocking) {
        mUserFingerprintAuthenticated.put(userId, true);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintAuthenticated(userId, wakeAndUnlocking);
            }
        }
    }

    private void handleFingerprintAuthFailed() {
        releaseFingerprintWakeLock();
        handleFingerprintHelp(-1, mContext.getString(R.string.fingerprint_not_recognized));
    }

    private void handleFingerprintAcquired(int acquireInfo) {
        if (acquireInfo != FingerprintManager.FINGERPRINT_ACQUIRED_GOOD) {
            return;
        }
        if (!mDeviceInteractive && !mScreenOn) {
            releaseFingerprintWakeLock();
            mWakeLock = mPowerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, FINGERPRINT_WAKE_LOCK_NAME);
            mWakeLock.acquire();
            mFpWakeMode = FP_WAKE_DIRECT_UNLOCK;
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fingerprint acquired, grabbing fp wakelock");
            }
            mHandler.postDelayed(mReleaseFingerprintWakeLockRunnable,
                    FINGERPRINT_WAKELOCK_TIMEOUT_MS);
        } else if (!mDeviceInteractive) {
            mFpWakeMode = FP_WAKE_WAKE_TO_BOUNCER;
        } else {
            mFpWakeMode = FP_WAKE_NONE;
        }
    }

    private final Runnable mReleaseFingerprintWakeLockRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fp wakelock: TIMEOUT!!");
            }
            releaseFingerprintWakeLock();
        }
    };

    private void releaseFingerprintWakeLock() {
        if (mWakeLock != null) {
            mHandler.removeCallbacks(mReleaseFingerprintWakeLockRunnable);
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "releasing fp wakelock");
            }
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    private void handleFingerprintAuthenticated() {
        if (mFpWakeMode == FP_WAKE_WAKE_TO_BOUNCER || mFpWakeMode == FP_WAKE_DIRECT_UNLOCK) {
            if (DEBUG_FP_WAKELOCK) {
                Log.i(TAG, "fp wakelock: Authenticated, waking up...");
            }
            mPowerManager.wakeUp(SystemClock.uptimeMillis());
        }
        releaseFingerprintWakeLock();
        try {
            final int userId;
            try {
                userId = ActivityManagerNative.getDefault().getCurrentUser().id;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get current user id: ", e);
                return;
            }
            if (isFingerprintDisabled(userId)) {
                Log.d(TAG, "Fingerprint disabled by DPM for userId: " + userId);
                return;
            }
            onFingerprintAuthenticated(userId, mFpWakeMode == FP_WAKE_DIRECT_UNLOCK);
        } finally {
            setFingerprintRunningDetectionRunning(false);
        }
    }

    private void handleFingerprintHelp(int msgId, String helpString) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintHelp(msgId, helpString);
            }
        }
    }

    private void handleFingerprintError(int msgId, String errString) {
        setFingerprintRunningDetectionRunning(false);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintError(msgId, errString);
            }
        }
    }

    private void setFingerprintRunningDetectionRunning(boolean running) {
        if (running != mFingerprintDetectionRunning) {
            mFingerprintDetectionRunning = running;
            notifyFingerprintRunningStateChanged();
        }
    }

    private void notifyFingerprintRunningStateChanged() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintRunningStateChanged(mFingerprintDetectionRunning);
            }
        }
    }
    private void handleFaceUnlockStateChanged(boolean running, int userId) {
        mUserFaceUnlockRunning.put(userId, running);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFaceUnlockStateChanged(running, userId);
            }
        }
    }

    public boolean isFaceUnlockRunning(int userId) {
        return mUserFaceUnlockRunning.get(userId);
    }

    public boolean isFingerprintDetectionRunning() {
        return mFingerprintDetectionRunning;
    }

    private boolean isTrustDisabled(int userId) {
        // Don't allow trust agent if device is secured with a SIM PIN. This is here
        // mainly because there's no other way to prompt the user to enter their SIM PIN
        // once they get past the keyguard screen.
        final boolean disabledBySimPin = isSimPinSecure();
        return disabledBySimPin;
    }

    private boolean isFingerprintDisabled(int userId) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && (dpm.getKeyguardDisabledFeatures(null, userId)
                    & DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT) != 0;
    }

    public boolean getUserCanSkipBouncer(int userId) {
        return getUserHasTrust(userId) || (mUserFingerprintAuthenticated.get(userId)
                && isUnlockingWithFingerprintAllowed());
    }

    public boolean getUserHasTrust(int userId) {
        return !isTrustDisabled(userId) && mUserHasTrust.get(userId);
    }

    public boolean getUserTrustIsManaged(int userId) {
        return mUserTrustIsManaged.get(userId) && !isTrustDisabled(userId);
    }

    public boolean isUnlockingWithFingerprintAllowed() {
        return mUserHasAuthenticatedSinceBoot;
    }

    static class DisplayClientState {
        public int clientGeneration;
        public boolean clearing;
        public PendingIntent intent;
        public int playbackState;
        public long playbackEventTime;
    }

    private DisplayClientState mDisplayClientState = new DisplayClientState();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                final int status = intent.getIntExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
                final int plugged = intent.getIntExtra(EXTRA_PLUGGED, 0);
                final int level = intent.getIntExtra(EXTRA_LEVEL, 0);
                final int health = intent.getIntExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
                final Message msg = mHandler.obtainMessage(
                        MSG_BATTERY_UPDATE, new BatteryStatus(status, level, plugged, health));
                mHandler.sendMessage(msg);
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                SimData args = SimData.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action " + action
                        + " state: " + intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)
                        + " slotId: " + args.slotId + " subid: " + args.subId);
                }
                mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, args.subId, args.slotId, args.simState)
                        .sendToTarget();
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_AIRPLANE_MODE_CHANGED);
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                dispatchBootCompleted();
            } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (DEBUG) {
                    Log.v(TAG, "action " + action + " serviceState=" + serviceState + " subId="
                            + subId);
                }
                mHandler.sendMessage(
                        mHandler.obtainMessage(MSG_SERVICE_STATE_CHANGE, subId, 0, serviceState));
            }
        }
    };

    private final BroadcastReceiver mBroadcastAllReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_INFO_CHANGED,
                        intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId()), 0));
            } else if (ACTION_FACE_UNLOCK_STARTED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 1,
                        getSendingUserId()));
            } else if (ACTION_FACE_UNLOCK_STOPPED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 0,
                        getSendingUserId()));
            } else if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                    .equals(action)) {
                mHandler.sendEmptyMessage(MSG_DPM_STATE_CHANGED);
            }
        }
    };

    private FingerprintManager.AuthenticationCallback mAuthenticationCallback
            = new AuthenticationCallback() {

        @Override
        public void onAuthenticationFailed() {
            handleFingerprintAuthFailed();
        };

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            handleFingerprintAuthenticated();
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            handleFingerprintHelp(helpMsgId, helpString.toString());
        }

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            handleFingerprintError(errMsgId, errString.toString());
        }

        @Override
        public void onAuthenticationAcquired(int acquireInfo) {
            handleFingerprintAcquired(acquireInfo);
        }
    };
    private CancellationSignal mFingerprintCancelSignal;
    private FingerprintManager mFpm;
    private PowerManager.WakeLock mWakeLock;

    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    private static class SimData {
        public State simState;
        public int slotId;
        public int subId;

        SimData(State state, int slot, int id) {
            simState = state;
            slotId = slot;
            subId = id;
        }

        static SimData fromIntent(Intent intent) {
            State state;
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);

                if (IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state = IccCardConstants.State.PERM_DISABLED;
                } else {
                    state = IccCardConstants.State.ABSENT;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state = IccCardConstants.State.PIN_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state = IccCardConstants.State.PUK_REQUIRED;
                } else {
                    state = IccCardConstants.State.UNKNOWN;
                }
            } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
                state = IccCardConstants.State.NETWORK_LOCKED;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                // This is required because telephony doesn't return to "READY" after
                // these state transitions. See bug 7197471.
                state = IccCardConstants.State.READY;
            } else {
                state = IccCardConstants.State.UNKNOWN;
            }
            return new SimData(state, slotId, subId);
        }

        @Override
        public String toString() {
            return "SimData{state=" + simState + ",slotId=" + slotId + ",subId=" + subId + "}";
        }
    }

    public static class BatteryStatus {
        public final int status;
        public final int level;
        public final int plugged;
        public final int health;
        public BatteryStatus(int status, int level, int plugged, int health) {
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

        /**
         * Determine whether the device is plugged in (USB, power, or wireless).
         * @return true if the device is plugged in.
         */
        public boolean isPluggedIn() {
            return plugged == BatteryManager.BATTERY_PLUGGED_AC
                    || plugged == BatteryManager.BATTERY_PLUGGED_USB
                    || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }

        /**
         * Whether or not the device is charged. Note that some devices never return 100% for
         * battery level, so this allows either battery level or status to determine if the
         * battery is charged.
         * @return true if the device is charged
         */
        public boolean isCharged() {
            return status == BATTERY_STATUS_FULL || level >= 100;
        }

        /**
         * Whether battery is low and needs to be charged.
         * @return true if battery is low
         */
        public boolean isBatteryLow() {
            return level < LOW_BATTERY_THRESHOLD;
        }

    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardUpdateMonitor(context);
        }
        return sInstance;
    }

    protected void handleStartedWakingUp() {
        updateFingerprintListeningState();
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onStartedWakingUp();
            }
        }
    }

    protected void handleFinishedGoingToSleep(int arg1) {
        clearFingerprintRecognized();
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFinishedGoingToSleep(arg1);
            }
        }
        updateFingerprintListeningState();
    }

    /**
     * IMPORTANT: Must be called from UI thread.
     */
    public void dispatchSetBackground(Bitmap bmp) {
        if (DEBUG) Log.d(TAG, "dispatchSetBackground");
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSetBackground(bmp);
            }
        }
    }

    private void handleUserInfoChanged(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserInfoChanged(userId);
            }
        }
    }

    private KeyguardUpdateMonitor(Context context) {
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(context);
        mPowerManager = context.getSystemService(PowerManager.class);
        mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }

        // Take a guess at initial SIM state, battery status and PLMN until we get an update
        mBatteryStatus = new BatteryStatus(BATTERY_STATUS_UNKNOWN, 100, 0, 0);

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        context.registerReceiver(mBroadcastReceiver, filter);

        final IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootCompleteFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mBroadcastReceiver, bootCompleteFilter);

        final IntentFilter allUserFilter = new IntentFilter();
        allUserFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        allUserFilter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STARTED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STOPPED);
        allUserFilter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        context.registerReceiverAsUser(mBroadcastAllReceiver, UserHandle.ALL, allUserFilter,
                null, null);

        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                    new IUserSwitchObserver.Stub() {
                        @Override
                        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHING,
                                    newUserId, 0, reply));
                        }
                        @Override
                        public void onUserSwitchComplete(int newUserId) throws RemoteException {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCH_COMPLETE,
                                    newUserId, 0));
                        }
                        @Override
                        public void onForegroundProfileSwitch(int newProfileId) {
                            // Ignore.
                        }
                    });
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        mTrustManager = (TrustManager) context.getSystemService(Context.TRUST_SERVICE);
        mTrustManager.registerTrustListener(this);

        mFpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        updateFingerprintListeningState();
    }

    private void updateFingerprintListeningState() {
        boolean shouldListenForFingerprint = shouldListenForFingerprint();
        if (mFingerprintDetectionRunning && !shouldListenForFingerprint) {
            stopListeningForFingerprint();
        } else if (!mFingerprintDetectionRunning && shouldListenForFingerprint) {
            startListeningForFingerprint();
        }
    }

    private boolean shouldListenForFingerprint() {
        return mKeyguardIsVisible && !mSwitchingUser &&
                mTrustManager.hasUserAuthenticatedSinceBoot(
                        ActivityManager.getCurrentUser());
    }

    private void startListeningForFingerprint() {
        if (DEBUG) Log.v(TAG, "startListeningForFingerprint()");
        int userId = ActivityManager.getCurrentUser();
        if (isUnlockWithFingerPrintPossible(userId)) {
            mUserHasAuthenticatedSinceBoot = mTrustManager.hasUserAuthenticatedSinceBoot(
                    ActivityManager.getCurrentUser());
            if (mFingerprintCancelSignal != null) {
                mFingerprintCancelSignal.cancel();
            }
            mFingerprintCancelSignal = new CancellationSignal();
            mFpm.authenticate(null, mFingerprintCancelSignal, 0, mAuthenticationCallback, null, userId);
            setFingerprintRunningDetectionRunning(true);
        }
    }

    public boolean isUnlockWithFingerPrintPossible(int userId) {
        return mFpm != null && mFpm.isHardwareDetected() && !isFingerprintDisabled(userId)
                && mFpm.getEnrolledFingerprints(userId).size() > 0;
    }

    private void stopListeningForFingerprint() {
        if (DEBUG) Log.v(TAG, "stopListeningForFingerprint()");
        if (isFingerprintDetectionRunning()) {
            mFingerprintCancelSignal.cancel();
            mFingerprintCancelSignal = null;
        }
        setFingerprintRunningDetectionRunning(false);
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void watchForDeviceProvisioning() {
        mDeviceProvisionedObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
                if (mDeviceProvisioned) {
                    mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
                }
                if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                false, mDeviceProvisionedObserver);

        // prevent a race condition between where we check the flag and where we register the
        // observer by grabbing the value once again...
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != mDeviceProvisioned) {
            mDeviceProvisioned = provisioned;
            if (mDeviceProvisioned) {
                mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }

    /**
     * Handle {@link #MSG_DPM_STATE_CHANGED}
     */
    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDevicePolicyManagerStateChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCHING}
     */
    protected void handleUserSwitching(int userId, IRemoteCallback reply) {
        mSwitchingUser = true;
        updateFingerprintListeningState();

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitching(userId);
            }
        }
        try {
            reply.sendResult(null);
        } catch (RemoteException e) {
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCH_COMPLETE}
     */
    protected void handleUserSwitchComplete(int userId) {
        mSwitchingUser = false;
        updateFingerprintListeningState();

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitchComplete(userId);
            }
        }
    }

    /**
     * This is exposed since {@link Intent#ACTION_BOOT_COMPLETED} is not sticky. If
     * keyguard crashes sometime after boot, then it will never receive this
     * broadcast and hence not handle the event. This method is ultimately called by
     * PhoneWindowManager in this case.
     */
    public void dispatchBootCompleted() {
        mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    /**
     * Handle {@link #MSG_BOOT_COMPLETED}
     */
    protected void handleBootCompleted() {
        if (mBootCompleted) return;
        mBootCompleted = true;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBootCompleted();
            }
        }
    }

    /**
     * We need to store this state in the KeyguardUpdateMonitor since this class will not be
     * destroyed.
     */
    public boolean hasBootCompleted() {
        return mBootCompleted;
    }

    /**
     * Handle {@link #MSG_DEVICE_PROVISIONED}
     */
    protected void handleDeviceProvisioned() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDeviceProvisioned();
            }
        }
        if (mDeviceProvisionedObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mDeviceProvisionedObserver);
            mDeviceProvisionedObserver = null;
        }
    }

    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     */
    protected void handlePhoneStateChanged(String newState) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_RINGING;
        }
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPhoneStateChanged(mPhoneState);
            }
        }
    }

    /**
     * Handle {@link #MSG_RINGER_MODE_CHANGED}
     */
    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRingerModeChanged(mode);
            }
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(BatteryStatus status) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        final boolean batteryUpdateInteresting = isBatteryUpdateInteresting(mBatteryStatus, status);
        mBatteryStatus = status;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onRefreshBatteryInfo(status);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(int subId, int slotId, State state) {

        if (DEBUG_SIM_STATES) {
            Log.d(TAG, "handleSimStateChange(subId=" + subId + ", slotId="
                    + slotId + ", state=" + state +")");
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "invalid subId in handleSimStateChange()");
            return;
        }

        SimData data = mSimDatas.get(subId);
        final boolean changed;
        if (data == null) {
            data = new SimData(state, slotId, subId);
            mSimDatas.put(subId, data);
            changed = true; // no data yet; force update
        } else {
            changed = (data.simState != state || data.subId != subId || data.slotId != slotId);
            data.simState = state;
            data.subId = subId;
            data.slotId = slotId;
        }
        if (changed && state != State.UNKNOWN) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onSimStateChanged(subId, slotId, state);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_SERVICE_STATE_CHANGE}
     */
    private void handleServiceStateChange(int subId, ServiceState serviceState) {
        if (DEBUG) {
            Log.d(TAG,
                    "handleServiceStateChange(subId=" + subId + ", serviceState=" + serviceState);
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "invalid subId in handleServiceStateChange()");
            return;
        }

        mServiceStates.put(subId, serviceState);

        for (int j = 0; j < mCallbacks.size(); j++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_VISIBILITY_CHANGED}
     */
    private void handleKeyguardVisibilityChanged(int showing) {
        if (DEBUG) Log.d(TAG, "handleKeyguardVisibilityChanged(" + showing + ")");
        boolean isShowing = (showing == 1);
        mKeyguardIsVisible = isShowing;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardVisibilityChangedRaw(isShowing);
            }
        }
        updateFingerprintListeningState();
    }

    /**
     * Handle {@link #MSG_KEYGUARD_RESET}
     */
    private void handleKeyguardReset() {
        if (DEBUG) Log.d(TAG, "handleKeyguardReset");
        if (!isUnlockingWithFingerprintAllowed()) {
            updateFingerprintListeningState();
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_BOUNCER_CHANGED}
     * @see #sendKeyguardBouncerChanged(boolean)
     */
    private void handleKeyguardBouncerChanged(int bouncer) {
        if (DEBUG) Log.d(TAG, "handleKeyguardBouncerChanged(" + bouncer + ")");
        boolean isBouncer = (bouncer == 1);
        mBouncer = isBouncer;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardBouncerChanged(isBouncer);
            }
        }
    }

    /**
     * Handle {@link #MSG_REPORT_EMERGENCY_CALL_ACTION}
     */
    private void handleReportEmergencyCallAction() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onEmergencyCallAction();
            }
        }
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current) {
        final boolean nowPluggedIn = current.isPluggedIn();
        final boolean wasPluggedIn = old.isPluggedIn();
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && nowPluggedIn == true
            && (old.status != current.status);

        // change in plug state is always interesting
        if (wasPluggedIn != nowPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        if (nowPluggedIn && old.level != current.level) {
            return true;
        }

        // change where battery needs charging
        if (!nowPluggedIn && current.isBatteryLow() && current.level != old.level) {
            return true;
        }
        return false;
    }

    /**
     * Remove the given observer's callback.
     *
     * @param callback The callback to remove
     */
    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** unregister callback for " + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback to register
     */
    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** register callback for " + callback);
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                if (DEBUG) Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<KeyguardUpdateMonitorCallback>(callback));
        removeCallback(null); // remove unused references
        sendUpdates(callback);
    }

    private void sendUpdates(KeyguardUpdateMonitorCallback callback) {
        // Notify listener of the current state
        callback.onRefreshBatteryInfo(mBatteryStatus);
        callback.onTimeChanged();
        callback.onRingerModeChanged(mRingMode);
        callback.onPhoneStateChanged(mPhoneState);
        callback.onRefreshCarrierInfo();
        callback.onClockVisibilityChanged();
        for (Entry<Integer, SimData> data : mSimDatas.entrySet()) {
            final SimData state = data.getValue();
            callback.onSimStateChanged(state.subId, state.slotId, state.simState);
        }
    }

    public void sendKeyguardVisibilityChanged(boolean showing) {
        if (DEBUG) Log.d(TAG, "sendKeyguardVisibilityChanged(" + showing + ")");
        Message message = mHandler.obtainMessage(MSG_KEYGUARD_VISIBILITY_CHANGED);
        message.arg1 = showing ? 1 : 0;
        message.sendToTarget();
    }

    public void sendKeyguardReset() {
        mHandler.obtainMessage(MSG_KEYGUARD_RESET).sendToTarget();
    }

    /**
     * @see #handleKeyguardBouncerChanged(int)
     */
    public void sendKeyguardBouncerChanged(boolean showingBouncer) {
        if (DEBUG) Log.d(TAG, "sendKeyguardBouncerChanged(" + showingBouncer + ")");
        Message message = mHandler.obtainMessage(MSG_KEYGUARD_BOUNCER_CHANGED);
        message.arg1 = showingBouncer ? 1 : 0;
        message.sendToTarget();
    }

    /**
     * Report that the user successfully entered the SIM PIN or PUK/SIM PIN so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     *
     * NOTE: Because handleSimStateChange() invokes callbacks immediately without going
     * through mHandler, this *must* be called from the UI thread.
     */
    public void reportSimUnlocked(int subId) {
        if (DEBUG_SIM_STATES) Log.v(TAG, "reportSimUnlocked(subId=" + subId + ")");
        int slotId = SubscriptionManager.getSlotId(subId);
        handleSimStateChange(subId, slotId, State.READY);
    }

    /**
     * Report that the emergency call button has been pressed and the emergency dialer is
     * about to be displayed.
     *
     * @param bypassHandler runs immediately.
     *
     * NOTE: Must be called from UI thread if bypassHandler == true.
     */
    public void reportEmergencyCallAction(boolean bypassHandler) {
        if (!bypassHandler) {
            mHandler.obtainMessage(MSG_REPORT_EMERGENCY_CALL_ACTION).sendToTarget();
        } else {
            handleReportEmergencyCallAction();
        }
    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public void clearFailedUnlockAttempts() {
        mFailedAttempts.delete(sCurrentUser);
    }

    public int getFailedUnlockAttempts() {
        return mFailedAttempts.get(sCurrentUser, 0);
    }

    public void reportFailedUnlockAttempt() {
        mFailedAttempts.put(sCurrentUser, getFailedUnlockAttempts() + 1);
    }

    public void clearFingerprintRecognized() {
        mUserFingerprintAuthenticated.clear();
    }

    public boolean isSimPinVoiceSecure() {
        // TODO: only count SIMs that handle voice
        return isSimPinSecure();
    }

    public boolean isSimPinSecure() {
        // True if any SIM is pin secure
        for (SubscriptionInfo info : getSubscriptionInfo(false /* forceReload */)) {
            if (isSimPinSecure(getSimState(info.getSubscriptionId()))) return true;
        }
        return false;
    }

    public State getSimState(int subId) {
        if (mSimDatas.containsKey(subId)) {
            return mSimDatas.get(subId).simState;
        } else {
            return State.UNKNOWN;
        }
    }

    /**
     * @return true if and only if the state has changed for the specified {@code slotId}
     */
    private boolean refreshSimState(int subId, int slotId) {

        // This is awful. It exists because there are two APIs for getting the SIM status
        // that don't return the complete set of values and have different types. In Keyguard we
        // need IccCardConstants, but TelephonyManager would only give us
        // TelephonyManager.SIM_STATE*, so we retrieve it manually.
        final TelephonyManager tele = TelephonyManager.from(mContext);
        int simState =  tele.getSimState(slotId);
        State state;
        try {
            state = State.intToState(simState);
        } catch(IllegalArgumentException ex) {
            Log.w(TAG, "Unknown sim state: " + simState);
            state = State.UNKNOWN;
        }
        SimData data = mSimDatas.get(subId);
        final boolean changed;
        if (data == null) {
            data = new SimData(state, slotId, subId);
            mSimDatas.put(subId, data);
            changed = true; // no data yet; force update
        } else {
            changed = data.simState != state;
            data.simState = state;
        }
        return changed;
    }

    public static boolean isSimPinSecure(IccCardConstants.State state) {
        final IccCardConstants.State simState = state;
        return (simState == IccCardConstants.State.PIN_REQUIRED
                || simState == IccCardConstants.State.PUK_REQUIRED
                || simState == IccCardConstants.State.PERM_DISABLED);
    }

    public DisplayClientState getCachedDisplayClientState() {
        return mDisplayClientState;
    }

    // TODO: use these callbacks elsewhere in place of the existing notifyScreen*()
    // (KeyguardViewMediator, KeyguardHostView)
    public void dispatchStartedWakingUp() {
        synchronized (this) {
            mDeviceInteractive = true;
        }
        mHandler.sendEmptyMessage(MSG_STARTED_WAKING_UP);
    }

    public void dispatchFinishedGoingToSleep(int why) {
        synchronized(this) {
            mDeviceInteractive = false;
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FINISHED_GOING_TO_SLEEP, why, 0));
    }

    public void dispatchScreenTurnedOn() {
        synchronized (this) {
            mScreenOn = true;
        }
    }

    public void dispatchScreenTurnedOff() {
        synchronized(this) {
            mScreenOn = false;
        }
    }

    public boolean isDeviceInteractive() {
        return mDeviceInteractive;
    }

    /**
     * Find the next SubscriptionId for a SIM in the given state, favoring lower slot numbers first.
     * @param state
     * @return subid or {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} if none found
     */
    public int getNextSubIdForState(State state) {
        List<SubscriptionInfo> list = getSubscriptionInfo(false /* forceReload */);
        int resultId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int bestSlotId = Integer.MAX_VALUE; // Favor lowest slot first
        for (int i = 0; i < list.size(); i++) {
            final SubscriptionInfo info = list.get(i);
            final int id = info.getSubscriptionId();
            int slotId = SubscriptionManager.getSlotId(id);
            if (state == getSimState(id) && bestSlotId > slotId ) {
                resultId = id;
                bestSlotId = slotId;
            }
        }
        return resultId;
    }

    public SubscriptionInfo getSubscriptionInfoForSubId(int subId) {
        List<SubscriptionInfo> list = getSubscriptionInfo(false /* forceReload */);
        for (int i = 0; i < list.size(); i++) {
            SubscriptionInfo info = list.get(i);
            if (subId == info.getSubscriptionId()) return info;
        }
        return null; // not found
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardUpdateMonitor state:");
        pw.println("  SIM States:");
        for (SimData data : mSimDatas.values()) {
            pw.println("    " + data.toString());
        }
        pw.println("  Subs:");
        if (mSubscriptionInfo != null) {
            for (int i = 0; i < mSubscriptionInfo.size(); i++) {
                pw.println("    " + mSubscriptionInfo.get(i));
            }
        }
        pw.println("  Service states:");
        for (int subId : mServiceStates.keySet()) {
            pw.println("    " + subId + "=" + mServiceStates.get(subId));
        }
    }
}
