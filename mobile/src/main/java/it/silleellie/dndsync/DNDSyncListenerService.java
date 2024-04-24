package it.silleellie.dndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.apache.commons.lang3.SerializationUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import it.silleellie.dndsync.shared.WearSignal;

public class DNDSyncListenerService extends WearableListenerService {
    private static final String TAG = "DNDSyncListenerService";
    private static final String DND_SYNC_MESSAGE_PATH = "/wear-dnd-sync";

    private static final String BED_TIME_SYNC_MESSAGE_PATH = "/wear-bed-time-sync";



    @Override
    public void onMessageReceived (@NonNull MessageEvent messageEvent) {

        if (messageEvent.getPath().equalsIgnoreCase(BED_TIME_SYNC_MESSAGE_PATH)) {
            byte[] data = messageEvent.getData();
            WearSignal wearSignal = SerializationUtils.deserialize(data);
            int bedtimeStateWear = wearSignal.dndState;
            boolean isEnabledDND = isDNDModeEnabled(this);
            boolean isExistNotification  = executeNotifications();


            if((isExistNotification && bedtimeStateWear == 1) || (!isExistNotification && bedtimeStateWear == 2)) {
                executeBedTimeMode();
            }
            if(isEnabledDND && !isExistNotification && bedtimeStateWear == 2){
                executeBedTimeMode();
            }

        } else if (messageEvent.getPath().equalsIgnoreCase(DND_SYNC_MESSAGE_PATH)) {

            Log.d(TAG, "received path: " + DND_SYNC_MESSAGE_PATH);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            byte[] data = messageEvent.getData();
            WearSignal wearSignal = SerializationUtils.deserialize(data);
            int dndStateWear = wearSignal.dndState;

            Log.d(TAG, "dndStateWear: " + dndStateWear);

            // get dnd state
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            int currentDndState = mNotificationManager.getCurrentInterruptionFilter();

            Log.d(TAG, "currentDndState: " + currentDndState);
            if (currentDndState < 0 || currentDndState > 4) {
                Log.d(TAG, "Current DND state it's weird, should be in range [0,4]");
            }

            boolean shouldSync = prefs.getBoolean("watch_dnd_sync_key", false);

            if (currentDndState != dndStateWear && shouldSync) {
                Log.d(TAG, "currentDndState != dndStateWear: " + currentDndState + " != " + dndStateWear);
                if (mNotificationManager.isNotificationPolicyAccessGranted()) {
                    mNotificationManager.setInterruptionFilter(dndStateWear);
                    Log.d(TAG, "DND set to " + dndStateWear);
                } else {
                    Log.d(TAG, "attempting to set DND but access not granted");
                }
            }

        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private static boolean executeNotifications() {
        try {
            Process process = Runtime.getRuntime().exec("su");

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            process.getOutputStream().write("dumpsys notification | grep \"com.google.android.apps.wellbeing\"\n".getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();

            String line;
            boolean isContain = false;
            while ((line = reader.readLine()) != null) {
                if(line.contains("wind_down_notifications")){
                    isContain =  true;
                    break;
                }
            }
            process.waitFor(100, TimeUnit.MILLISECONDS);

            return isContain;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isDNDModeEnabled(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            return notificationManager.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_NONE;
        }
        return false; // Failed to get NotificationManager
    }
    private static void executeBedTimeMode() {
        try {
            Process process = Runtime.getRuntime().exec("su");

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            process.getOutputStream().write("cmd statusbar click-tile com.google.android.apps.wellbeing/.screen.ui.GrayscaleTileService\n".getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            process.waitFor(100, TimeUnit.MILLISECONDS);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
