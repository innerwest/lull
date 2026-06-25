package com.innerwest.lull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

/**
 * Foreground service (type mediaPlayback) that keeps Lull's WebView audio alive
 * with the screen off and surfaces lock-screen / notification transport controls.
 *
 * The audio itself plays in the WebView; this service only (a) keeps the process
 * alive via startForeground + a partial wake lock, and (b) owns a MediaSession +
 * media-style notification whose transport actions are forwarded back into the
 * WebView through {@link LullAudioPlugin}.
 */
public class LullAudioService extends android.app.Service {
    static final String ACTION_START = "com.innerwest.lull.action.START";
    static final String ACTION_UPDATE = "com.innerwest.lull.action.UPDATE";
    static final String ACTION_STOP = "com.innerwest.lull.action.STOP";
    private static final String CHANNEL_ID = "lull_playback";
    private static final int NOTI_ID = 1;

    private MediaSessionCompat session;
    private PowerManager.WakeLock wakeLock;
    private String title = "Lull";
    private boolean playing = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        session = new MediaSessionCompat(this, "Lull");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { LullAudioPlugin.dispatch("play"); }
            @Override public void onPause() { LullAudioPlugin.dispatch("pause"); }
            @Override public void onStop() { LullAudioPlugin.dispatch("pause"); }
            @Override public void onSkipToNext() { LullAudioPlugin.dispatch("next"); }
            @Override public void onSkipToPrevious() { LullAudioPlugin.dispatch("prev"); }
        });
        session.setActive(true);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lull::playback");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            releaseWakeLock();
            session.setActive(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
            if (intent.hasExtra("title")) title = intent.getStringExtra("title");
            if (intent.hasExtra("playing")) playing = intent.getBooleanExtra("playing", true);
            applyState();
            startForegroundCompat(buildNotification());
            if (playing) acquireWakeLock(); else releaseWakeLock();
            return START_STICKY;
        }

        // Media button (notification buttons, lock screen, bluetooth) -> session callback
        MediaButtonReceiver.handleIntent(session, intent);
        return START_STICKY;
    }

    private void applyState() {
        session.setMetadata(new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Lull")
            .build());
        session.setPlaybackState(new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, piFlags);

        NotificationCompat.Action prev = new NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        NotificationCompat.Action playPause = playing
            ? new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE))
            : new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY));
        NotificationCompat.Action next = new NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Lull")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(content)
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .setStyle(new MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTI_ID, n);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Playback",
                NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private void acquireWakeLock() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(); }
    private void releaseWakeLock() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        if (session != null) session.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
