package com.code_intelligence.jazzer.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ArrayList;

public class AndroidStart extends Service {
    
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

    @Override
    public void onCreate(){
        super.onCreate();
        AndroidStartWrapper.Call();
    }
}