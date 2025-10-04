package fr.ozf.cronos;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import fr.ozf.cronos.databinding.ActivityMainBinding;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity implements TimerAdapter.OnTimerClickListener, TimerAdapter.OnTimerFinishListener {

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String TIMER_LIST_KEY = "timerList";
    private static final String CURRENT_ROUND_KEY = "currentRound";
    private static final String TOTAL_ROUNDS_KEY = "totalRounds";
    private static final String SHARED_PREFS_SCHEMES = "sharedPrefsSchemes";
    private static final String SCHEME_NAMES_KEY = "schemeNames";
    private static final String CURRENT_SCHEME_NAME_KEY = "currentSchemeName";

    private ActivityMainBinding binding;
    private TimerAdapter timerAdapter;
    private List<Timer> timerList;
    private TextView roundDisplayTextView;
    private TextView totalDurationTextView;
    private int currentRound = 1;
    private int totalRounds = 10; // Default total rounds
    private String currentSchemeName = "Default";

    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        roundDisplayTextView = findViewById(R.id.round_display_text_view);
        totalDurationTextView = findViewById(R.id.total_duration_text_view);

        loadData(); // Load saved data

        updateRoundDisplay();
        updateTitleBar(); // Initial update of the title bar

        roundDisplayTextView.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Set Total Rounds");

            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setText(String.valueOf(totalRounds));
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                try {
                    int newTotalRounds = Integer.parseInt(input.getText().toString());
                    if (newTotalRounds > 0) {
                        totalRounds = newTotalRounds;
                        if (currentRound > totalRounds) {
                            currentRound = 1; // Reset current round if it exceeds new total
                        }
                        updateRoundDisplay();
                        updateTotalDurationDisplay(); // Update total duration after changing total rounds
                        saveData(); // Save data after changing total rounds
                        Toast.makeText(this, "Total rounds updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Total rounds must be greater than 0", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number for total rounds", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            builder.show();
        });

        RecyclerView timerRecyclerView = findViewById(R.id.timer_list_recycler_view);
        timerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timerAdapter = new TimerAdapter(timerList, this, this, this);
        timerRecyclerView.setAdapter(timerAdapter);

        updateTotalDurationDisplay(); // Initial update of total duration

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int newItemPosition = timerList.size();
                timerList.add(new Timer("New Timer", 1 * 60 * 1000)); // Default 1 minute timer
                timerAdapter.notifyItemInserted(newItemPosition);
                timerRecyclerView.scrollToPosition(newItemPosition);
                updateTotalDurationDisplay(); // Update total duration after adding a timer
                saveData(); // Save data after adding a timer
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_save_scheme) {
            showSaveSchemeDialog();
            return true;
        } else if (id == R.id.action_load_scheme) {
            showLoadSchemeDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTimerClick(int position, Timer timer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_timer, null);
        builder.setView(dialogView);

        EditText editLabel = dialogView.findViewById(R.id.edit_timer_label);
        EditText editMinutes = dialogView.findViewById(R.id.edit_timer_minutes);
        EditText editSeconds = dialogView.findViewById(R.id.edit_timer_seconds);

        editLabel.setText(timer.getLabel());
        long minutes = (timer.getTotalTimeInMillis() / 1000) / 60;
        long seconds = (timer.getTotalTimeInMillis() / 1000) % 60;
        editMinutes.setText(String.valueOf(minutes));
        editSeconds.setText(String.valueOf(seconds));

        builder.setTitle("Edit Timer");
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newLabel = editLabel.getText().toString();
            long newMinutes = Long.parseLong(editMinutes.getText().toString());
            long newSeconds = Long.parseLong(editSeconds.getText().toString());

            long newTotalTimeInMillis = (newMinutes * 60 + newSeconds) * 1000;

            timer.setLabel(newLabel);
            timer.setTotalTimeInMillis(newTotalTimeInMillis);
            timer.setTimeLeftInMillis(newTotalTimeInMillis);
            timer.setRunning(false);
            timerAdapter.notifyItemChanged(position);

            updateTotalDurationDisplay(); // Update total duration after editing a timer
            saveData(); // Save data after editing a timer

            Toast.makeText(this, "Timer updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // Handle delete button
        Button deleteButton = dialogView.findViewById(R.id.delete_timer_button);
        AlertDialog alertDialog = builder.create(); // Create the dialog here
        deleteButton.setOnClickListener(v -> {
            timerList.remove(position);
            timerAdapter.notifyItemRemoved(position);
            alertDialog.dismiss(); // Dismiss the created dialog
            updateTotalDurationDisplay(); // Update total duration after deleting a timer
            saveData(); // Save data after deleting a timer
            Toast.makeText(this, "Timer deleted", Toast.LENGTH_SHORT).show();
        });

        alertDialog.show(); // Show the created dialog
    }

    @Override
    public void onTimerFinish(int position, Timer timer) {
        currentRound++;
        if (currentRound > totalRounds) {
            currentRound = 1; // Reset or handle completion of all rounds
            Toast.makeText(this, "All rounds completed!", Toast.LENGTH_LONG).show();
        }
        updateRoundDisplay();
        saveData(); // Save data after a timer finishes
    }

    private void updateRoundDisplay() {
        roundDisplayTextView.setText(String.format(Locale.getDefault(), "Round %d / %d", currentRound, totalRounds));
    }

    private void updateTotalDurationDisplay() {
        long totalMillis = 0;
        for (Timer timer : timerList) {
            totalMillis += timer.getTotalTimeInMillis();
        }
        totalMillis *= totalRounds;

        long hours = TimeUnit.MILLISECONDS.toHours(totalMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(totalMillis));

        String formattedDuration = String.format(Locale.getDefault(), "Total Duration: %02d:%02d:%02d", hours, minutes, seconds);
        totalDurationTextView.setText(formattedDuration);
    }

    private void saveData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String json = gson.toJson(timerList);
        editor.putString(TIMER_LIST_KEY, json);
        editor.putInt(CURRENT_ROUND_KEY, currentRound);
        editor.putInt(TOTAL_ROUNDS_KEY, totalRounds);
        editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName); // Save current scheme name
        editor.apply();
    }

    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        String json = sharedPreferences.getString(TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        timerList = gson.fromJson(json, type);

        if (timerList == null) {
            timerList = new ArrayList<>();
        }

        currentRound = sharedPreferences.getInt(CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(TOTAL_ROUNDS_KEY, 10);
        currentSchemeName = sharedPreferences.getString(CURRENT_SCHEME_NAME_KEY, "Default"); // Load current scheme name
    }

    private void showSaveSchemeDialog() {
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
                saveScheme(schemeName);
                currentSchemeName = schemeName; // Update current scheme name after saving
                updateTitleBar(); // Update title bar after saving
                Toast.makeText(this, "Scheme '" + schemeName + "' saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Scheme name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveScheme(String schemeName) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Log timer details before saving
        Log.d("SaveScheme", "Saving scheme: " + schemeName);
        for (int i = 0; i < timerList.size(); i++) {
            Log.d("SaveScheme", "Timer " + i + ": label=" + timerList.get(i).getLabel() + ", totalTimeInMillis=" + timerList.get(i).getTotalTimeInMillis());
        }

        // Save timer list
        String timerListJson = gson.toJson(timerList);
        editor.putString(schemeName + "_" + TIMER_LIST_KEY, timerListJson);

        // Save current and total rounds
        editor.putInt(schemeName + "_" + CURRENT_ROUND_KEY, currentRound);
        editor.putInt(schemeName + "_" + TOTAL_ROUNDS_KEY, totalRounds);

        // Save scheme name to a set of all scheme names
        Set<String> schemeNames = sharedPreferences.getStringSet(SCHEME_NAMES_KEY, new HashSet<>());
        schemeNames.add(schemeName);
        editor.putStringSet(SCHEME_NAMES_KEY, schemeNames);

        editor.apply();
    }

    private void showLoadSchemeDialog() {
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
            Toast.makeText(this, "Scheme '" + selectedSchemeName + "' loaded", Toast.LENGTH_SHORT).show();
            // Dismiss the dialog after loading
            ((AlertDialog) parent.getTag()).dismiss();
        });

        builder.setTitle("Load Scheme");
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog alertDialog = builder.create();
        schemeListView.setTag(alertDialog); // Store dialog in tag to dismiss it from item click listener
        alertDialog.show();
    }

    private void loadScheme(String schemeName) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);

        // Load timer list
        String timerListJson = sharedPreferences.getString(schemeName + "_" + TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        timerList = gson.fromJson(timerListJson, type);

        if (timerList == null) {
            timerList = new ArrayList<>();
        } else {
            // Log timer details after loading
            Log.d("LoadScheme", "Loaded scheme: " + schemeName);
            for (int i = 0; i < timerList.size(); i++) {
                Log.d("LoadScheme", "Timer " + i + ": label=" + timerList.get(i).getLabel() + ", totalTimeInMillis=" + timerList.get(i).getTotalTimeInMillis());
            }
            // Ensure timeLeftInMillis is correctly set after deserialization
            for (Timer timer : timerList) {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timer.setRunning(false); // Ensure timers are not running when loaded
            }
        }

        // Load current and total rounds
        currentRound = sharedPreferences.getInt(schemeName + "_" + CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(schemeName + "_" + TOTAL_ROUNDS_KEY, 10);

        // Update UI
        timerAdapter.setTimers(timerList); // Use the new setTimers method
        updateRoundDisplay();
        updateTotalDurationDisplay();
    }

    private Set<String> getSavedSchemeNames() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_SCHEMES, MODE_PRIVATE);
        return sharedPreferences.getStringSet(SCHEME_NAMES_KEY, new HashSet<>());
    }

    private void updateTitleBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name) + " - " + currentSchemeName);
        }
    }
}