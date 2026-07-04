package com.cfoptimizer.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

public class QuickRunActivity extends Activity {
    private static final String PREFS = "cf_optimizer_mobile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startQuickRunService();
        finishWithoutAnimation();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startQuickRunService();
        finishWithoutAnimation();
    }

    private void startQuickRunService() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("quickRunRunning", true).apply();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先打开 App 允许通知权限，否则可能看不到进度", Toast.LENGTH_SHORT).show();
        }

        Intent service = new Intent(this, QuickRunService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Exception e) {
            prefs.edit().putBoolean("quickRunRunning", false).apply();
            Toast.makeText(this, "后台测速启动失败：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
