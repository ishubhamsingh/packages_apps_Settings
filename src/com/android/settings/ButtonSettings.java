/*
 * Copyright (C) 2013 The CyanogenMod project
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

package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.cyanogenmod.ButtonBacklightBrightness;

public class ButtonSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SystemSettings";

    private static final String KEY_BUTTON_BACKLIGHT = "button_backlight";
    private static final String KEY_HOME_LONG_PRESS = "hardware_keys_home_long_press";
    private static final String KEY_HOME_DOUBLE_TAP = "hardware_keys_home_double_tap";
    private static final String KEY_MENU_PRESS = "hardware_keys_menu_press";
    private static final String KEY_MENU_LONG_PRESS = "hardware_keys_menu_long_press";
    private static final String KEY_APP_SWITCH_PRESS = "hardware_keys_app_switch_press";
    private static final String KEY_APP_SWITCH_LONG_PRESS = "hardware_keys_app_switch_long_press";
    private static final String KEY_VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";
    private static final String DISABLE_NAV_KEYS = "disable_nav_keys";

    private static final String CATEGORY_POWER = "power_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_MENU = "menu_key";
    private static final String CATEGORY_ASSIST = "assist_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_CAMERA = "camera_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_BACKLIGHT = "key_backlight";
    private static final String CATEGORY_NAVBAR = "navigation_bar";

    // Available custom actions to perform on a key press.
    // Must match values for KEY_HOME_LONG_PRESS_ACTION in:
    // frameworks/base/core/java/android/provider/Settings.java
    private static final int ACTION_NOTHING = 0;
    private static final int ACTION_MENU = 1;
    private static final int ACTION_APP_SWITCH = 2;
    private static final int ACTION_SEARCH = 3;
    private static final int ACTION_VOICE_SEARCH = 4;
    private static final int ACTION_IN_APP_SEARCH = 5;
    private static final int ACTION_LAUNCH_CAMERA = 6;
    private static final int ACTION_LAST_APP = 7;
    private static final int ACTION_SLEEP = 8;

    // Masks for checking presence of hardware keys.
    // Must match values in frameworks/base/core/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;

    private ListPreference mHomeLongPressAction;
    private ListPreference mHomeDoubleTapAction;
    private ListPreference mMenuPressAction;
    private ListPreference mMenuLongPressAction;
    private ListPreference mAppSwitchPressAction;
    private ListPreference mAppSwitchLongPressAction;
    private ListPreference mVolumeKeyCursorControl;

    private PreferenceCategory mNavigationPreferencesCat;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.button_settings);

        final Resources res = getResources();
        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final int deviceKeys = getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);

        final boolean hasPowerKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER);
        final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;
        final boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
        final boolean hasAssistKey = (deviceKeys & KEY_MASK_ASSIST) != 0;
        final boolean hasAppSwitchKey = (deviceKeys & KEY_MASK_APP_SWITCH) != 0;

        boolean hasAnyBindableKey = false;
        final PreferenceCategory homeCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_HOME);
        final PreferenceCategory menuCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_MENU);
        final PreferenceCategory appSwitchCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_APPSWITCH);
        final PreferenceCategory volumeCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_VOLUME);

        mHandler = new Handler();

        if (hasHomeKey) {
            int defaultLongPressAction = res.getInteger(
                    com.android.internal.R.integer.config_longPressOnHomeBehavior);
            if (defaultLongPressAction < ACTION_NOTHING ||
                    defaultLongPressAction > ACTION_IN_APP_SEARCH) {
                defaultLongPressAction = ACTION_NOTHING;
            }

            int defaultDoubleTapAction = res.getInteger(
                    com.android.internal.R.integer.config_doubleTapOnHomeBehavior);
            if (defaultDoubleTapAction < ACTION_NOTHING ||
                    defaultDoubleTapAction > ACTION_IN_APP_SEARCH) {
                defaultDoubleTapAction = ACTION_NOTHING;
            }

            int longPressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_HOME_LONG_PRESS_ACTION,
                    defaultLongPressAction);
            mHomeLongPressAction = initActionList(KEY_HOME_LONG_PRESS, longPressAction);

            int doubleTapAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                    defaultDoubleTapAction);
            mHomeDoubleTapAction = initActionList(KEY_HOME_DOUBLE_TAP, doubleTapAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(homeCategory);
        }

        if (hasMenuKey) {
            int pressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_MENU_ACTION, ACTION_MENU);
            mMenuPressAction = initActionList(KEY_MENU_PRESS, pressAction);

            int longPressAction = Settings.System.getInt(resolver,
                        Settings.System.KEY_MENU_LONG_PRESS_ACTION,
                        hasAssistKey ? ACTION_NOTHING : ACTION_SEARCH);
            mMenuLongPressAction = initActionList(KEY_MENU_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(menuCategory);
        }

        final ButtonBacklightBrightness backlight =
                (ButtonBacklightBrightness) findPreference(KEY_BUTTON_BACKLIGHT);
       	if(backlight!=null) {
		    if (!backlight.isButtonSupported() && !backlight.isKeyboardSupported()) {
		        prefScreen.removePreference(backlight);
		    }
		 }

        if (hasAppSwitchKey) {
            int pressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_APP_SWITCH_ACTION, ACTION_APP_SWITCH);
            mAppSwitchPressAction = initActionList(KEY_APP_SWITCH_PRESS, pressAction);

            int longPressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION, ACTION_NOTHING);
            mAppSwitchLongPressAction = initActionList(KEY_APP_SWITCH_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(appSwitchCategory);
        }
 
        if (Utils.hasVolumeRocker(getActivity())) {
            int cursorControlAction = Settings.System.getInt(resolver,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0);
            mVolumeKeyCursorControl = initActionList(KEY_VOLUME_KEY_CURSOR_CONTROL,
                    cursorControlAction);
        } else {
            prefScreen.removePreference(volumeCategory);
        }
    }

    private ListPreference initActionList(String key, int value) {
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        list.setValue(Integer.toString(value));
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private void handleActionListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);

        pref.setSummary(pref.getEntries()[index]);
        Settings.System.putInt(getContentResolver(), setting, Integer.valueOf(value));
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHomeLongPressAction) {
            handleActionListChange(mHomeLongPressAction, newValue,
                    Settings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mHomeDoubleTapAction) {
            handleActionListChange(mHomeDoubleTapAction, newValue,
                    Settings.System.KEY_HOME_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mMenuPressAction) {
            handleActionListChange(mMenuPressAction, newValue,
                    Settings.System.KEY_MENU_ACTION);
            return true;
        } else if (preference == mMenuLongPressAction) {
            handleActionListChange(mMenuLongPressAction, newValue,
                    Settings.System.KEY_MENU_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAppSwitchPressAction) {
            handleActionListChange(mAppSwitchPressAction, newValue,
                    Settings.System.KEY_APP_SWITCH_ACTION);
            return true;
        } else if (preference == mAppSwitchLongPressAction) {
            handleActionListChange(mAppSwitchLongPressAction, newValue,
                    Settings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mVolumeKeyCursorControl) {
            handleActionListChange(mVolumeKeyCursorControl, newValue,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL);
            return true;
        }
        return false;
    }
}
