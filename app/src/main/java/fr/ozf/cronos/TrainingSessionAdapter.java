package fr.ozf.cronos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class TrainingSessionAdapter extends RecyclerView.Adapter<TrainingSessionAdapter.TrainingSessionViewHolder> {

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    private List<TrainingSession> trainingSessionList;
    private OnItemLongClickListener longClickListener;

    public TrainingSessionAdapter(List<TrainingSession> trainingSessionList, OnItemLongClickListener longClickListener) {
        this.trainingSessionList = trainingSessionList;
        this.longClickListener = longClickListener;
    }

    public void setTrainingSessionList(List<TrainingSession> trainingSessionList) {
        this.trainingSessionList = trainingSessionList;
        notifyDataSetChanged();
    }

        @Override
        public TrainingSessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.training_session_item, parent, false);
            return new TrainingSessionViewHolder(view, longClickListener);
        }

    @Override
    public void onBindViewHolder(@NonNull TrainingSessionViewHolder holder, int position) {
        TrainingSession session = trainingSessionList.get(position);
        holder.bind(session);
    }

    @Override
    public int getItemCount() {
        return trainingSessionList.size();
    }

    static class TrainingSessionViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {
        TextView textViewSessionDate;
        TextView textViewSessionScheme;
        TextView textViewSessionDistance;
        TextView textViewSessionDuration;
        private OnItemLongClickListener longClickListener;

        public TrainingSessionViewHolder(@NonNull View itemView, OnItemLongClickListener longClickListener) {
            super(itemView);
            textViewSessionDate = itemView.findViewById(R.id.text_view_session_date);
            textViewSessionScheme = itemView.findViewById(R.id.text_view_session_scheme);
            textViewSessionDistance = itemView.findViewById(R.id.text_view_session_distance);
            textViewSessionDuration = itemView.findViewById(R.id.text_view_session_duration);
            this.longClickListener = longClickListener;
            itemView.setOnLongClickListener(this);
        }

        public void bind(TrainingSession session) {
            textViewSessionDate.setText(String.format("Date: %s", session.getFormattedDate()));
            textViewSessionScheme.setText(String.format("Scheme: %s", session.getSchemeName()));
            textViewSessionDistance.setText(String.format(Locale.getDefault(), "Distance: %.2f km", session.getDistanceKm()));
            textViewSessionDuration.setText(String.format("Duration: %s", session.getFormattedDuration()));
        }

        @Override
        public boolean onLongClick(View v) {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(getAdapterPosition());
                return true;
            }
            return false;
        }
    }
}