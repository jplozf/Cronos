package fr.ozf.cronos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import fr.ozf.cronos.databinding.FragmentSettingsTabBinding;

public class SettingsTabFragment extends Fragment {

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String DEFAULT_RINGTONE_URI_KEY = "defaultRingtoneUri";
    private static final String COUNTDOWN_BEEP_KEY = "countdownBeepEnabled";
    private static final String KEEP_SCREEN_ON_KEY = "keepScreenOn";
    private static final String HALFWAY_WARNING_KEY = "halfwayWarningEnabled";
    private static final int RINGTONE_PICKER_REQUEST_CODE = 1;

    private FragmentSettingsTabBinding binding;
    private Button selectRingtoneButton;
    private Button testRingtoneButton;
    private TextView selectedRingtoneTextView;
    private Switch countdownBeepSwitch;
    private Switch keepScreenOnSwitch;
    private Switch halfwayWarningSwitch;
    private TextView versionTextView;

    private Uri selectedRingtoneUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsTabBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        selectRingtoneButton = root.findViewById(R.id.select_ringtone_button);
        testRingtoneButton = root.findViewById(R.id.test_ringtone_button);
        selectedRingtoneTextView = root.findViewById(R.id.selected_ringtone_text_view);
        countdownBeepSwitch = root.findViewById(R.id.countdown_beep_switch);
        keepScreenOnSwitch = root.findViewById(R.id.keep_screen_on_switch);
        halfwayWarningSwitch = root.findViewById(R.id.halfway_warning_switch);
        versionTextView = root.findViewById(R.id.version_text_view);

        loadDefaultRingtone();
        loadCountdownBeepSetting();
        loadKeepScreenOnSetting();
        loadHalfwayWarningSetting();
        setVersionInfo();

        selectRingtoneButton.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri);
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST_CODE);
        });

        testRingtoneButton.setOnClickListener(v -> {
            if (selectedRingtoneUri != null) {
                try {
                    Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), selectedRingtoneUri);
                    if (ringtone != null) {
                        ringtone.play();
                        Toast.makeText(requireContext(), "Playing ringtone", Toast.LENGTH_SHORT).show();
                        Log.d("SettingsTabFragment", "Playing ringtone: " + selectedRingtoneUri.toString());
                    } else {
                        Toast.makeText(requireContext(), "Ringtone not found", Toast.LENGTH_SHORT).show();
                        Log.e("SettingsTabFragment", "Ringtone object is null for URI: " + selectedRingtoneUri.toString());
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error playing ringtone", Toast.LENGTH_SHORT).show();
                    Log.e("SettingsTabFragment", "Error playing ringtone: " + e.getMessage(), e);
                }
            } else {
                Toast.makeText(requireContext(), "No ringtone selected", Toast.LENGTH_SHORT).show();
                Log.d("SettingsTabFragment", "Test Sound clicked, but no ringtone URI selected.");
            }
        });

        countdownBeepSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveCountdownBeepSetting(isChecked);
        });

        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveKeepScreenOnSetting(isChecked);
            // Immediately apply the setting to the activity's window
            if (isChecked) {
                requireActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                requireActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        halfwayWarningSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHalfwayWarningSetting(isChecked);
        });

        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                selectedRingtoneUri = uri;
                saveDefaultRingtone(selectedRingtoneUri);
                updateSelectedRingtoneTextView();
                Toast.makeText(requireContext(), "Default ringtone updated", Toast.LENGTH_SHORT).show();

                try {
                    Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), uri);
                    ringtone.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadDefaultRingtone() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String ringtoneUriString = sharedPreferences.getString(DEFAULT_RINGTONE_URI_KEY, null);
        if (ringtoneUriString != null) {
            selectedRingtoneUri = Uri.parse(ringtoneUriString);
        } else {
            selectedRingtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        }
        updateSelectedRingtoneTextView();
    }

    private void saveDefaultRingtone(Uri ringtoneUri) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(DEFAULT_RINGTONE_URI_KEY, ringtoneUri.toString());
        editor.apply();
    }

    private void updateSelectedRingtoneTextView() {
        if (selectedRingtoneUri != null) {
            Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), selectedRingtoneUri);
            String ringtoneName = ringtone.getTitle(requireContext());
            selectedRingtoneTextView.setText(String.format("Ringtone: %s", ringtoneName));
        } else {
            selectedRingtoneTextView.setText("Ringtone: (None)");
        }
    }

    private void loadCountdownBeepSetting() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        boolean countdownBeepEnabled = sharedPreferences.getBoolean(COUNTDOWN_BEEP_KEY, true);
        countdownBeepSwitch.setChecked(countdownBeepEnabled);
    }

    private void saveCountdownBeepSetting(boolean isEnabled) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(COUNTDOWN_BEEP_KEY, isEnabled);
        editor.apply();
    }

    private void loadKeepScreenOnSetting() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        boolean keepScreenOnEnabled = sharedPreferences.getBoolean(KEEP_SCREEN_ON_KEY, true);
        keepScreenOnSwitch.setChecked(keepScreenOnEnabled);
    }

    private void saveKeepScreenOnSetting(boolean isEnabled) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEEP_SCREEN_ON_KEY, isEnabled);
        editor.apply();
    }

    private void loadHalfwayWarningSetting() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        boolean halfwayWarningEnabled = sharedPreferences.getBoolean(HALFWAY_WARNING_KEY, false);
        halfwayWarningSwitch.setChecked(halfwayWarningEnabled);
    }

    private void saveHalfwayWarningSetting(boolean isEnabled) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(HALFWAY_WARNING_KEY, isEnabled);
        editor.apply();
    }

    private void setVersionInfo() {
        String version = "Cronos v" + BuildConfig.VERSION_NAME + " © JPL 2025";
        versionTextView.setText(version);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
