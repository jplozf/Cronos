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

    private LocationManager locationManager;


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
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        initializeMap();


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

        if (locationManager == null) {
            Log.e(TAG, "initializeMap: locationManager is null. Cannot get last known location.");
            // Fallback to Paris if locationManager is not available
            mapView.getController().setCenter(new GeoPoint(48.8583, 2.2945));
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
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
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
        Log.d(TAG, "onDestroy: cleanup completed");
    }
}