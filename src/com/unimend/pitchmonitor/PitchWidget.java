package com.unimend.pitchmonitor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class PitchWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        publishIdle(context, manager, ids);
    }

    @Override public void onDisabled(Context context) {
        context.stopService(new Intent(context, PitchService.class));
    }

    static int[] ids(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, PitchWidget.class));
    }

    static void publishIdle(Context context) {
        publishIdle(context, AppWidgetManager.getInstance(context), ids(context));
    }

    static void publishIdle(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null || ids.length == 0) return;
        PitchRenderer renderer = new PitchRenderer(context);
        for (int id : ids) {
            RemoteViews views = baseViews(context, id);
            views.setImageViewBitmap(R.id.content, renderer.drawIdle());
            manager.updateAppWidget(id, views);
        }
    }

    static RemoteViews baseViews(Context context, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        Intent intent = new Intent(context, ToggleActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent click = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.click_target, click);
        return views;
    }
}
