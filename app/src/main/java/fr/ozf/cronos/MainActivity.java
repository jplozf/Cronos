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

public class MainActivity extends AppCompatActivity {

    private static final String SHARED_PREFS_SCHEMES = "sharedPrefsSchemes";
    private static final String SCHEME_NAMES_KEY = "schemeNames";
    private static final String CURRENT_SCHEME_NAME_KEY = "currentSchemeName";
    private static final String TIMER_LIST_KEY = "timerList";
    private static final String CURRENT_ROUND_KEY = "currentRound";
    private static final String TOTAL_ROUNDS_KEY = "totalRounds";
    private static final String CURRENT_TIMER_INDEX_KEY = "currentTimerIndex";

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
                    ((HomeFragment) currentFragment).saveScheme(schemeName);
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
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (currentFragment instanceof HomeFragment) {
                ((HomeFragment) currentFragment).loadScheme(selectedSchemeName);
                currentSchemeName = selectedSchemeName; // Update current scheme name after loading
                updateTitleBar(); // Update title bar after loading
                // Save current scheme name to shared preferences
                SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName);
                editor.apply();
                Toast.makeText(this, "Scheme '" + selectedSchemeName + "' loaded", Toast.LENGTH_SHORT).show();
            }
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