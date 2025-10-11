package fr.ozf.cronos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import fr.ozf.cronos.databinding.FragmentTrainingHistoryBinding;

public class TrainingHistoryFragment extends Fragment implements TrainingSessionAdapter.OnItemLongClickListener {

    private static final String TAG = "TrainingHistoryFragment";
    private static final String SHARED_PREFS_TRAINING_HISTORY = "sharedPrefsTrainingHistory";
    private static final String TRAINING_HISTORY_LIST_KEY = "trainingHistoryList";

    private FragmentTrainingHistoryBinding binding;
    private TrainingSessionAdapter adapter;
    private List<TrainingSession> trainingSessionList;
    private TextView noHistoryTextView;
    private Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: start");
        binding = FragmentTrainingHistoryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        RecyclerView recyclerView = root.findViewById(R.id.training_history_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        noHistoryTextView = root.findViewById(R.id.no_history_text_view);

        loadTrainingHistory();

        if (trainingSessionList == null) {
            trainingSessionList = new ArrayList<>();
        }

        adapter = new TrainingSessionAdapter(trainingSessionList, this);
        recyclerView.setAdapter(adapter);

        updateNoHistoryVisibility();

        Log.d(TAG, "onCreateView: end");
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload data in case a new session was added while this fragment was paused
        loadTrainingHistory();
        adapter.setTrainingSessionList(trainingSessionList);
        updateNoHistoryVisibility();
    }

    private void loadTrainingHistory() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_TRAINING_HISTORY, Context.MODE_PRIVATE);
        String json = sharedPreferences.getString(TRAINING_HISTORY_LIST_KEY, null);
        Type type = new TypeToken<ArrayList<TrainingSession>>() {}.getType();
        trainingSessionList = gson.fromJson(json, type);

        if (trainingSessionList == null) {
            trainingSessionList = new ArrayList<>();
        }
        Log.d(TAG, "Loaded " + trainingSessionList.size() + " training sessions.");
    }

    private void saveTrainingHistory() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_TRAINING_HISTORY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String json = gson.toJson(trainingSessionList);
        editor.putString(TRAINING_HISTORY_LIST_KEY, json);
        editor.apply();
        Log.d(TAG, "Training history saved. Current size: " + trainingSessionList.size());
    }

    private void updateNoHistoryVisibility() {
        if (trainingSessionList.isEmpty()) {
            noHistoryTextView.setVisibility(View.VISIBLE);
        } else {
            noHistoryTextView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onItemLongClick(int position) {
        Log.d(TAG, "onItemLongClick called for position: " + position);
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Training Session")
                .setMessage("Are you sure you want to delete this training session?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    trainingSessionList.remove(position);
                    adapter.notifyItemRemoved(position);
                    saveTrainingHistory();
                    updateNoHistoryVisibility();
                    Log.d(TAG, "Training session deleted at position: " + position);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    Log.d(TAG, "Deletion cancelled.");
                })
                .show();
    }
}