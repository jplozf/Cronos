package fr.ozf.cronos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import fr.ozf.cronos.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity implements TimerAdapter.OnTimerClickListener, TimerAdapter.OnTimerFinishListener {

    private ActivityMainBinding binding;
    private TimerAdapter timerAdapter;
    private List<Timer> timerList;
    private TextView roundDisplayTextView;
    private TextView totalDurationTextView;
    private int currentRound = 1;
    private int totalRounds = 10; // Default total rounds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        roundDisplayTextView = findViewById(R.id.round_display_text_view);
        totalDurationTextView = findViewById(R.id.total_duration_text_view);
        updateRoundDisplay();

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

        timerList = new ArrayList<>();
        timerList.add(new Timer("Walk", 2 * 60 * 1000)); // 2 minutes
        timerList.add(new Timer("Run", 3 * 60 * 1000)); // 3 minutes

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
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
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
}