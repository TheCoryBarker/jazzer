// Copyright 2025 Code Intelligence GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.code_intelligence.jazzer.sanitizers;

import android.util.Log;
import com.code_intelligence.jazzer.api.FuzzerSecurityIssueCritical;
import com.code_intelligence.jazzer.api.HookType;
import com.code_intelligence.jazzer.api.Jazzer;
import com.code_intelligence.jazzer.api.MethodHook;
import com.code_intelligence.jazzer.api.MethodHooks;
import java.lang.invoke.MethodHandle;

/**
 * Detects Intent redirection vulnerabilities in Android applications.
 *
 * <p>Intent redirection occurs when an application receives an Intent from an untrusted source and
 * uses it to start an Activity, Service, or send a Broadcast without proper validation. An attacker
 * can exploit this to:
 *
 * <ul>
 *   <li>Launch arbitrary exported components with controlled data
 *   <li>Access protected components by bypassing permission checks
 *   <li>Escalate privileges by starting components in other apps
 *   <li>Leak sensitive data through implicit Intents
 * </ul>
 *
 * <p>This sanitizer hooks methods in {@code android.content.Context} and {@code
 * android.app.Activity} that start components using Intents. It guides the fuzzer to create Intents
 * with specific component names and reports when a honeypot component is reached.
 *
 * <p>IMPORTANT: This sanitizer uses REPLACE hooks to prevent Intents from actually being started
 * during fuzzing, avoiding millions of ActivityNotFoundException and performance overhead.
 *
 * <p>The vulnerability is considered critical because it can lead to unauthorized access to
 * application components and data.
 */
@SuppressWarnings({"unused", "UnusedVariable"})
public final class IntentRedirection {
  private static final String TAG = "CorySanitizer";

  // Honeypot package and class name to detect Intent redirection
  private static final String HONEYPOT_PACKAGE = "jaz.zer";
  private static final String HONEYPOT_CLASS = "jaz.zer.IntentTarget";

  private IntentRedirection() {}

