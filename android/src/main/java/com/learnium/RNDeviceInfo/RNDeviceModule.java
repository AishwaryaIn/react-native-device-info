package com.learnium.RNDeviceInfo;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.BatteryManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.webkit.WebSettings;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.app.ActivityManager;
import android.util.DisplayMetrics;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.CallbackImpl;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.lang.Runtime;
import java.net.NetworkInterface;

import javax.annotation.Nullable;

import static android.app.Activity.RESULT_OK;

public class RNDeviceModule extends ReactContextBaseJavaModule {
    private ReactInstanceManager mReactInstanceManager;

    ReactApplicationContext reactContext;

    WifiInfo wifiInfo;
    //**************************** Device auth *******************************//
    private static final int LOCK_REQUEST_CODE = 221;
    private static final int SECURITY_SETTING_REQUEST_CODE = 233;

    private Promise mPickerPromise;
    private Context context;

    public RNDeviceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "RNDeviceInfo";
    }

    private WifiInfo getWifiInfo() {
        if (this.wifiInfo == null) {
            WifiManager manager = (WifiManager) reactContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            this.wifiInfo = manager.getConnectionInfo();
        }
        return this.wifiInfo;
    }

    private String getCurrentLanguage() {
        Locale current;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            current = getReactApplicationContext().getResources().getConfiguration().getLocales().get(0);
        } else {
            current = getReactApplicationContext().getResources().getConfiguration().locale;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return current.toLanguageTag();
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(current.getLanguage());
            if (current.getCountry() != null) {
                builder.append("-");
                builder.append(current.getCountry());
            }
            return builder.toString();
        }
    }

    private String getCurrentCountry() {
        Locale current;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            current = getReactApplicationContext().getResources().getConfiguration().getLocales().get(0);
        } else {
            current = getReactApplicationContext().getResources().getConfiguration().locale;
        }

        return current.getCountry();
    }

    private Boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private Boolean isTablet() {
        int layout = getReactApplicationContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (layout != Configuration.SCREENLAYOUT_SIZE_LARGE && layout != Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            return false;
        }

        final DisplayMetrics metrics = getReactApplicationContext().getResources().getDisplayMetrics();
        if (metrics.densityDpi == DisplayMetrics.DENSITY_DEFAULT
                || metrics.densityDpi == DisplayMetrics.DENSITY_HIGH
                || metrics.densityDpi == DisplayMetrics.DENSITY_MEDIUM
                || metrics.densityDpi == DisplayMetrics.DENSITY_TV
                || metrics.densityDpi == DisplayMetrics.DENSITY_XHIGH) {
            return true;
        }
        return false;
    }

    private float fontScale() {
        return getReactApplicationContext().getResources().getConfiguration().fontScale;
    }

    private Boolean is24Hour() {
        return android.text.format.DateFormat.is24HourFormat(this.reactContext.getApplicationContext());
    }

    @ReactMethod
    public void isPinOrFingerprintSet(Callback callback) {
        KeyguardManager keyguardManager = (KeyguardManager) this.reactContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE); //api 16+
        Log.d("callback", "authCallback is ** null" + keyguardManager.isKeyguardLocked() + keyguardManager.isKeyguardSecure() + keyguardManager.inKeyguardRestrictedInputMode());
        callback.invoke(keyguardManager.isKeyguardSecure());
    }

    //**************************** Device auth *******************************//
    @ReactMethod
    public void getAuthonticationConfirmed(Promise promise) {
        final Activity currentActivity = reactContext.getCurrentActivity();

        mPickerPromise = promise;
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE); //api 16+
        if (currentActivity == null) {
            mPickerPromise.reject("Activity doesn't exist");
            return;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Intent i = keyguardManager.createConfirmDeviceCredentialIntent("Unlock", "Confirm your screen lock PIN,Pattern or Password");
                    //Start activity for result
                    currentActivity.startActivityForResult(i, LOCK_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    try {
                        //Start activity for result
                        currentActivity.startActivityForResult(intent, SECURITY_SETTING_REQUEST_CODE);
                    } catch (Exception ex) {
                        mPickerPromise.reject("Unable to find screen lock. Go to Security settings then set screen lock manually");
                    }
                }
            } else {
                mPickerPromise.reject("Device authentication doesn't support");
                mPickerPromise = null;
            }
        }
    }

    //**************************** Device auth *******************************//

    @ReactMethod
    public void getIpAddress(Promise p) {
        String ipAddress = Formatter.formatIpAddress(getWifiInfo().getIpAddress());
        p.resolve(ipAddress);
    }

    @ReactMethod
    public void getMacAddress(Promise p) {
        String macAddress = getWifiInfo().getMacAddress();

        String permission = "android.permission.INTERNET";
        int res = reactContext.checkCallingOrSelfPermission(permission);

        if (res == PackageManager.PERMISSION_GRANTED) {
            try {
                List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface nif : all) {
                    if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) {
                        macAddress = "";
                    } else {

                        StringBuilder res1 = new StringBuilder();
                        for (byte b : macBytes) {
                            res1.append(String.format("%02X:", b));
                        }

                        if (res1.length() > 0) {
                            res1.deleteCharAt(res1.length() - 1);
                        }

                        macAddress = res1.toString();
                    }
                }
            } catch (Exception ex) {
            }
        }

        p.resolve(macAddress);
    }

    @ReactMethod
    public String getCarrier() {
        TelephonyManager telMgr = (TelephonyManager) reactContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telMgr.getNetworkOperatorName();
    }

    @ReactMethod
    public Integer getTotalDiskCapacity() {
        try {
            StatFs root = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            return root.getBlockCount() * root.getBlockSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @ReactMethod
    public Integer getFreeDiskStorage() {
        try {
            StatFs external = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            return external.getAvailableBlocks() * external.getBlockSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @ReactMethod
    public void getBatteryLevel(Promise p) {
        Intent batteryIntent = reactContext.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryLevel = level / (float) scale;
        p.resolve(batteryLevel);
    }

    public String getInstallReferrer() {
        SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences("react-native-device-info", Context.MODE_PRIVATE);
        return sharedPref.getString("installReferrer", null);
    }

    @Override
    public @Nullable
    Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<String, Object>();

        PackageManager packageManager = reactContext.getPackageManager();
        String packageName = reactContext.getPackageName();

        constants.put("appVersion", "not available");
        constants.put("appName", "not available");
        constants.put("buildVersion", "not available");
        constants.put("buildNumber", 0);

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            PackageInfo info = packageManager.getPackageInfo(packageName, 0);
            String applicationName = reactContext.getApplicationInfo().loadLabel(reactContext.getPackageManager()).toString();
            constants.put("appVersion", info.versionName);
            constants.put("buildNumber", info.versionCode);
            constants.put("firstInstallTime", info.firstInstallTime);
            constants.put("lastUpdateTime", info.lastUpdateTime);
            constants.put("appName", applicationName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String deviceName = "Unknown";

        String permission = "android.permission.BLUETOOTH";
        int res = reactContext.checkCallingOrSelfPermission(permission);
        if (res == PackageManager.PERMISSION_GRANTED) {
            try {
                BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
                if (myDevice != null) {
                    deviceName = myDevice.getName();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        try {
            if (Class.forName("com.google.android.gms.iid.InstanceID") != null) {
                constants.put("instanceId", com.google.android.gms.iid.InstanceID.getInstance(reactContext).getId());
            }
        } catch (ClassNotFoundException e) {
            constants.put("instanceId", "N/A: Add com.google.android.gms:play-services-gcm to your project.");
        }
        constants.put("serialNumber", Build.SERIAL);
        constants.put("deviceName", deviceName);
        constants.put("systemName", "Android");
        constants.put("systemVersion", Build.VERSION.RELEASE);
        constants.put("model", Build.MODEL);
        constants.put("brand", Build.BRAND);
        constants.put("deviceId", Build.BOARD);
        constants.put("apiLevel", Build.VERSION.SDK_INT);
        constants.put("deviceLocale", this.getCurrentLanguage());
        constants.put("deviceCountry", this.getCurrentCountry());
        constants.put("uniqueId", Secure.getString(reactContext.getContentResolver(), Secure.ANDROID_ID));
        constants.put("systemManufacturer", Build.MANUFACTURER);
        constants.put("bundleId", packageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                constants.put("userAgent", WebSettings.getDefaultUserAgent(reactContext));
            } catch (RuntimeException e) {
                constants.put("userAgent", System.getProperty("http.agent"));
            }
        }
        constants.put("timezone", TimeZone.getDefault().getID());
        constants.put("isEmulator", this.isEmulator());
        constants.put("isTablet", this.isTablet());
        constants.put("fontScale", this.fontScale());
        constants.put("is24Hour", this.is24Hour());
        if (getCurrentActivity() != null &&
                (getCurrentActivity().checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                        getCurrentActivity().checkCallingOrSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
                        getCurrentActivity().checkCallingOrSelfPermission("android.permission.READ_PHONE_NUMBERS") == PackageManager.PERMISSION_GRANTED)) {
            TelephonyManager telMgr = (TelephonyManager) reactContext.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            constants.put("phoneNumber", telMgr.getLine1Number());
        }
        constants.put("carrier", this.getCarrier());
        constants.put("totalDiskCapacity", this.getTotalDiskCapacity());
        constants.put("freeDiskStorage", this.getFreeDiskStorage());
        constants.put("installReferrer", this.getInstallReferrer());

        Runtime rt = Runtime.getRuntime();
        constants.put("maxMemory", rt.maxMemory());
        ActivityManager actMgr = (ActivityManager) this.reactContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actMgr.getMemoryInfo(memInfo);
        constants.put("totalMemory", memInfo.totalMem);

        return constants;
    }
}