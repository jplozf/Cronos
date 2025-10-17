package fr.ozf.cronos;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline trail;
    private boolean isTracking = false;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;
    private double totalDistanceKm = 0.0;
    private List<GeoPoint> trackedPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Log.d(TAG, "onCreate: start");

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Setup toolbar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Training Map");
        }

        // Initialize map
        mapView = findViewById(R.id.map_view);
        initializeMap();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (isTracking) {
                    GeoPoint newPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    trackedPoints.add(newPoint);
                    trail.addPoint(newPoint);

                    if (lastLocation != null) {
                        float distance = lastLocation.distanceTo(location); // distance in meters
                        totalDistanceKm += distance / 1000.0; // convert to kilometers
                        Log.d(TAG, "Distance updated: " + totalDistanceKm + " km");
                    }
                    lastLocation = location;
                    mapView.invalidate();
                }
            }
        };

        // Check if tracking should be started based on intent extras
        boolean shouldStartTracking = getIntent().getBooleanExtra("start_tracking", false);
        if (shouldStartTracking) {
            startTracking();
        }

        FloatingActionButton fabRecenter = findViewById(R.id.fab_recenter);
        fabRecenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recenterMapOnUserLocation();
            }
        });

        Log.d(TAG, "onCreate: end");
    }

    private void initializeMap() {
        if (mapView == null) {
            Log.e(TAG, "initializeMap: mapView is null");
            return;
        }

        Log.d(TAG, "initializeMap: start");

        // Basic map setup
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // Request location permission if not granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Center map on last known location
            Location lastKnownLocation = null;
            try {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Location permission not granted for getting last known location", e);
            }

            if (lastKnownLocation != null) {
                mapView.getController().setCenter(new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));
            } else {
                // Fallback to Paris if no location is available
                mapView.getController().setCenter(new GeoPoint(48.8583, 2.2945));
            }
            setupLocationOverlay();
        }

        Log.d(TAG, "initializeMap: end");
    }

    private void setupLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();

        // Customizing the user location icon color
        try {
            // --- Person Icon (Green Circle) ---
            int diameter = 60;
            Bitmap personBitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
            Canvas personCanvas = new Canvas(personBitmap);
            Paint personPaint = new Paint();
            personPaint.setColor(ContextCompat.getColor(this, R.color.green_primary));
            personPaint.setStyle(Paint.Style.FILL);
            personPaint.setAntiAlias(true);
            personCanvas.drawCircle(diameter / 2f, diameter / 2f, diameter / 2f, personPaint);
            Paint borderPaint = new Paint();
            borderPaint.setColor(ContextCompat.getColor(this, R.color.white));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(5);
            borderPaint.setAntiAlias(true);
            personCanvas.drawCircle(diameter / 2f, diameter / 2f, (diameter - 5) / 2f, borderPaint);
            myLocationOverlay.setPersonIcon(personBitmap);

            // --- Direction Arrow Icon (Green Triangle) ---
            int arrowSize = 60;
            Bitmap arrowBitmap = Bitmap.createBitmap(arrowSize, arrowSize, Bitmap.Config.ARGB_8888);
            Canvas arrowCanvas = new Canvas(arrowBitmap);
            Paint arrowPaint = new Paint();
            arrowPaint.setColor(ContextCompat.getColor(this, R.color.green_primary));
            arrowPaint.setStyle(Paint.Style.FILL);
            arrowPaint.setAntiAlias(true);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(arrowSize / 2f, 0); // Top point
            path.lineTo(0, arrowSize);     // Bottom-left
            path.lineTo(arrowSize, arrowSize); // Bottom-right
            path.close();
            arrowCanvas.drawPath(path, arrowPaint);
            myLocationOverlay.setDirectionArrow(arrowBitmap, personBitmap);

        } catch (Exception e) {
            Log.e(TAG, "Error customizing location overlay icon", e);
        }

        mapView.getOverlays().add(myLocationOverlay);
        Log.d(TAG, "setupLocationOverlay: location overlay added");
    }

    private void startTracking() {
        Log.d(TAG, "startTracking called");
        isTracking = true;
        trackedPoints.clear();
        totalDistanceKm = 0.0;
        
        // Initialize lastLocation with the current fix if available to start distance calculation immediately
        if (myLocationOverlay != null && myLocationOverlay.getLastFix() != null) {
            lastLocation = myLocationOverlay.getLastFix();
            Log.d(TAG, "startTracking: Initialized lastLocation");
        } else {
            lastLocation = null;
        }

        trail = new Polyline();
        trail.setColor(ContextCompat.getColor(this, R.color.green_primary));
        trail.setWidth(10f);
        mapView.getOverlays().add(trail);

        if (myLocationOverlay != null) {
            myLocationOverlay.enableFollowLocation();
        }

        // Request location updates from the best available provider
        try {
            String provider = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            }

            if (provider != null) {
                locationManager.requestLocationUpdates(provider, 2000, 5, locationListener);
                Log.d(TAG, "Requested location updates from " + provider);
            } else {
                Log.w(TAG, "No location provider enabled.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted for requesting updates", e);
        }
    }

    public void stopTracking() {
        Log.d(TAG, "stopTracking: start");
        isTracking = false;
        if (myLocationOverlay != null) {
            myLocationOverlay.disableFollowLocation();
        }
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            Log.d(TAG, "stopTracking: removed location updates");
        }
        if (mapView != null && trail != null) {
            try {
                mapView.getOverlays().remove(trail);
                Log.d(TAG, "stopTracking: Trail removed from overlays");
            } catch (Exception e) {
                Log.e(TAG, "Error removing trail overlay", e);
            }
        }
        Log.d(TAG, "stopTracking: end");
    }

    private void recenterMapOnUserLocation() {
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(myLocationOverlay.getMyLocation());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocationOverlay();
            } else {
                Log.w(TAG, "Location permission denied");
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            stopTracking(); // Ensure tracking is stopped before finishing
            Intent resultIntent = new Intent();
            resultIntent.putExtra("total_distance_km", totalDistanceKm);
            setResult(RESULT_OK, resultIntent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        // Re-register for location updates if tracking is enabled
        if (isTracking) {
            try {
                String provider = null;
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                }
                if (provider != null) {
                    locationManager.requestLocationUpdates(provider, 2000, 5, locationListener);
                    Log.d(TAG, "onResume: Re-requested location updates from " + provider);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Location permission not granted for requesting updates", e);
            }
        }
        Log.d(TAG, "onResume: map resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            Log.d(TAG, "onPause: removed location updates");
        }
        Log.d(TAG, "onPause: map paused");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.getOverlays().clear();
            if (myLocationOverlay != null) {
                myLocationOverlay.disableMyLocation();
            }
            mapView.onDetach();
        }
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            Log.d(TAG, "onDestroy: removed location updates");
        }
        Log.d(TAG, "onDestroy: cleanup completed");
    }
}