package fr.ozf.cronos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Removed OSMDroid imports - map moved to separate activity
import android.preference.PreferenceManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import fr.ozf.cronos.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements TimerAdapter.OnTimerClickListener, TimerAdapter.OnTimerFinishListener {

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String TIMER_LIST_KEY = "timerList";
    private static final String CURRENT_ROUND_KEY = "currentRound";
    private static final String TOTAL_ROUNDS_KEY = "totalRounds";
    private static final String CURRENT_TIMER_INDEX_KEY = "currentTimerIndex";
    private static final String SHARED_PREFS_SCHEMES = "sharedPrefsSchemes";
    private static final String SCHEME_NAMES_KEY = "schemeNames";
    private static final String CURRENT_SCHEME_NAME_KEY = "currentSchemeName";
    private static final String DEFAULT_RINGTONE_URI_KEY = "defaultRingtoneUri";
    private static final String COUNTDOWN_BEEP_KEY = "countdownBeepEnabled";
    private static final String KEEP_SCREEN_ON_KEY = "keepScreenOn";
    private static final int RINGTONE_PICKER_REQUEST_CODE = 2;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int MAP_ACTIVITY_REQUEST_CODE = 3;

    private static final String TAG = "HomeFragment";

    private static final String SHARED_PREFS_TRAINING_HISTORY = "sharedPrefsTrainingHistory";
    private static final String TRAINING_HISTORY_LIST_KEY = "trainingHistoryList";

    private FragmentHomeBinding binding;
    private TimerAdapter timerAdapter;
    private List<Timer> timerList;
    private TextView roundDisplayTextView;
    private TextView totalDurationTextView;
    private int currentRound = 1;
    private int totalRounds = 10;
    private int currentTimerIndex = 0;
    private String currentSchemeName = "Default";
    private int editingTimerPosition = -1;
    private long sessionStartTime = 0;

    private Gson gson = new Gson();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private TextToSpeech textToSpeech;
    private ToneGenerator toneGenerator;

    // Map moved to separate MapActivity

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: start");
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();



        roundDisplayTextView = root.findViewById(R.id.round_display_text_view);
        totalDurationTextView = root.findViewById(R.id.total_duration_text_view);

        loadData();

        if (timerList == null) {
            timerList = new ArrayList<>();
        }

        timerAdapter = new TimerAdapter(timerList, this, this, requireContext());
        RecyclerView timerRecyclerView = root.findViewById(R.id.timer_list_recycler_view);
        timerRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        timerRecyclerView.setAdapter(timerAdapter);

        if (!timerList.isEmpty()) {
            resetSession();
            updateRoundDisplay();
            updateTotalDurationDisplay();
        } else {
            currentRound = 1;
            totalRounds = 10;
            currentTimerIndex = 0;
            updateRoundDisplay();
            updateTotalDurationDisplay();
        }

        Button startButton = root.findViewById(R.id.button_start_all);
        Button pauseButton = root.findViewById(R.id.button_pause_all);
        Button stopButton = root.findViewById(R.id.button_stop_all);
        Button resetButton = root.findViewById(R.id.button_reset_all);

        startButton.setOnClickListener(v -> startTimerAtIndex(currentTimerIndex));
        pauseButton.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            if (currentTimerIndex < timerList.size()) {
                timerList.get(currentTimerIndex).setRunning(false);
                timerAdapter.notifyItemChanged(currentTimerIndex);
            }
            timerAdapter.setRunningTimerPosition(-1);
        });
        stopButton.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            if (currentTimerIndex < timerList.size()) {
                Timer timer = timerList.get(currentTimerIndex);
                timer.setRunning(false);
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timerAdapter.notifyItemChanged(currentTimerIndex);
            }
            timerAdapter.setRunningTimerPosition(-1);
        });
        resetButton.setOnClickListener(v -> resetSession());

        roundDisplayTextView.setOnClickListener(v -> showSetTotalRoundsDialog());

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported or missing data");
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed");
            }
        });

        // Initialize ToneGenerator
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);



        // Setup map button
        Button openMapButton = root.findViewById(R.id.button_open_map);
        openMapButton.setOnClickListener(v -> openMapActivity());

        Log.d(TAG, "onCreateView: end");
        return root;
    }
    
    private void openMapActivity() {
        Intent intent = new Intent(requireContext(), MapActivity.class);
        // Pass tracking state if needed
        intent.putExtra("start_tracking", isAnyTimerRunning());
        startActivityForResult(intent, MAP_ACTIVITY_REQUEST_CODE);
        Log.d(TAG, "openMapActivity: launched MapActivity for result");
    }
    
    private boolean isAnyTimerRunning() {
        for (Timer timer : timerList) {
            if (timer.isRunning()) {
                return true;
            }
        }
        return false;
    }

    // Map functionality moved to MapActivity







    @Override
    public void onResume() {
        Log.d(TAG, "onResume: start");
        super.onResume();
        updateKeepScreenOn();
        // Map moved to separate activity
        Log.d(TAG, "onResume: end");
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: start");
        super.onPause();
        saveData();
        // Map moved to separate activity
        Log.d(TAG, "onPause: end");
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: start");
        super.onDestroyView();
        handler.removeCallbacks(timerRunnable);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        // Map moved to separate activity
        binding = null;
        Log.d(TAG, "onDestroyView: end");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: start");
        super.onDestroy();
        // Map moved to separate activity
        Log.d(TAG, "onDestroy: end");
    }

    // Tracking functionality moved to MapActivity



    @Override
    public void onTimerClick(int position, Timer timer) {
        Log.d(TAG, "onTimerClick (for editing) called for position: " + position);
        boolean isSessionActive = false;
        for (Timer t : timerList) {
            if (t.isRunning()) {
                isSessionActive = true;
                break;
            }
            t.setTimeLeftInMillis(t.getTotalTimeInMillis());
            t.setRunning(false);
        }

        if (isSessionActive) {
            Log.d(TAG, "Session active. Resetting session to allow editing timer at " + position);
            resetSession();
            Toast.makeText(requireContext(), "Session reset to allow editing", Toast.LENGTH_SHORT).show();
        }
        showEditTimerDialog(position, timer);
        saveData();
    }

    @Override
    public void onTimerFinish(int position, Timer finishedTimer) {
        Log.d(TAG, "onTimerFinish called for position: " + position + ", timer label: " + finishedTimer.getLabel());

        Uri ringtoneUri = null;
        if (finishedTimer.getRingtoneUri() != null) {
            ringtoneUri = Uri.parse(finishedTimer.getRingtoneUri());
        } else {
            SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
            String defaultRingtoneUriString = sharedPreferences.getString(DEFAULT_RINGTONE_URI_KEY, null);
            if (defaultRingtoneUriString != null) {
                ringtoneUri = Uri.parse(defaultRingtoneUriString);
            } else {
                ringtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            }
        }

        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(requireContext(), ringtoneUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> mp.release());
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone", e);
            MediaPlayer mediaPlayer = MediaPlayer.create(requireContext(), R.raw.notification_sound);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(mp -> mp.release());
            }
        }

        finishedTimer.setRunning(false);
        finishedTimer.setTimeLeftInMillis(finishedTimer.getTotalTimeInMillis());
        timerAdapter.notifyItemChanged(position);

        if (currentTimerIndex < timerList.size() - 1) {
            currentTimerIndex++;
            startTimerAtIndex(currentTimerIndex);
            Log.d(TAG, "Moving to next timer at index: " + currentTimerIndex);
        } else {
            if (currentRound < totalRounds) {
                currentRound++;
                currentTimerIndex = 0;
                updateRoundDisplay();
                startTimerAtIndex(currentTimerIndex);
                Log.d(TAG, "Moving to next round: " + currentRound + ", starting timer at index: " + currentTimerIndex);
            } else {
                Log.d(TAG, "All rounds and timers finished.");
                resetSession();
                Toast.makeText(requireContext(), "All rounds completed!", Toast.LENGTH_LONG).show();

                long sessionDuration = 0;
                if (sessionStartTime != 0) {
                    sessionDuration = System.currentTimeMillis() - sessionStartTime;
                    sessionStartTime = 0; // Reset for next session
                }

                // Placeholder for mean speed, will be updated after MapActivity returns distance
                double meanSpeedKmH = 0.0;

                // Save training session when all rounds are completed
                saveTrainingSession(currentSchemeName, 0.0, sessionDuration, meanSpeedKmH); // Distance and speed will be updated from MapActivity

                MediaPlayer mediaPlayer = MediaPlayer.create(requireContext(), R.raw.tada);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(mp -> mp.release());
                }
            }
        }
        saveData();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null && editingTimerPosition != -1) {
                Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                Timer timer = timerList.get(editingTimerPosition);
                if (uri != null) {
                    timer.setRingtoneUri(uri.toString());

                    try {
                        Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), uri);
                        ringtone.play();
                    } catch (Exception e) {
                    }
                } else {
                    timer.setRingtoneUri(null);
                }
                timerAdapter.notifyItemChanged(editingTimerPosition);
                saveData();
                editingTimerPosition = -1;
            }
        } else if (requestCode == MAP_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                double distance = data.getDoubleExtra("total_distance_km", 0.0);
                Log.d(TAG, "Received distance from MapActivity: " + distance + " km");
                // Update the last saved training session with the actual distance
                updateLastTrainingSessionDistance(distance);
            }
        }
    }

    private void showSetTotalRoundsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Set Total Rounds");

        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(totalRounds));
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                int newTotalRounds = Integer.parseInt(input.getText().toString());
                if (newTotalRounds > 0) {
                    totalRounds = newTotalRounds;
                    if (currentRound > totalRounds) {
                        currentRound = 1;
                    }
                    updateRoundDisplay();
                    updateTotalDurationDisplay();
                    saveData();
                    Toast.makeText(requireContext(), "Total rounds updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Total rounds must be greater than 0", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid number for total rounds", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditTimerDialog(int position, Timer timer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_timer, null);
        builder.setView(dialogView);

        EditText editLabel = dialogView.findViewById(R.id.edit_timer_label);
        EditText editMinutes = dialogView.findViewById(R.id.edit_timer_minutes);
        EditText editSeconds = dialogView.findViewById(R.id.edit_timer_seconds);
        Button selectRingtoneButton = dialogView.findViewById(R.id.select_ringtone_button);
        TextView selectedRingtoneTextView = dialogView.findViewById(R.id.selected_ringtone_text_view);
        Switch speakLabelSwitch = dialogView.findViewById(R.id.switch_speak_label);

        editLabel.setText(timer.getLabel());
        long minutes = (timer.getTotalTimeInMillis() / 1000) / 60;
        long seconds = (timer.getTotalTimeInMillis() / 1000) % 60;
        editMinutes.setText(String.valueOf(minutes));
        editSeconds.setText(String.valueOf(seconds));

        updateRingtoneTextView(selectedRingtoneTextView, timer.getRingtoneUri());

        speakLabelSwitch.setChecked(timer.getSpeakLabelOnStart());
        selectRingtoneButton.setEnabled(!timer.getSpeakLabelOnStart());

        speakLabelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            timer.setSpeakLabelOnStart(isChecked);
            selectRingtoneButton.setEnabled(!isChecked);
            if (isChecked) {
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
            timer.setTimeLeftInMillis(newTotalTimeInMillis);
            timer.setRunning(false);
            timerAdapter.notifyItemChanged(position);

            updateTotalDurationDisplay();
            saveData();
            Toast.makeText(requireContext(), "Timer updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        Button deleteButton = dialogView.findViewById(R.id.delete_timer_button);
        AlertDialog alertDialog = builder.create();
        deleteButton.setOnClickListener(v -> {
            timerList.remove(position);
            timerAdapter.notifyItemRemoved(position);
            alertDialog.dismiss();
            updateTotalDurationDisplay();
            saveData();
            Toast.makeText(requireContext(), "Timer deleted", Toast.LENGTH_SHORT).show();
            if (position == currentTimerIndex) {
                resetSession();
            }
        });

        alertDialog.show();
    }

    public void addDefaultTimer() {
        int newItemPosition = timerList.size();
        timerList.add(new Timer("New Timer", 1 * 60 * 1000));
        timerAdapter.notifyItemInserted(newItemPosition);
        RecyclerView timerRecyclerView = binding.timerListRecyclerView;
        timerRecyclerView.scrollToPosition(newItemPosition);
        updateTotalDurationDisplay();
        saveData();
        Toast.makeText(requireContext(), "Timer added", Toast.LENGTH_SHORT).show();
    }

    private void resetSession() {
        Log.d(TAG, "resetSession called.");
        // Tracking moved to MapActivity
        handler.removeCallbacks(timerRunnable);
        timerRunnable = null; // Ensure runnable is nulled out
        for (int i = 0; i < timerList.size(); i++) {
            Timer timer = timerList.get(i);
            if (timer.isRunning()) {
                Log.d(TAG, "Stopping timer at index " + i + " during resetSession.");
                timer.setRunning(false);
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timerAdapter.notifyItemChanged(i);
            } else {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
            }
        }
        currentRound = 1;
        currentTimerIndex = 0;
        updateRoundDisplay();
        timerAdapter.setRunningTimerPosition(-1);
        timerAdapter.notifyDataSetChanged();
        Log.d(TAG, "resetSession finished. Current round: " + currentRound + ", Current timer index: " + currentTimerIndex);
    }

    private void startTimerAtIndex(int index) {
        Log.d(TAG, "startTimerAtIndex called for index: " + index);
        if (timerList.isEmpty() || index < 0 || index >= timerList.size()) {
            Log.w(TAG, "startTimerAtIndex: Invalid index or empty timer list.");
            return;
        }

        handler.removeCallbacks(timerRunnable);
        Log.d(TAG, "Handler callbacks removed in startTimerAtIndex.");

        for (int i = 0; i < timerList.size(); i++) {
            Timer timer = timerList.get(i);
            if (i == index) {
                timer.setRunning(true);
                Log.d(TAG, "Timer at index " + index + " marked as running. Label: " + timer.getLabel());
            } else {
                timer.setRunning(false);
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                Log.d(TAG, "Timer at index " + i + " marked as not running and reset.");
            }
            timerAdapter.notifyItemChanged(i);
        }

        currentTimerIndex = index;
        timerAdapter.setRunningTimerPosition(currentTimerIndex);
        Log.d(TAG, "currentTimerIndex set to: " + currentTimerIndex);

        if (currentRound == 1 && currentTimerIndex == 0 && sessionStartTime == 0) {
            sessionStartTime = System.currentTimeMillis();
            Log.d(TAG, "Session start time recorded: " + sessionStartTime);
        }

        // Tracking moved to MapActivity - removed isTracking check

        if (timerList.get(currentTimerIndex).getSpeakLabelOnStart()) {
            if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                textToSpeech.speak(timerList.get(currentTimerIndex).getLabel(), TextToSpeech.QUEUE_FLUSH, null, null);
                Log.d(TAG, "Speaking timer label at start: " + timerList.get(currentTimerIndex).getLabel());
            }
        }

        startCountdownRunnable();
        Log.d(TAG, "startCountdownRunnable called.");
    }

    private void startCountdownRunnable() {
        Log.d(TAG, "startCountdownRunnable called.");
        if (timerRunnable == null) {
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "TimerRunnable executing.");
                    Timer runningTimer = null;
                    int runningTimerPosition = -1;
                    for (int i = 0; i < timerList.size(); i++) {
                        if (timerList.get(i).isRunning()) {
                            runningTimer = timerList.get(i);
                            runningTimerPosition = i;
                            Log.d(TAG, "startCountdownRunnable found running timer at index: " + i + " with label: " + runningTimer.getLabel());
                            break;
                        }
                    }

                    if (runningTimer != null) {
                        runningTimer.setTimeLeftInMillis(runningTimer.getTimeLeftInMillis() - 1000);

                        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
                        boolean countdownBeepEnabled = sharedPreferences.getBoolean(COUNTDOWN_BEEP_KEY, true);
                        if (countdownBeepEnabled && runningTimer.getTimeLeftInMillis() <= 5000 && runningTimer.getTimeLeftInMillis() > 0) {
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                        }

                        if (runningTimer.getTimeLeftInMillis() <= 0) {
                            runningTimer.setTimeLeftInMillis(0);
                            runningTimer.setRunning(false);
                            Log.d(TAG, "Timer at index " + runningTimerPosition + " finished.");
                            onTimerFinish(runningTimerPosition, runningTimer);
                        } else {
                            timerAdapter.notifyItemChanged(runningTimerPosition);
                            handler.postDelayed(this, 1000);
                            Log.d(TAG, "Scheduled next tick for timer at " + runningTimerPosition);
                        }
                    } else {
                        handler.removeCallbacks(this);
                        Log.d(TAG, "No running timer found. Stopped runnable.");
                    }
                }
            };
        }
        handler.postDelayed(timerRunnable, 1000);
        Log.d(TAG, "TimerRunnable scheduled for 1000ms.");
    }

    private void updateRingtoneTextView(TextView textView, String ringtoneUriString) {
        if (ringtoneUriString != null) {
            Uri ringtoneUri = Uri.parse(ringtoneUriString);
            Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), ringtoneUri);
            String ringtoneName = ringtone.getTitle(requireContext());
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
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String json = gson.toJson(timerList);
        editor.putString(TIMER_LIST_KEY, json);
        editor.putInt(CURRENT_ROUND_KEY, currentRound);
        editor.putInt(TOTAL_ROUNDS_KEY, totalRounds);
        editor.putInt(CURRENT_TIMER_INDEX_KEY, currentTimerIndex);
        editor.putString(CURRENT_SCHEME_NAME_KEY, currentSchemeName);
        editor.apply();
    }

    private void loadData() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String json = sharedPreferences.getString(TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        timerList = gson.fromJson(json, type);

        currentRound = sharedPreferences.getInt(CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(TOTAL_ROUNDS_KEY, 10);
        currentTimerIndex = sharedPreferences.getInt(CURRENT_TIMER_INDEX_KEY, 0);
        currentSchemeName = sharedPreferences.getString(CURRENT_SCHEME_NAME_KEY, "Default");

        if (timerList != null) {
            for (Timer timer : timerList) {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timer.setRunning(false);
            }
        } else {
            timerList = new ArrayList<>();
        }
    }

    private void saveTrainingSession(String schemeName, double distanceKm, long durationMillis, double meanSpeedKmH) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_TRAINING_HISTORY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String json = sharedPreferences.getString(TRAINING_HISTORY_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<TrainingSession>>() {}.getType();
        List<TrainingSession> historyList = gson.fromJson(json, type);

        if (historyList == null) {
            historyList = new ArrayList<>();
        }

        historyList.add(0, new TrainingSession(schemeName, distanceKm, durationMillis, meanSpeedKmH)); // Add to the beginning of the list

        // Keep only the last 50 sessions to prevent the list from growing indefinitely
        if (historyList.size() > 50) {
            historyList = historyList.subList(0, 50);
        }

        String updatedJson = gson.toJson(historyList);
        editor.putString(TRAINING_HISTORY_LIST_KEY, updatedJson);
        editor.apply();
        Log.d(TAG, "Training session saved: Scheme=" + schemeName + ", Distance=" + distanceKm);
    }

    private void updateLastTrainingSessionDistance(double distanceKm) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_TRAINING_HISTORY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String json = sharedPreferences.getString(TRAINING_HISTORY_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<TrainingSession>>() {}.getType();
        List<TrainingSession> historyList = gson.fromJson(json, type);

        if (historyList != null && !historyList.isEmpty()) {
            // The most recent session is at index 0
            TrainingSession lastSession = historyList.get(0);

            double calculatedMeanSpeedKmH = 0.0;
            if (lastSession.getDurationMillis() > 0) {
                calculatedMeanSpeedKmH = (distanceKm / (lastSession.getDurationMillis() / 1000.0 / 3600.0)); // km/h
            }

            // Create a new TrainingSession with updated distance and mean speed, keeping other fields same
            // This is safer than modifying the object directly if TrainingSession were immutable
            TrainingSession updatedSession = new TrainingSession(lastSession.getSchemeName(), distanceKm, lastSession.getDurationMillis(), calculatedMeanSpeedKmH);
            updatedSession.setTimestamp(lastSession.getTimestamp());
            historyList.set(0, updatedSession);

            String updatedJson = gson.toJson(historyList);
            editor.putString(TRAINING_HISTORY_LIST_KEY, updatedJson);
            editor.apply();
            Log.d(TAG, "Last training session distance updated to: " + distanceKm + " km");
        } else {
            Log.w(TAG, "No training sessions found to update distance.");
        }
    }

    private void updateKeepScreenOn() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        boolean keepScreenOn = sharedPreferences.getBoolean(KEEP_SCREEN_ON_KEY, true);
        if (keepScreenOn) {
            requireActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            requireActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // Method to get the current scheme name (for MainActivity to update its title)
    public String getCurrentSchemeName() {
        return currentSchemeName;
    }

    // Method to update the current scheme name (when loading/saving schemes from MainActivity)
    public void setCurrentSchemeName(String schemeName) {
        this.currentSchemeName = schemeName;
        // Optionally, update UI elements that display the scheme name within the fragment
    }

    // Methods for scheme management (called from MainActivity)
    public void saveScheme(String schemeName) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("sharedPrefsSchemes", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String timerListJson = gson.toJson(timerList);
        editor.putString(schemeName + "_" + TIMER_LIST_KEY, timerListJson);

        editor.putInt(schemeName + "_" + CURRENT_ROUND_KEY, currentRound);
        editor.putInt(schemeName + "_" + TOTAL_ROUNDS_KEY, totalRounds);
        editor.putInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, currentTimerIndex);

        Set<String> schemeNames = sharedPreferences.getStringSet("schemeNames", new HashSet<>());
        schemeNames.add(schemeName);
        editor.putStringSet("schemeNames", schemeNames);

        editor.apply();
    }

    public void loadScheme(String schemeName) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("sharedPrefsSchemes", Context.MODE_PRIVATE);

        String timerListJson = sharedPreferences.getString(schemeName + "_" + TIMER_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<Timer>>() {}.getType();
        timerList = gson.fromJson(timerListJson, type);

        currentRound = sharedPreferences.getInt(schemeName + "_" + CURRENT_ROUND_KEY, 1);
        totalRounds = sharedPreferences.getInt(schemeName + "_" + TOTAL_ROUNDS_KEY, 10);
        currentTimerIndex = sharedPreferences.getInt(schemeName + "_" + CURRENT_TIMER_INDEX_KEY, 0);

        if (timerList != null) {
            for (Timer timer : timerList) {
                timer.setTimeLeftInMillis(timer.getTotalTimeInMillis());
                timer.setRunning(false);
            }
        } else {
            timerList = new ArrayList<>();
        }

        timerAdapter.setTimers(timerList);
        updateRoundDisplay();
        updateTotalDurationDisplay();
    }

    public Set<String> getSavedSchemeNames() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("sharedPrefsSchemes", Context.MODE_PRIVATE);
        return sharedPreferences.getStringSet("schemeNames", new HashSet<>());
    }

    // Method to handle FAB click from MainActivity
    public void onFabClick() {
        addDefaultTimer();
    }
}
