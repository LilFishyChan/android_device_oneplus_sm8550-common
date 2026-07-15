/*
 * Copyright (C) 2018-2023 crDroid Android Project
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

package org.uwuaosp.device.DeviceSettings;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.os.FileObserver;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import androidx.annotation.Keep;

import com.android.internal.os.DeviceKeyHandler;

import java.util.Arrays;

import org.uwuaosp.device.DeviceSettings.Constants;
import org.uwuaosp.device.DeviceSettings.SliderControllerBase;
import org.uwuaosp.device.DeviceSettings.slider.NotificationController;
import org.uwuaosp.device.DeviceSettings.slider.FlashlightController;
import org.uwuaosp.device.DeviceSettings.slider.BrightnessController;
import org.uwuaosp.device.DeviceSettings.slider.RotationController;
import org.uwuaosp.device.DeviceSettings.slider.RingerController;
import org.uwuaosp.device.DeviceSettings.slider.NotificationRingerController;

@Keep
public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final NotificationController mNotificationController;
    private final FlashlightController mFlashlightController;
    private final BrightnessController mBrightnessController;
    private final RotationController mRotationController;
    private final RingerController mRingerController;
    private final NotificationRingerController mNotificationRingerController;

    private SliderControllerBase mSliderController;

    private final InputManager mInputManager;

    private final BroadcastReceiver mSliderUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int usage = intent.getIntExtra(Constants.EXTRA_SLIDER_USAGE, 0);
            int[] actions = intent.getIntArrayExtra(Constants.EXTRA_SLIDER_ACTIONS);

            Log.d(TAG, "update usage " + usage + " with actions " +
                    Arrays.toString(actions));

            if (mSliderController != null) {
                mSliderController.reset();
            }

            switch (usage) {
                case NotificationController.ID:
                    mSliderController = mNotificationController;
                    mSliderController.update(actions);
                    break;
                case FlashlightController.ID:
                    mSliderController = mFlashlightController;
                    mSliderController.update(actions);
                    break;
                case BrightnessController.ID:
                    mSliderController = mBrightnessController;
                    mSliderController.update(actions);
                    break;
                case RotationController.ID:
                    mSliderController = mRotationController;
                    mSliderController.update(actions);
                    break;
                case RingerController.ID:
                    mSliderController = mRingerController;
                    mSliderController.update(actions);
                    break;
                case NotificationRingerController.ID:
                    mSliderController = mNotificationRingerController;
                    mSliderController.update(actions);
                    break;
            }

            mSliderController.restoreState(context, false);
        }
    };

    public KeyHandler(Context context) {
        mContext = context;

        mNotificationController = new NotificationController(mContext);
        mFlashlightController = new FlashlightController(mContext);
        mBrightnessController = new BrightnessController(mContext);
        mRotationController = new RotationController(mContext);
        mRingerController = new RingerController(mContext);
        mNotificationRingerController = new NotificationRingerController(mContext);

        mContext.registerReceiver(mSliderUpdateReceiver,
                new IntentFilter(Constants.ACTION_UPDATE_SLIDER_SETTINGS));

        mInputManager = mContext.getSystemService(InputManager.class);

        mTriStateObserver = new FileObserver(Constants.SLIDER_STATE, FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if (mSliderController != null) {
                    mSliderController.processEvent(mContext);
                }
            }
        };
        mTriStateObserver.startWatching();

        restoreSliderState();
    }

    private void restoreSliderState() {
        try {
            Context deviceContext = mContext.createPackageContext(
                    "org.uwuaosp.device.DeviceSettings", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences prefs = deviceContext.getSharedPreferences(
                    deviceContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            Resources res = deviceContext.getResources();

            String usage = prefs.getString(Constants.NOTIF_SLIDER_USAGE_KEY,
                    res.getString(R.string.config_defaultNotificationSliderUsage));

            int usageInt = Integer.parseInt(usage);
            int defaultsResId = getDefaultSliderActionsResId(usageInt);
            if (defaultsResId == 0) return;

            String[] defaults = res.getStringArray(defaultsResId);
            int[] actions = new int[]{
                Integer.parseInt(prefs.getString(
                    Constants.NOTIF_SLIDER_ACTION_TOP_KEY, defaults[0])),
                Integer.parseInt(prefs.getString(
                    Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, defaults[1])),
                Integer.parseInt(prefs.getString(
                    Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, defaults[2]))
            };

            switch (usageInt) {
                case NotificationController.ID:
                    mSliderController = mNotificationController; break;
                case FlashlightController.ID:
                    mSliderController = mFlashlightController; break;
                case BrightnessController.ID:
                    mSliderController = mBrightnessController; break;
                case RotationController.ID:
                    mSliderController = mRotationController; break;
                case RingerController.ID:
                    mSliderController = mRingerController; break;
                case NotificationRingerController.ID:
                    mSliderController = mNotificationRingerController; break;
            }

            if (mSliderController != null) {
                mSliderController.update(actions);
                mSliderController.restoreState(mContext, false);
            }
        } catch (NameNotFoundException e) {
            Log.w(TAG, "DeviceSettings package not found, slider state not restored", e);
        }
    }

    private static int getDefaultSliderActionsResId(int usage) {
        switch (usage) {
            case NotificationController.ID:
                return R.array.config_defaultSliderActionsForNotification;
            case FlashlightController.ID:
                return R.array.config_defaultSliderActionsForFlashlight;
            case BrightnessController.ID:
                return R.array.config_defaultSliderActionsForBrightness;
            case RotationController.ID:
                return R.array.config_defaultSliderActionsForRotation;
            case RingerController.ID:
                return R.array.config_defaultSliderActionsForRinger;
            case NotificationRingerController.ID:
                return R.array.config_defaultSliderActionsForNotificationRinger;
            default:
                return 0;
        }
    }

    private FileObserver mTriStateObserver;

    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return event;
        }

        if (!mInputManager.getInputDevice(event.getDeviceId()).getName().equals("oplus,hall_tri_state_key")) {
            return event;
        }

        mSliderController.processEvent(mContext);

        return null;
    }
}
