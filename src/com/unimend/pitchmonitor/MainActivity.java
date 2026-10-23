package com.unimend.pitchmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_MIC = 41;
    private Button button;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (hasMic()) {
            showUi();
        } else {
            showUi();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
        }
    }

    private void showUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.BLACK);

        TextView title = text("PITCH MONITOR", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = text("实时音高 · 音准轨迹 · dBFS\n\n添加 3×1 桌面组件后，点击卡片即可开始或停止监听。运行时系统会显示麦克风和前台通知。", 17, 0xffbbbbbb);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(0, dp(28), 0, dp(32));
        root.addView(info, infoParams);

        button = new Button(this);
        button.setText(PitchService.isRunning(this) ? "停止监听" : "开始监听");
        button.setTextSize(17);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (PitchService.isRunning(MainActivity.this)) {
                    stopService(new Intent(MainActivity.this, PitchService.class));
                    PitchWidget.publishIdle(MainActivity.this);
                    button.setText("开始监听");
                } else if (hasMic()) {
                    startListening();
                    button.setText("停止监听");
                } else {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
                }
            }
        });
        root.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(root);
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        return view;
    }

    private boolean hasMic() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startListening() {
        Intent service = new Intent(this, PitchService.class).setAction(PitchService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
            if (button != null) button.setText("停止监听");
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
