package de.rhaeus.dndsync;


import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;
import java.util.concurrent.ExecutionException;

public class DNDNotificationService extends NotificationListenerService {
    private static final String TAG = "DNDNotificationService";
    private static final String DND_SYNC_CAPABILITY_NAME = "dnd_sync";
    private static final String DND_SYNC_MESSAGE_PATH = "/wear-dnd-sync";

    public static boolean running = false;

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "listener connected");
        running = true;
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "listener disconnected");
        running = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        onNotificationAddedOrRemovedCallDNDSync(sbn,5);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        onNotificationAddedOrRemovedCallDNDSync(sbn,6);
    }

    private void onNotificationAddedOrRemovedCallDNDSync(StatusBarNotification sbn, int interruptionFilter) {
        if(sbn.getPackageName().equals("com.google.android.apps.wellbeing")) {
            String title = sbn.getNotification().extras.getString("android.title");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean syncBedTime = prefs.getBoolean("bedtime_sync_key", true);
            if(syncBedTime && (title.contains("on") || title.contains("paused"))) {
                int updatedInterruptionFilter;
                //BedTime
                if (title.contains("paused")) {
                    updatedInterruptionFilter = (interruptionFilter == 5) ? 6 : 5;
                } else {
                    updatedInterruptionFilter = interruptionFilter;
                }
                new Thread(() -> sendDNDSync(updatedInterruptionFilter)).start();
            }
        }
    }

    @Override
    public void onInterruptionFilterChanged (int interruptionFilter) {
        Log.d(TAG, "interruption filter changed to " + interruptionFilter);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean syncDnd = prefs.getBoolean("dnd_sync_key", true);
        if(syncDnd) {
            new Thread(new Runnable() {
                public void run() {
                    sendDNDSync(interruptionFilter);
                }
            }).start();
        }
    }

    private void sendDNDSync(int dndState) {
        // https://developer.android.com/training/wearables/data/messages

        // search nodes for sync
        CapabilityInfo capabilityInfo = null;
        try {
            capabilityInfo = Tasks.await(
                    Wearable.getCapabilityClient(this).getCapability(
                            DND_SYNC_CAPABILITY_NAME, CapabilityClient.FILTER_REACHABLE));
        } catch (ExecutionException e) {
            e.printStackTrace();
            Log.e(TAG, "execution error while searching nodes", e);
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.e(TAG, "interruption error while searching nodes", e);
            return;
        }

        // send request to all reachable nodes
        // capabilityInfo has the reachable nodes with the dnd sync capability
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        if (connectedNodes.isEmpty()) {
            // Unable to retrieve node with transcription capability
            Log.d(TAG, "Unable to retrieve node with sync capability!");
        } else {
            for (Node node : connectedNodes) {
                if (node.isNearby()) {
                    byte[] data = new byte[1];
                    data[0] = (byte) dndState;
                    Task<Integer> sendTask =
                            Wearable.getMessageClient(this).sendMessage(node.getId(), DND_SYNC_MESSAGE_PATH, data);

                    sendTask.addOnSuccessListener(new OnSuccessListener<Integer>() {
                        @Override
                        public void onSuccess(Integer integer) {
                            Log.d(TAG, "send successful! Receiver node id: " + node.getId());
                        }
                    });

                    sendTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "send failed! Receiver node id: " + node.getId());
                        }
                    });
                }
            }
        }
    }
}