  /**
   * Hooks startActivity methods to detect Intent redirection vulnerabilities.
   *
   * <p>Checks if the Intent has an explicit component set. If the component matches the honeypot
   * class, reports a critical security issue. Otherwise, guides the fuzzer towards creating an
   * Intent with the honeypot component.
   *
   * <p>Does NOT actually invoke startActivity to avoid overhead during fuzzing.
   */
  @MethodHooks({
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "startActivity",
        targetMethodDescriptor = "(Landroid/content/Intent;)V"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "startActivity",
        targetMethodDescriptor = "(Landroid/content/Intent;Landroid/os/Bundle;)V"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.app.Activity",
        targetMethod = "startActivityForResult",
        targetMethodDescriptor = "(Landroid/content/Intent;I)V"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.app.Activity",
        targetMethod = "startActivityForResult",
        targetMethodDescriptor = "(Landroid/content/Intent;ILandroid/os/Bundle;)V"),
  })
  public static void startActivityHook(
      MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
    Log.d(TAG, "startActivityHook triggered - hookId: " + hookId);

    if (arguments.length == 0) {
      Log.d(TAG, "No arguments, skipping");
      return; // Don't invoke the method
    }

    Object intentObj = arguments[0];
    if (intentObj == null) {
      Log.d(TAG, "Intent is null, skipping");
      return; // Don't invoke the method
    }

    Log.d(TAG, "Checking Intent component for Activity");
    checkIntentComponent(intentObj, hookId, "Activity");
    // NOTE: We do NOT call method.invokeWithArguments() to prevent actually starting the Intent
  }

  /**
   * Hooks startService methods to detect Intent redirection to services. Does NOT actually invoke
   * startService to avoid overhead during fuzzing.
   */
  @MethodHooks({
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "startService",
        targetMethodDescriptor = "(Landroid/content/Intent;)Landroid/content/ComponentName;"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "startForegroundService",
        targetMethodDescriptor = "(Landroid/content/Intent;)Landroid/content/ComponentName;"),
  })
  public static Object startServiceHook(
      MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
    Log.d(TAG, "startServiceHook triggered - hookId: " + hookId);

    if (arguments.length == 0) {
      Log.d(TAG, "No arguments, skipping");
      return null; // Return null instead of starting service
    }

    Object intentObj = arguments[0];
    if (intentObj == null) {
      Log.d(TAG, "Intent is null, skipping");
      return null; // Return null instead of starting service
    }

    Log.d(TAG, "Checking Intent component for Service");
    checkIntentComponent(intentObj, hookId, "Service");
    // NOTE: We do NOT call method.invokeWithArguments() to prevent actually starting the service
    return null;
    }

  /**
   * Hooks sendBroadcast methods to detect Intent redirection in broadcasts. Does NOT actually
   * invoke sendBroadcast to avoid overhead during fuzzing.
   */
  @MethodHooks({
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "sendBroadcast",
        targetMethodDescriptor = "(Landroid/content/Intent;)V"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "sendBroadcast",
        targetMethodDescriptor = "(Landroid/content/Intent;Ljava/lang/String;)V"),
    @MethodHook(
        type = HookType.REPLACE,
        targetClassName = "android.content.Context",
        targetMethod = "sendOrderedBroadcast",
        targetMethodDescriptor = "(Landroid/content/Intent;Ljava/lang/String;)V"),
  })
  public static void sendBroadcastHook(
      MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
    Log.d(TAG, "sendBroadcastHook triggered - hookId: " + hookId);

    if (arguments.length == 0) {
      Log.d(TAG, "No arguments, skipping");
      return; // Don't invoke the method
    }

    Object intentObj = arguments[0];
    if (intentObj == null) {
      Log.d(TAG, "Intent is null, skipping");
      return; // Don't invoke the method
    }

    Log.d(TAG, "Checking Intent component for Broadcast");
    checkIntentComponent(intentObj, hookId, "Broadcast");
    // NOTE: We do NOT call method.invokeWithArguments() to prevent actually sending the broadcast
  }

  /**
   * Checks if an Intent has a component set and whether it matches the honeypot.
   *
   * <p>Uses reflection to access Intent methods since this code runs during instrumentation and may
   * not have access to Android SDK classes directly.
   */
  private static void checkIntentComponent(Object intentObj, int hookId, String componentType) {
    try {
      // Get the component name from the Intent using reflection
      java.lang.reflect.Method getComponentMethod = intentObj.getClass().getMethod("getComponent");
      Object componentObj = getComponentMethod.invoke(intentObj);

      if (componentObj == null) {
        // No explicit component set - implicit Intent
        Log.d(TAG, "No explicit component set - implicit Intent");
        guideTowardsExplicitComponent(intentObj, hookId, componentType);
        return;
      }

      // Get the class name from ComponentName
      java.lang.reflect.Method getClassNameMethod =
          componentObj.getClass().getMethod("getClassName");
      String className = (String) getClassNameMethod.invoke(componentObj);

      if (className == null) {
        Log.d(TAG, "Component class name is null");
        return;
      }

      Log.d(TAG, "Found component: " + className);

      // Check if this matches our honeypot class
      if (HONEYPOT_CLASS.equals(className)) {
        Log.e(TAG, "!!! HONEYPOT REACHED !!! Component: " + className);
        Jazzer.reportFindingFromHook(
            new FuzzerSecurityIssueCritical(
                "Intent Redirection to "
                    + componentType
                    + "\n"
                    + "The application uses an attacker-controlled Intent to start a "
                    + componentType.toLowerCase()
                    + ". "
                    + "This can lead to unauthorized component access, privilege escalation, "
                    + "or information disclosure.\n"
                    + "Target component: "
                    + className));
      }

      // Guide the fuzzer towards the honeypot class name
      Log.d(TAG, "Guiding fuzzer towards honeypot class: " + HONEYPOT_CLASS);
      Jazzer.guideTowardsEquality(className, HONEYPOT_CLASS, hookId);

      // Also get package name and guide towards it
      java.lang.reflect.Method getPackageNameMethod =
          componentObj.getClass().getMethod("getPackageName");
      String packageName = (String) getPackageNameMethod.invoke(componentObj);

      if (packageName != null) {
        Log.d(TAG, "Found package: " + packageName + ", guiding towards: " + HONEYPOT_PACKAGE);
        Jazzer.guideTowardsEquality(packageName, HONEYPOT_PACKAGE, hookId);
      }

    } catch (Exception e) {
      // If reflection fails (e.g., not an Android Intent), silently ignore
      // This can happen during instrumentation when processing non-Android code
      Log.w(TAG, "Exception while checking Intent component: " + e.getMessage());
    }
  }

  /** Guides the fuzzer towards creating an explicit Intent when an implicit Intent is used. */
  private static void guideTowardsExplicitComponent(
      Object intentObj, int hookId, String componentType) {
    try {
      // Get the action from the Intent
      java.lang.reflect.Method getActionMethod = intentObj.getClass().getMethod("getAction");
      String action = (String) getActionMethod.invoke(intentObj);
      if (action != null) {
        Log.d(TAG, "Implicit Intent with action: " + action);
        // Guide towards a specific action that might indicate redirection
        Jazzer.guideTowardsEquality(action, "android.intent.action.VIEW", hookId);
      }
    } catch (Exception e) {
      // Silently ignore reflection failures
      Log.w(TAG, "Exception while guiding towards explicit component: " + e.getMessage());
    }
  }
}
