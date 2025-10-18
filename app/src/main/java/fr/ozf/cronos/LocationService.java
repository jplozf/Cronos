package fr.ozf.cronos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    public static final String ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE";
    public static final String DISTANCE_BROADCAST = "DISTANCE_BROADCAST";
    public static final String EXTRA_TOTAL_DISTANCE_KM = "EXTRA_TOTAL_DISTANCE_KM";

    private static final String NOTIFICATION_CHANNEL_ID = "LocationServiceChannel";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;
    private double totalDistanceKm = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service created");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d(TAG, "onLocationChanged: New location received: " + location.getLatitude() + ", " + location.getLongitude());
                if (lastLocation == null) {
                    lastLocation = location;
                } else {
                    float distance = lastLocation.distanceTo(location); // distance in meters
                    totalDistanceKm += distance / 1000.0; // convert to kilometers
                    lastLocation = location;
                    Log.d(TAG, "Distance updated: " + totalDistanceKm + " km");
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service started");
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopService();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Cronos Training")
                .setContentText("Tracking your location...")
                .setSmallIcon(R.drawable.ic_map_green) // Make sure you have this drawable
                .build();

        startForeground(1, notification);
        startTracking();

        return START_STICKY;
    }

    private void startTracking() {
        Log.d(TAG, "startTracking: Starting location tracking");
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lastLocation = null;
            totalDistanceKm = 0.0;
            try {
                boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (isGpsEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
                    Log.d(TAG, "Requesting updates from GPS_PROVIDER");
                } else {
                    Log.w(TAG, "GPS_PROVIDER is not enabled.");
                }

                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, locationListener);
                    Log.d(TAG, "Requesting updates from NETWORK_PROVIDER");
                } else {
                    Log.w(TAG, "NETWORK_PROVIDER is not enabled.");
                }

                if (!isGpsEnabled && !isNetworkEnabled) {
                    Log.e(TAG, "No location providers are enabled. Cannot track location.");
                    // Optionally, stop the service or notify the user
                }

            } catch (SecurityException e) {
                Log.e(TAG, "Location permission not granted, even after check.", e);
            }
        } else {
            Log.w(TAG, "startTracking: Location permissions not granted. Cannot start tracking.");
            // Optionally, you could stop the service here if tracking is essential
            // stopSelf();
        }
    }

    private void stopService() {
        Log.d(TAG, "stopService: Stopping location tracking and service.");
        locationManager.removeUpdates(locationListener);

        // Broadcast the final distance
        Intent broadcastIntent = new Intent(DISTANCE_BROADCAST);
        broadcastIntent.putExtra(EXTRA_TOTAL_DISTANCE_KM, totalDistanceKm);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
        Log.d(TAG, "stopService: Final distance broadcasted: " + totalDistanceKm + " km");

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service destroyed");
        // Ensure updates are removed if the service is destroyed unexpectedly
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We are not using binding, so return null
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
