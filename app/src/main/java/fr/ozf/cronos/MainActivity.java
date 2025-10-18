package fr.ozf.cronos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osmdroid.config.Configuration;
import android.preference.PreferenceManager;

import fr.ozf.cronos.databinding.ActivityMainBinding;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class MainActivity extends AppCompatActivity {

    private static final String SHARED_PREFS_SCHEMES = "sharedPrefsSchemes";
    private static final String SCHEME_NAMES_KEY = "schemeNames";
    private static final String CURRENT_SCHEME_NAME_KEY = "currentSchemeName";
    private static final String TIMER_LIST_KEY = "timerList";
    private static final String CURRENT_ROUND_KEY = "currentRound";
    private static final String TOTAL_ROUNDS_KEY = "totalRounds";
    private static final String CURRENT_TIMER_INDEX_KEY = "currentTimerIndex";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private ActivityMainBinding binding;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private String currentSchemeName = "Default";
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MainActivity", "onCreate: start");
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Important! Set your user agent to prevent getting banned from the OSM servers
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setSupportActionBar(binding.toolbar);

        viewPager = binding.viewPager;
        tabLayout = binding.tabLayout;

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);



        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Training");
                    break;
                case 1:
                    tab.setText("History");
                    break;
                case 2:
                    tab.setText("Settings");
                    break;
            }
        }).attach();

        // Load the current scheme name to update the title bar
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
        currentSchemeName = sharedPreferences.getString(CURRENT_SCHEME_NAME_KEY, "Default");
        updateTitleBar();

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("MainActivity", "FAB clicked");
                // Only allow FAB click on the HomeFragment tab
                if (viewPager.getCurrentItem() == 0) {
                    Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
                    if (currentFragment instanceof HomeFragment) {
                        ((HomeFragment) currentFragment).onFabClick();
                    }
                }
            }
        });

        // Hide FAB for other tabs
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 0) {
                    binding.fab.show();
                } else {
                    binding.fab.hide();
                }
            }
        });
        Log.d("MainActivity", "onCreate: end");

        checkLocationPermissions();
    }

    private void checkLocationPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        // For Android 14 (API level 34) and above, FOREGROUND_SERVICE_LOCATION is required for location foreground services.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d("MainActivity", "Location permissions already granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Location permissions granted.");
                Toast.makeText(this, "Location permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.w("MainActivity", "Location permissions denied.");
                Toast.makeText(this, "Location permissions denied. Some features may not work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save_scheme) {
            showSaveSchemeDialog();
            return true;
        } else if (id == R.id.action_load_scheme) {
            showLoadSchemeDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    return new TrainingHistoryFragment();
                case 2:
                    return new SettingsTabFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private void showSaveSchemeDialog() {
        Log.d("MainActivity", "showSaveSchemeDialog: start");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_save_scheme, null);
        builder.setView(dialogView);

        EditText editSchemeName = dialogView.findViewById(R.id.edit_scheme_name);
        editSchemeName.setText(currentSchemeName); // Set current scheme name as default

        builder.setTitle("Save Scheme");
        builder.setPositiveButton("Save", (dialog, which) -> {
            String schemeName = editSchemeName.getText().toString().trim();
            if (!schemeName.isEmpty()) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
                if (currentFragment instanceof HomeFragment) {
                    saveScheme(schemeName);
                    currentSchemeName = schemeName; // Update current scheme name after saving
                    updateTitleBar(); // Update title bar after saving
                    // Save current scheme name to shared preferences
                    SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName);
                    editor.apply();
                    Toast.makeText(this, "Scheme '" + schemeName + "' saved", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Scheme name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
        Log.d("MainActivity", "showSaveSchemeDialog: end");
    }

    private void showLoadSchemeDialog() {
        Log.d("MainActivity", "showLoadSchemeDialog: start");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_load_scheme, null);
        builder.setView(dialogView);

        ListView schemeListView = dialogView.findViewById(R.id.scheme_list_view);
        List<String> schemeNames = new ArrayList<>(getSavedSchemeNames());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, schemeNames);
        schemeListView.setAdapter(adapter);

        schemeListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSchemeName = schemeNames.get(position);
            loadScheme(selectedSchemeName);
            currentSchemeName = selectedSchemeName; // Update current scheme name after loading
            updateTitleBar(); // Update title bar after loading
            // Save current scheme name to shared preferences
            SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName);
            editor.apply();
            Toast.makeText(this, "Scheme '" + selectedSchemeName + "' loaded", Toast.LENGTH_SHORT).show();
            // Dismiss the dialog after loading
            ((AlertDialog) parent.getTag()).dismiss();
        });

        builder.setTitle("Load Scheme");
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog alertDialog = builder.create();
        schemeListView.setTag(alertDialog); // Store dialog in tag to dismiss it from item click listener
        alertDialog.show();
        Log.d("MainActivity", "showLoadSchemeDialog: end");
    }

    public String getCurrentSchemeName() {
        return currentSchemeName;
    }

    // Methods for scheme management
    public void saveScheme(String schemeName) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Get the current HomeFragment to retrieve its timerList and other state
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment instanceof HomeFragment) {
            HomeFragment homeFragment = (HomeFragment) currentFragment;
            String timerListJson = gson.toJson(homeFragment.getTimerList());
            editor.putString(schemeName + "_" + TIMER_LIST_KEY, timerListJson);

            editor.putInt(schemeName + "_" + CURRENT_ROUND_KEY, homeFragment.getCurrentRound());
            editor.putInt(schemeName + "_" + TOTAL_ROUNDS_KEY, homeFragment.getTotalRounds());
            editor.putInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, homeFragment.getCurrentTimerIndex());
        } else {
            Log.e("MainActivity", "Attempted to save scheme from a non-HomeFragment: " + currentFragment.getClass().getSimpleName());
            Toast.makeText(this, "Cannot save scheme from this tab. Please switch to Training tab.", Toast.LENGTH_LONG).show();
            return; // Exit the method if not on HomeFragment
        }

        Set<String> schemeNames = sharedPreferences.getStringSet(SCHEME_NAMES_KEY, new HashSet<>());
        schemeNames.add(schemeName);
        editor.putStringSet(SCHEME_NAMES_KEY, schemeNames);

        editor.apply();
    }

    public void loadScheme(String schemeName) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);

        String timerListJson = sharedPreferences.getString(schemeName + "_" + TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        List<Timer> loadedTimerList = gson.fromJson(timerListJson, type);

        int loadedCurrentRound = sharedPreferences.getInt(schemeName + "_" + CURRENT_ROUND_KEY, 1);
        int loadedTotalRounds = sharedPreferences.getInt(schemeName + "_" + TOTAL_ROUNDS_KEY, 10);
        int loadedCurrentTimerIndex = sharedPreferences.getInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, 0);

        // Update the HomeFragment with the loaded data
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment instanceof HomeFragment) {
            HomeFragment homeFragment = (HomeFragment) currentFragment;
            homeFragment.setTimers(loadedTimerList);
            homeFragment.setCurrentRound(loadedCurrentRound);
            homeFragment.setTotalRounds(loadedTotalRounds);
            homeFragment.setCurrentTimerIndex(loadedCurrentTimerIndex);
            homeFragment.updateRoundDisplay();
            homeFragment.updateTotalDurationDisplay();
            homeFragment.resetSession(); // Reset session to apply new scheme
        } else {
            Log.e("MainActivity", "Attempted to load scheme on a non-HomeFragment: " + currentFragment.getClass().getSimpleName());
            Toast.makeText(this, "Cannot load scheme on this tab. Please switch to Training tab.", Toast.LENGTH_LONG).show();
        }
    }

    private Set<String> getSavedSchemeNames() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
        return sharedPreferences.getStringSet(SCHEME_NAMES_KEY, new HashSet<>());
    }

    private void updateTitleBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name) + " : " + currentSchemeName);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Pass the result to the currently active fragment
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}