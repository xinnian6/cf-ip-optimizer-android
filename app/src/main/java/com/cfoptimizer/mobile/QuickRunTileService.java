package com.cfoptimizer.mobile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QuickRunTileService extends TileService {
    private static final String PREFS = "cf_optimizer_mobile";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, QuickRunService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("quickRunRunning", true).apply();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("quickRunRunning", false);
        tile.setLabel("CF优选");
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(running ? "后台测速中" : "一键后台测速");
        }
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
