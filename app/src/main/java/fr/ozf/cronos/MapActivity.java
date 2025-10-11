package fr.ozf.cronos;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import android.preference.PreferenceManager;

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
                    mapView.invalidate(); // Redraw the map

                    if (lastLocation != null) {
                        double distance = lastLocation.distanceTo(location) / 1000.0; // in kilometers
                        totalDistanceKm += distance;
                        Log.d(TAG, "Distance updated: " + totalDistanceKm + " km");
                    }
                    lastLocation = location;
                }
            }
        };

        // Check if tracking should be started based on intent extras
        boolean shouldStartTracking = getIntent().getBooleanExtra("start_tracking", false);
        if (shouldStartTracking) {
            startTracking();
        }
        
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
        
        // Set zoom and center to Paris (you can make this configurable)
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(new GeoPoint(48.8583, 2.2945));
        
        // Request location permission if not granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            setupLocationOverlay();
        }
        
        Log.d(TAG, "initializeMap: end");
    }
    
    private void setupLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
        Log.d(TAG, "setupLocationOverlay: location overlay added");
    }
    
    private void startTracking() {
        if (mapView == null || myLocationOverlay == null) {
            Log.e(TAG, "startTracking: map or location overlay is null");
            return;
        }
        
        Log.d(TAG, "startTracking: start");
        isTracking = true;
        
        // Create new trail
        trail = new Polyline(mapView);
        trail.setColor(ContextCompat.getColor(this, R.color.green_primary));
        trail.setWidth(10f);
        
        // Add initial point to prevent null LinearRing issues
        if (myLocationOverlay.getLastFix() != null) {
            GeoPoint currentLocation = new GeoPoint(myLocationOverlay.getLastFix());
            trail.addPoint(currentLocation);
            Log.d(TAG, "startTracking: Added initial point to trail");
        } else {
            // Add map center as fallback
            GeoPoint center = (GeoPoint) mapView.getMapCenter();
            trail.addPoint(center);
            Log.d(TAG, "startTracking: Added map center as initial point");
        }
        
        mapView.getOverlays().add(trail);
        myLocationOverlay.enableFollowLocation();

        // Request location updates
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
            Log.d(TAG, "startTracking: requested GPS location updates");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted for requesting updates", e);
        }

        Log.d(TAG, "startTracking: tracking enabled");
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