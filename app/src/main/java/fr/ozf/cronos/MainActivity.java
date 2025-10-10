package fr.ozf.cronos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech; // Import TextToSpeech
import android.widget.Switch; // Import Switch
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import android.preference.PreferenceManager;
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

public class MainActivity extends AppCompatActivity implements TimerAdapter.OnTimerClickListener, TimerAdapter.OnTimerFinishListener {

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String TIMER_LIST_KEY = "timerList";
    private static final String CURRENT_ROUND_KEY = "currentRound";
    private static final String TOTAL_ROUNDS_KEY = "totalRounds";
    private static final String CURRENT_TIMER_INDEX_KEY = "currentTimerIndex"; // Key for saving current timer index
    private static final String SHARED_PREFS_SCHEMES = "sharedPrefsSchemes";
    private static final String SCHEME_NAMES_KEY = "schemeNames";
    private static final String CURRENT_SCHEME_NAME_KEY = "currentSchemeName";
    private static final String DEFAULT_RINGTONE_URI_KEY = "defaultRingtoneUri";
    private static final int RINGTONE_PICKER_REQUEST_CODE = 2; // Different from SettingsActivity's request code
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;


    private static final String TAG = "MainActivity"; // Tag for logging

    private ActivityMainBinding binding;
    private TimerAdapter timerAdapter;
    private List<Timer> timerList;
    private TextView roundDisplayTextView;
    private TextView totalDurationTextView;
    private int currentRound = 1;
    private int totalRounds = 10; // Default total rounds
    private int currentTimerIndex = 0; // Index of the timer currently running or to be started next
    private String currentSchemeName = "Default";
    private int editingTimerPosition = -1; // To keep track of which timer is being edited

    private Gson gson = new Gson();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private TextToSpeech textToSpeech; // Declare TextToSpeech object

    private MapView osmdroidMapView;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline trail;
    private boolean isTracking = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Important! Set your user agent to prevent getting banned from the OSM servers
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        roundDisplayTextView = findViewById(R.id.round_display_text_view);
        totalDurationTextView = findViewById(R.id.total_duration_text_view);

        loadData(); // Load saved data

        // Initialize timer list if null
        if (timerList == null) {
            timerList = new ArrayList<>();
        }

        // Initialize adapter
        timerAdapter = new TimerAdapter(timerList, this, this, this);
        RecyclerView timerRecyclerView = findViewById(R.id.timer_list_recycler_view);
        timerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timerRecyclerView.setAdapter(timerAdapter);

        // Set initial state based on loaded data or defaults
        if (!timerList.isEmpty()) {
            // If timers exist, reset them and set initial index
            resetSession(); // This will set currentRound=1, currentTimerIndex=0, and reset all timers
            // Ensure the display reflects the loaded state
            updateRoundDisplay();
            updateTotalDurationDisplay();
        } else {
            // If no timers, use default values
            currentRound = 1;
            totalRounds = 10;
            currentTimerIndex = 0;
            updateRoundDisplay();
            updateTotalDurationDisplay();
        }

        Button startButton = findViewById(R.id.button_start_all);
        Button pauseButton = findViewById(R.id.button_pause_all);
        Button stopButton = findViewById(R.id.button_stop_all);
        Button resetButton = findViewById(R.id.button_reset_all);

