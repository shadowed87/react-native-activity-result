// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.util.SparseArray;
import android.content.pm.PackageManager;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javax.annotation.Nullable;

public class ActivityResultModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    final SparseArray<Promise> mPromises;

    public ActivityResultModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mPromises = new SparseArray<>();
    }

    @Override
    public String getName() {
        return "ActivityResult";
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<>();
        constants.put("OK", Activity.RESULT_OK);
        constants.put("CANCELED", Activity.RESULT_CANCELED);
        return constants;
    }

    @Override
    public void initialize() {
        super.initialize();
        getReactApplicationContext().addActivityEventListener(this);
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        getReactApplicationContext().removeActivityEventListener(this);
    }

    @ReactMethod
    public void openApp(String packageName, String activityName, ReadableMap data, Promise promise) {
        ReactApplicationContext context = getReactApplicationContext();
        PackageManager manager = context.getPackageManager();
        try {
            Intent intent = manager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                promise.resolve(false);
            }
            intent.setComponent(new ComponentName(packageName, activityName));
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.putExtras(Arguments.toBundle(data));
            context.startActivity(intent);
            promise.resolve(true);
        } catch (android.content.ActivityNotFoundException e) {
            promise.resolve(false);
        }
    }


    @ReactMethod
    public void startActivity(String action, ReadableMap data) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        Intent intent = new Intent(action);
        intent.putExtras(Arguments.toBundle(data));
        activity.startActivity(intent);
    }

    @ReactMethod
    public void startActivityWithPackageName(String packageName, String activityName, String data) {
        ComponentName component = new ComponentName(packageName, activityName);
        Activity activity = getReactApplicationContext().getCurrentActivity();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.putExtra("data", data);
        intent.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK |  Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @ReactMethod
    public void isIntentAvailable(String packageName, Promise promise) {
        PackageManager pm = getReactApplicationContext().getPackageManager();
        try {
            pm.getPackageInfo(packageName, 0);
            WritableMap map = new WritableNativeMap();
            map.putString("result", "true");
            promise.resolve(map);
        } catch (PackageManager.NameNotFoundException e) {
            promise.resolve(null);
            return;
        }
    } 

    @ReactMethod
    public void startActivityForResult(int requestCode, String action, ReadableMap data, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        Intent intent = new Intent(action);
        intent.putExtras(Arguments.toBundle(data));
        activity.startActivityForResult(intent, requestCode);
        mPromises.put(requestCode, promise);
    }

    @ReactMethod
    public void resolveActivity(String action, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        Intent intent = new Intent(action);
        ComponentName componentName = intent.resolveActivity(activity.getPackageManager());
        if (componentName == null) {
            promise.resolve(null);
            return;
        }

        WritableMap map = new WritableNativeMap();
        map.putString("class", componentName.getClassName());
        map.putString("package", componentName.getPackageName());
        promise.resolve(map);
    }

    @ReactMethod
    public void finish(int result, String action, ReadableMap map) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        Intent intent = new Intent(action);
        intent.putExtras(Arguments.toBundle(map));
        activity.setResult(result, intent);

        // this way clear instance in "recent app" after calling this function
        // https://github.com/noitq/react-native-exit-app/blob/master/android/src/main/java/com/github/wumke/RNExitApp/RNExitAppModule.java#L40
        if(android.os.Build.VERSION.SDK_INT >= 21) {
            activity.finishAndRemoveTask();
        } else {
            activity.finish();
        }
        // this way remain instance in "recent app" after calling this function
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        Promise promise = mPromises.get(requestCode);
        if (promise != null) {
            WritableMap result = new WritableNativeMap();
            result.putInt("resultCode", resultCode);
            result.putMap("data", Arguments.makeNativeMap(data.getExtras()));
            promise.resolve(result);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        /* Do nothing */
    }
}
