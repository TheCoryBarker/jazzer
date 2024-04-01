
package com.android.tools.r8;

import com.code_intelligence.jazzer.driver.Opt;
import com.code_intelligence.jazzer.driver.OfflineInstrumentor;
import static java.lang.System.exit;
import com.code_intelligence.jazzer.utils.Log;

public class D8 {
  public static void main(String[] args) {
    
    for(String s: args) {
      System.out.println(s);
    }

    return;

    /*
    if (!Opt.instrumentOnly.get().isEmpty()) {
      if (Opt.dumpClassesDir.get().isEmpty()) {
        Log.error("--dump_classes_dir must be set with --instrument_only");
        exit(1);
      }

      boolean instrumentationSuccess = OfflineInstrumentor.instrumentJars(Opt.instrumentOnly.get());
      if (!instrumentationSuccess) {
        exit(1);
      }
    }
    */

  }
}
