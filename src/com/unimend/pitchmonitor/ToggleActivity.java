package com.unimend.pitchmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/** A transparent, animation-free bridge for widget taps. */
public final class ToggleActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        if (PitchService.isRunning(this)) {
            stopService(new Intent(this, PitchService.class));
            PitchWidget.publishIdle(this);
        } else if (hasMic()) {
            Intent service = new Intent(this, PitchService.class).setAction(PitchService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } else {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private boolean hasMic() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
}
