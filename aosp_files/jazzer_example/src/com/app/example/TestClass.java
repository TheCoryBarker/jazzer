
package com.app.example;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import android.util.Log;

public class TestClass {
  private static final String TAG = "TestClass";
    public static void test(int data) {
        Log.d(TAG, "Value of data: " + data);
        if (data == 365485522) {
            throw new UnsupportedOperationException("fuzz error condition hit!!");
        }
    }
}
