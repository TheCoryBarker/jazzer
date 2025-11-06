
package com.app.example;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import android.util.Log;

public class TestClass {
  private static final String TAG = "TestClass";

  public static void test(FuzzedDataProvider data) {
    // Try to obtain an application Context to exercise Intent flows
    Context ctx = tryGetApplicationContext();
    if (ctx == null) {
      Log.d(TAG, "No application context available, skipping intent operations");
      return;
    }

    // Choose which API to exercise
    int which = data.consumeInt(0, 2); // 0=startActivity, 1=startService, 2=sendBroadcast

    // Build component from fuzz data; sanitizer will guide towards honeypot values
    String pkg = data.consumeString(40);
    String cls = data.consumeString(80);
    if (pkg == null) pkg = "";
    if (cls == null) cls = "";

    Intent intent = new Intent();
    try {
      if (!pkg.isEmpty() && !cls.isEmpty()) {
        intent.setComponent(new ComponentName(pkg, cls));
      }
    } catch (Throwable t) {
      // Ignore invalid component names
    }

    try {
      switch (which) {
        case 0:
          // startActivity from non-activity context requires this flag
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          ctx.startActivity(intent);
          break;
        case 1:
          ctx.startService(intent);
          break;
        default:
          ctx.sendBroadcast(intent);
          break;
      }
    } catch (Throwable t) {
      // Swallow runtime exceptions from invalid intents during fuzzing
      Log.d(TAG, "Intent operation threw: " + t.getClass().getSimpleName());
    }
  }

  private static Context tryGetApplicationContext() {
    try {
      // Use reflection to obtain Application without relying on an Activity
      Class<?> at = Class.forName("android.app.ActivityThread");
      java.lang.reflect.Method m = at.getDeclaredMethod("currentApplication");
      Object app = m.invoke(null);
      return (Context) app;
    } catch (Throwable t) {
      return null;
    }
  }
}
