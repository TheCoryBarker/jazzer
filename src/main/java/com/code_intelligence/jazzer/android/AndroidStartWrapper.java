package com.code_intelligence.jazzer.android;

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ArrayList;

public class AndroidStartWrapper {
    public static void Call(){

      try{
        System.out.println("Finding Driver class");
        Class<?> DRIVER = Class.forName(
          "com.code_intelligence.jazzer.driver.Driver", false, AndroidStartWrapper.class.getClassLoader());

        System.out.println("Finding start method");
        MethodHandle START = MethodHandles.lookup().findStatic(
          DRIVER, "start", MethodType.methodType(int.class, List.class, boolean.class));

        List<String> args = new ArrayList<String>();
        args.add("--target_class=com.google.android.samples.hellojni.FuzzMe");

        int r = (int)START.invokeExact(args, false);
      }
      catch(Throwable e) {
        System.out.println(e);
      } 
    }

}