package com.innerwest.lull;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LullAudioPlugin.class);
        super.onCreate(savedInstanceState);
        // The app starts audio from a tap, but sound switches and the sleep-timer
        // auto-start call audioEl.play() programmatically; allow that without
        // demanding a fresh gesture each time.
        getBridge().getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);

        // Android 13+ needs runtime POST_NOTIFICATIONS for the media notification
        // (and thus the lock-screen transport controls) to be visible.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }
}
