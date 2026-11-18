package com.gtarayici.webapkshell;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/**
 * Yüklü web uygulamasının başlatıcısı: G-Tarayıcı'nın web uygulaması penceresini açar ve kapanır.
 * G-Tarayıcı kaldırılmışsa adres varsayılan tarayıcıda açılır.
 */
public class LaunchActivity extends Activity {
    private static final String ACTION_VIEW_PWA = "mozilla.components.feature.pwa.VIEW_PWA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri url = null;
        String host = null;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            url = Uri.parse(info.metaData.getString("gtarayici.start_url"));
            host = info.metaData.getString("gtarayici.host");
        } catch (Exception ignored) {
        }
        if (url != null) {
            Intent intent = new Intent(ACTION_VIEW_PWA, url);
            intent.setPackage(host);
            intent.putExtra("gtarayici.webapk", getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        finish();
    }
}
