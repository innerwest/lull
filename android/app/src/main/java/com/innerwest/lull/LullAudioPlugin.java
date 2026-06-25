package com.innerwest.lull;

import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridges the web app to {@link LullAudioService}. JS calls start/update/stop to
 * control the foreground service; transport actions from the lock screen /
 * notification are forwarded back to JS via the "transport" event.
 */
@CapacitorPlugin(name = "LullAudio")
public class LullAudioPlugin extends Plugin {

    private static volatile LullAudioPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (instance == this) instance = null;
    }

    /** Called from the service (main thread) when a transport control is used. */
    static void dispatch(String action) {
        LullAudioPlugin p = instance;
        if (p == null) return;
        JSObject data = new JSObject();
        data.put("action", action);
        p.notifyListeners("transport", data);
    }

    @PluginMethod
    public void start(PluginCall call) {
        Intent i = new Intent(getContext(), LullAudioService.class);
        i.setAction(LullAudioService.ACTION_START);
        i.putExtra("title", call.getString("title", "Lull"));
        i.putExtra("playing", call.getBoolean("playing", true));
        ContextCompat.startForegroundService(getContext(), i);
        call.resolve();
    }

    @PluginMethod
    public void update(PluginCall call) {
        Intent i = new Intent(getContext(), LullAudioService.class);
        i.setAction(LullAudioService.ACTION_UPDATE);
        i.putExtra("title", call.getString("title", "Lull"));
        i.putExtra("playing", call.getBoolean("playing", true));
        ContextCompat.startForegroundService(getContext(), i);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Intent i = new Intent(getContext(), LullAudioService.class);
        i.setAction(LullAudioService.ACTION_STOP);
        getContext().startService(i);
        call.resolve();
    }
}
