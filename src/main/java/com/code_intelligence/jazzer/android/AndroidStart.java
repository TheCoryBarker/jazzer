package com.code_intelligence.jazzer.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;

public class AndroidStart extends Service {
  private static final String TAG = "FuzzService";

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // get String extra from the intent that started this service
    String targetClass = intent.getStringExtra("target_class");

    if (targetClass == null) {
        Log.e(TAG, "No target_class provided in Intent extras!");
        stopSelf();
        return START_NOT_STICKY;
    }

    ArrayList<String> args = new ArrayList<>();
    args.add("--reproducer_path=" + getFilesDir().getPath());
    args.add("--target_class=" + targetClass);

    // Note: If AndroidStartWrapper.Call is blocking (likely for a fuzzer),
    // you should run this in a separate thread to avoid ANRs (Application Not Responding).
    new Thread(() -> {
        AndroidStartWrapper.Call(args);
        stopSelf(); // optionally stop service when fuzzing completes
    }).start();

    return START_NOT_STICKY;
  }
}