package com.example.myapplication;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FacebookBlockService extends VpnService {
    private static final String TAG = "FacebookBlockService";
    private static final String FACEBOOK_PACKAGE = "com.facebook.katana"; // Facebook app package name
    private static final String FACEBOOK_DOMAIN = "facebook.com";
    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        new Thread(this::startVpn).start();
        new Thread(this::monitorFacebookApp).start();
        return START_STICKY;
    }

    private void startVpn() {
        Builder builder = new Builder();
        builder.setSession("Facebook Blocker")
                .addAddress("192.168.0.1", 24) // Assign a local IP for VPN
                .addRoute("0.0.0.0", 0); // Route all traffic through VPN

        Log.e(TAG, "startVpn");
        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface");
            stopSelf();
            return;
        }

        FileInputStream vpnInputStream = new FileInputStream(vpnInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(32767);

        try {
            while (true) {
                int length = vpnInputStream.read(packet.array());
                if (length > 0) {
                    String packetData = new String(packet.array(), 0, length);
                    if (packetData.contains(FACEBOOK_DOMAIN)) {
                        Log.d(TAG, "Blocking Facebook traffic");
                        packet.clear(); // Simulate blocking by dropping packets
                    }
                }
                packet.clear();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading VPN interface", e);
        } finally {
            try {
                vpnInputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close VPN input stream", e);
            }
        }
    }

    private void monitorFacebookApp() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        Log.d(TAG, "Facebook app is running. Terminating...");
        while (true) {
            try {
                Thread.sleep(2000); // Check every 2 seconds
                if (isFacebookAppRunning(activityManager)) {
                    Log.d(TAG, "Facebook app is running. Terminating...");
                    activityManager.killBackgroundProcesses(FACEBOOK_PACKAGE);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error in monitoring loop", e);
                break;
            }
        }
    }

    private boolean isFacebookAppRunning(ActivityManager activityManager) {
        for (ActivityManager.RunningAppProcessInfo processInfo : activityManager.getRunningAppProcesses()) {
            if (processInfo.processName.equals(FACEBOOK_PACKAGE) &&
                    processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Start login activity to ask for credentials before the service can be stopped
        Intent loginIntent = new Intent(this, LoginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Ensure it can start as a new task
        startActivity(loginIntent);

        Log.d(TAG, "Service stopped (login required)");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding for this service
    }
}
