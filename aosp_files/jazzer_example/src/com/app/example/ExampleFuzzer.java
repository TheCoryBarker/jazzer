
package com.app.example;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import android.util.Log;

public class ExampleFuzzer {
    private static final String TAG = "FuzzerExample";
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        TestClass.test(data);
    }
}
