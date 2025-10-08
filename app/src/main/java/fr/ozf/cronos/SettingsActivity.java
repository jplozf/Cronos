package fr.ozf.cronos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String DEFAULT_RINGTONE_URI_KEY = "defaultRingtoneUri";
    private static final int RINGTONE_PICKER_REQUEST_CODE = 1;

    private Button selectRingtoneButton;
    private TextView selectedRingtoneTextView;

    private Uri selectedRingtoneUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        selectRingtoneButton = findViewById(R.id.select_ringtone_button);
        selectedRingtoneTextView = findViewById(R.id.selected_ringtone_text_view);

        loadDefaultRingtone();

        selectRingtoneButton.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri);
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST_CODE);
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                selectedRingtoneUri = uri;
                saveDefaultRingtone(selectedRingtoneUri);
                updateSelectedRingtoneTextView();
                Toast.makeText(this, "Default ringtone updated", Toast.LENGTH_SHORT).show();

                // Play a preview of the selected ringtone
                try {
                    Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                    ringtone.play();
                } catch (Exception e) {
                    // Handle exceptions, e.g., if the ringtone file is not accessible
                    e.printStackTrace(); // Log the exception for debugging
                }
            }
        }
    }

    private void loadDefaultRingtone() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        String ringtoneUriString = sharedPreferences.getString(DEFAULT_RINGTONE_URI_KEY, null);
        if (ringtoneUriString != null) {
            selectedRingtoneUri = Uri.parse(ringtoneUriString);
        } else {
            // If no ringtone is set, use the system default notification sound
            selectedRingtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        }
        updateSelectedRingtoneTextView();
    }

    private void saveDefaultRingtone(Uri ringtoneUri) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(DEFAULT_RINGTONE_URI_KEY, ringtoneUri.toString());
        editor.apply();
    }

    private void updateSelectedRingtoneTextView() {
        if (selectedRingtoneUri != null) {
            Ringtone ringtone = RingtoneManager.getRingtone(this, selectedRingtoneUri);
            String ringtoneName = ringtone.getTitle(this);
            selectedRingtoneTextView.setText("Selected Ringtone: " + ringtoneName);
        } else {
            selectedRingtoneTextView.setText("No ringtone selected");
        }
    }
}
