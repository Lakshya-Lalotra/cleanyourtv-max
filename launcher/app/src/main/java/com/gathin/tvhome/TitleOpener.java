package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/** Starts a title in a streaming app: the title itself when the app takes the link, else the app, else its Play Store page. */
final class TitleOpener {
    private TitleOpener() {
    }

    static boolean open(Context context, String pkg, String url) {
        PackageManager pm = context.getPackageManager();
        if (!url.isEmpty()) {
            Intent title = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg);
            if (pm.resolveActivity(title, 0) != null && start(context, title)) return true;
        }
        Intent app = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (app == null) app = pm.getLaunchIntentForPackage(pkg);
        if (app != null && start(context, app)) return true;
        return start(context, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
    }

    private static boolean start(Context context, Intent intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