        startButton.setOnClickListener(v -> startTimerAtIndex(currentTimerIndex));
        pauseButton.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            if (currentTimerIndex < timerList.size()) {
                timerList.get(currentTimerIndex).setRunning(false);
                timerAdapter.notifyItemChanged(currentTimerIndex);
            }
            timerAdapter.setRunningTimerPosition(-1); // Clear highlight on pause
        });
        stopButton.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            if (currentTimerIndex < timerList.size()) {
                Timer timer = timerList.get(currentTimerIndex);
                timer.setRunning(false);
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timerAdapter.notifyItemChanged(currentTimerIndex);
            }
            timerAdapter.setRunningTimerPosition(-1); // Clear highlight on stop
        });
        resetButton.setOnClickListener(v -> resetSession());

        roundDisplayTextView.setOnClickListener(v -> showSetTotalRoundsDialog());

        updateTitleBar(); // Initial update of the title bar

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // FAB is now ONLY for adding new timers
                addDefaultTimer();
                saveData();
            }
        });

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported or missing data");
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed");
            }
        });

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        osmdroidMapView = findViewById(R.id.map_view);
        osmdroidMapView.setTileSource(TileSourceFactory.MAPNIK);
        osmdroidMapView.setBuiltInZoomControls(true);
        osmdroidMapView.setMultiTouchControls(true);

        // Set a default starting position and zoom level
        osmdroidMapView.getController().setZoom(15.0);
        osmdroidMapView.getController().setCenter(new GeoPoint(48.8583, 2.2945)); // Eiffel Tower as a default

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), osmdroidMapView);
        myLocationOverlay.enableMyLocation();
        osmdroidMapView.getOverlays().add(myLocationOverlay);
    }



    private void startTracking() {
        isTracking = true;
        osmdroidMapView.getOverlay().clear();
        trail = new Polyline(osmdroidMapView);
        trail.setColor(getColor(R.color.purple_500));
        trail.setWidth(10f);
        osmdroidMapView.getOverlays().add(trail);

        myLocationOverlay.enableFollowLocation();
    }

    private void stopTracking() {
        isTracking = false;
        if (myLocationOverlay != null) {
            myLocationOverlay.disableFollowLocation();
            myLocationOverlay.disableMyLocation();
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();

        saveData();
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop any running timers and remove callbacks to prevent memory leaks
        handler.removeCallbacks(timerRunnable);
        // Shutdown TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
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

    // Method to show the dialog for setting total rounds
    private void showSetTotalRoundsDialog() {
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
    }

    private void showEditTimerDialog(int position, Timer timer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_timer, null);
        builder.setView(dialogView);

        EditText editLabel = dialogView.findViewById(R.id.edit_timer_label);
        EditText editMinutes = dialogView.findViewById(R.id.edit_timer_minutes);
        EditText editSeconds = dialogView.findViewById(R.id.edit_timer_seconds);
        Button selectRingtoneButton = dialogView.findViewById(R.id.select_ringtone_button);
        TextView selectedRingtoneTextView = dialogView.findViewById(R.id.selected_ringtone_text_view);
        Switch speakLabelSwitch = dialogView.findViewById(R.id.switch_speak_label); // Find the new switch

        editLabel.setText(timer.getLabel());
        long minutes = (timer.getTotalTimeInMillis() / 1000) / 60;
        long seconds = (timer.getTotalTimeInMillis() / 1000) % 60;
        editMinutes.setText(String.valueOf(minutes));
        editSeconds.setText(String.valueOf(seconds));

        updateRingtoneTextView(selectedRingtoneTextView, timer.getRingtoneUri());

        // Set initial state of the switch
        speakLabelSwitch.setChecked(timer.getSpeakLabelOnStart());
        // Enable/disable ringtone selection based on switch state
        selectRingtoneButton.setEnabled(!timer.getSpeakLabelOnStart());

        speakLabelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            timer.setSpeakLabelOnStart(isChecked);
            selectRingtoneButton.setEnabled(!isChecked);
            if (isChecked) {
                // If speaking is enabled, clear any custom ringtone to avoid confusion
                timer.setRingtoneUri(null);
                updateRingtoneTextView(selectedRingtoneTextView, null);
            }
        });

        selectRingtoneButton.setOnClickListener(v -> {
            editingTimerPosition = position;
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Timer Sound");
            Uri currentUri = timer.getRingtoneUri() != null ? Uri.parse(timer.getRingtoneUri()) : Settings.System.DEFAULT_NOTIFICATION_URI;
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri);
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST_CODE);
        });

        builder.setTitle("Edit Timer");
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newLabel = editLabel.getText().toString();
            long newMinutes = Long.parseLong(editMinutes.getText().toString());
            long newSeconds = Long.parseLong(editSeconds.getText().toString());

            long newTotalTimeInMillis = (newMinutes * 60 + newSeconds) * 1000;

            timer.setLabel(newLabel);
            timer.setTotalTimeInMillis(newTotalTimeInMillis);
            // When editing, reset the timer to its full duration and stop it.
            timer.setTimeLeftInMillis(newTotalTimeInMillis);
            timer.setRunning(false);
            timerAdapter.notifyItemChanged(position);

            updateTotalDurationDisplay();
            saveData();
            Toast.makeText(this, "Timer updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // Handle delete button
        Button deleteButton = dialogView.findViewById(R.id.delete_timer_button);
        AlertDialog alertDialog = builder.create();
        deleteButton.setOnClickListener(v -> {
            timerList.remove(position);
            timerAdapter.notifyItemRemoved(position);
            alertDialog.dismiss();
            updateTotalDurationDisplay();
            saveData();
            Toast.makeText(this, "Timer deleted", Toast.LENGTH_SHORT).show();
            // If the deleted timer was the current active timer, reset the session or start the next one.
            if (position == currentTimerIndex) {
                resetSession(); // Simplest approach: reset the whole session
            }
        });

        alertDialog.show();
    }

    // Method to add a default timer
    private void addDefaultTimer() {
        int newItemPosition = timerList.size();
        timerList.add(new Timer("New Timer", 1 * 60 * 1000)); // Default 1 minute timer
        timerAdapter.notifyItemInserted(newItemPosition);
        RecyclerView timerRecyclerView = findViewById(R.id.timer_list_recycler_view);
        timerRecyclerView.scrollToPosition(newItemPosition);
        updateTotalDurationDisplay();
        saveData();
        Toast.makeText(this, "Timer added", Toast.LENGTH_SHORT).show();
    }

    // Method to reset the entire session
    private void resetSession() {
        Log.d(TAG, "resetSession called.");
        stopTracking();
        // Stop all timers and remove callbacks
        handler.removeCallbacks(timerRunnable);
        for (int i = 0; i < timerList.size(); i++) {
            Timer timer = timerList.get(i);
            if (timer.isRunning()) {
                Log.d(TAG, "Stopping timer at index " + i + " during resetSession.");
                timer.setRunning(false);
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis()); // Reset time
                timerAdapter.notifyItemChanged(i); // Update UI for the stopped timer
            } else {
                // Also reset time for timers that were not running
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
            }
        }
        currentRound = 1;
        currentTimerIndex = 0;
        updateRoundDisplay();
        timerAdapter.setRunningTimerPosition(-1); // Clear highlight
        timerAdapter.notifyDataSetChanged(); // Refresh all timers' UI
        Log.d(TAG, "resetSession finished. Current round: " + currentRound + ", Current timer index: " + currentTimerIndex);
    }

    // Method to start a specific timer by index and its countdown
    private void startTimerAtIndex(int index) {
        Log.d(TAG, "startTimerAtIndex called for index: " + index);
        if (timerList.isEmpty() || index < 0 || index >= timerList.size()) {
            Log.w(TAG, "startTimerAtIndex: Invalid index or empty timer list.");
            return; // No timers or invalid index
        }

        // --- Ensure only the target timer is marked as running --- 
        // This loop explicitly sets the running state for all timers.
        handler.removeCallbacks(timerRunnable); // Stop any pending countdowns
        Log.d(TAG, "Handler callbacks removed in startTimerAtIndex.");

        for (int i = 0; i < timerList.size(); i++) {
            Timer timer = timerList.get(i);
            if (i == index) {
                // This is the timer we want to start
                timer.setRunning(true); // Explicitly set to true
                Log.d(TAG, "Timer at index " + index + " marked as running. Label: " + timer.getLabel());
            } else {
                // This is another timer, ensure it's stopped and reset
                timer.setRunning(false); // Explicitly set to false
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis()); // Revert to preset time
                Log.d(TAG, "Timer at index " + i + " marked as not running and reset.");
            }
            timerAdapter.notifyItemChanged(i); // Update UI for all timers
        }
        // --- End of state setting --- 

        // Update the current timer index
        currentTimerIndex = index;
        timerAdapter.setRunningTimerPosition(currentTimerIndex); // Highlight the running timer
        Log.d(TAG, "currentTimerIndex set to: " + currentTimerIndex);

        if (currentTimerIndex == 0 && currentRound == 1 && !isTracking) {
            startTracking();
        }

        // Speak the timer label if enabled
        if (timerList.get(currentTimerIndex).getSpeakLabelOnStart()) {
            if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                textToSpeech.speak(timerList.get(currentTimerIndex).getLabel(), TextToSpeech.QUEUE_FLUSH, null, null);
                Log.d(TAG, "Speaking timer label at start: " + timerList.get(currentTimerIndex).getLabel());
            }
        }

        // Start the countdown runnable
        startCountdownRunnable();
        Log.d(TAG, "startCountdownRunnable called.");
    }

    // Method to start the countdown runnable
    private void startCountdownRunnable() {
        Log.d(TAG, "startCountdownRunnable called.");
        if (timerRunnable == null) {
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "TimerRunnable executing.");
                    // Find the currently running timer
                    Timer runningTimer = null;
                    int runningTimerPosition = -1;
                    for (int i = 0; i < timerList.size(); i++) {
                        if (timerList.get(i).isRunning()) { // <-- This check ensures only one timer is processed
                            runningTimer = timerList.get(i);
                            runningTimerPosition = i;
                            Log.d(TAG, "startCountdownRunnable found running timer at index: " + i + " with label: " + runningTimer.getLabel());
                            break; // <-- This break is crucial. It stops after finding the first one.
                        }
                    }

                    if (runningTimer != null) {
                        runningTimer.setTimeLeftInMillis(runningTimer.getTimeLeftInMillis() - 1000); // Decrement by 1 second

                        if (runningTimer.getTimeLeftInMillis() <= 0) {
                            runningTimer.setTimeLeftInMillis(0);
                            runningTimer.setRunning(false);
                            Log.d(TAG, "Timer at index " + runningTimerPosition + " finished.");
                            // Call onTimerFinish here, passing the position and the timer object
                            onTimerFinish(runningTimerPosition, runningTimer);
                        } else {
                            // Update the UI for the running timer
                            timerAdapter.notifyItemChanged(runningTimerPosition);
                            // Schedule the next tick
                            handler.postDelayed(this, 1000);
                            Log.d(TAG, "Scheduled next tick for timer at " + runningTimerPosition);
                        }
                    } else {
                        // No timer is running, stop the runnable
                        handler.removeCallbacks(this);
                        Log.d(TAG, "No running timer found. Stopped runnable.");
                    }
                }
            };
        }
        handler.postDelayed(timerRunnable, 1000);
        Log.d(TAG, "TimerRunnable scheduled for 1000ms.");
    }

    public void onTimerClick(int position, Timer timer) {
        Log.d(TAG, "onTimerClick (for editing) called for position: " + position);
        // When a timer item is clicked (not the start/stop button), open the edit dialog.
        // First, ensure no timer is running to prevent unexpected behavior during editing.
        boolean isSessionActive = false;
        for (Timer t : timerList) {
            if (t.isRunning()) {
                isSessionActive = true;
                break;
            }
            // Also reset any timers that were running but are now stopped (e.g., due to app restart)
            t.setTimeLeftInMillis(t.getTotalTimeInMillis());
            t.setRunning(false);
        }

        if (isSessionActive) {
            Log.d(TAG, "Session active. Resetting session to allow editing timer at " + position);
            resetSession(); // Resetting the session is the safest way to ensure no timer is running.
            Toast.makeText(this, "Session reset to allow editing", Toast.LENGTH_SHORT).show();
        }
        showEditTimerDialog(position, timer);
        saveData(); // Save state after interaction
    }
    public void onTimerFinish(int position, Timer finishedTimer) {
        Log.d(TAG, "onTimerFinish called for position: " + position + ", timer label: " + finishedTimer.getLabel());

        // Always play notification sound at the end of the timer
        Uri ringtoneUri = null;
        if (finishedTimer.getRingtoneUri() != null) {
            ringtoneUri = Uri.parse(finishedTimer.getRingtoneUri());
        } else {
            SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
            String defaultRingtoneUriString = sharedPreferences.getString(DEFAULT_RINGTONE_URI_KEY, null);
            if (defaultRingtoneUriString != null) {
                ringtoneUri = Uri.parse(defaultRingtoneUriString);
            } else {
                ringtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            }
        }

        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> mp.release());
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone", e);
            // Fallback to default sound if custom ringtone fails
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.notification_sound);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(mp -> mp.release());
            }
        }

        // Mark the finished timer as not running and reset its time
        finishedTimer.setRunning(false);
        finishedTimer.setTimeLeftInMillis(finishedTimer.getTotalTimeInMillis());
        timerAdapter.notifyItemChanged(position);

        // Move to the next timer or next round
        if (currentTimerIndex < timerList.size() - 1) {
            // Start the next timer in the list
            currentTimerIndex++;
            startTimerAtIndex(currentTimerIndex);
            Log.d(TAG, "Moving to next timer at index: " + currentTimerIndex);
        } else {
            // All timers in the current round are finished
            if (currentRound < totalRounds) {
                // Advance to the next round
                currentRound++;
                currentTimerIndex = 0; // Reset to the first timer for the new round
                updateRoundDisplay();
                // Start the first timer of the new round
                startTimerAtIndex(currentTimerIndex);
                Log.d(TAG, "Moving to next round: " + currentRound + ", starting timer at index: " + currentTimerIndex);
            } else {
                // All rounds and all timers are finished
                Log.d(TAG, "All rounds and timers finished.");
                resetSession(); // Reset the session to initial state
                Toast.makeText(this, "All rounds completed!", Toast.LENGTH_LONG).show();
            }
        }
        saveData(); // Save state after interaction
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null && editingTimerPosition != -1) {
                Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                Timer timer = timerList.get(editingTimerPosition);
                if (uri != null) {
                    timer.setRingtoneUri(uri.toString());

                    // Play a preview of the selected ringtone
                    try {
                        Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                        ringtone.play();
                    } catch (Exception e) {
                        // Handle exceptions
                    }
                } else {
                    timer.setRingtoneUri(null); // Allow setting back to default
                }
                timerAdapter.notifyItemChanged(editingTimerPosition);
                saveData();
                editingTimerPosition = -1; // Reset position
            }
        }
    }

    private void updateRingtoneTextView(TextView textView, String ringtoneUriString) {
        if (ringtoneUriString != null) {
            Uri ringtoneUri = Uri.parse(ringtoneUriString);
            Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            String ringtoneName = ringtone.getTitle(this);
            textView.setText("Ringtone: " + ringtoneName);
        } else {
            textView.setText("Ringtone: Default");
        }
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
        editor.putInt(CURRENT_TIMER_INDEX_KEY, currentTimerIndex); // Save current timer index
        editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName); // Save current scheme name
        editor.apply();
    }

    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        String json = sharedPreferences.getString(TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        timerList = gson.fromJson(json, type);

        currentRound = sharedPreferences.getInt(CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(TOTAL_ROUNDS_KEY, 10);
        currentTimerIndex = sharedPreferences.getInt(CURRENT_TIMER_INDEX_KEY, 0); // Load current timer index
        currentSchemeName = sharedPreferences.getString(CURRENT_SCHEME_NAME_KEY, "Default"); // Load current scheme name

        if (timerList != null) {
            // Ensure timeLeftInMillis is correctly set after deserialization and timers are not running
            for (Timer timer : timerList) {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timer.setRunning(false);
            }
        } else {
            timerList = new ArrayList<>(); // Initialize if null
        }
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

        // Save timer list
        String timerListJson = gson.toJson(timerList);
        editor.putString(schemeName + "_" + TIMER_LIST_KEY, timerListJson);

        // Save current and total rounds
        editor.putInt(schemeName + "_" + CURRENT_ROUND_KEY, currentRound);
        editor.putInt(schemeName + "_" + TOTAL_ROUNDS_KEY, totalRounds);
        editor.putInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, currentTimerIndex); // Save current timer index

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

        // Load current and total rounds
        currentRound = sharedPreferences.getInt(schemeName + "_" + CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(schemeName + "_" + TOTAL_ROUNDS_KEY, 10);
        currentTimerIndex = sharedPreferences.getInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, 0); // Load current timer index

        if (timerList != null) {
            // Ensure timeLeftInMillis is correctly set after deserialization and timers are not running
            for (Timer timer : timerList) {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timer.setRunning(false);
            }
        } else {
            timerList = new ArrayList<>(); // Initialize if null
        }

        // Update UI
        timerAdapter.setTimers(timerList); // Assuming TimerAdapter has a setTimers method
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
