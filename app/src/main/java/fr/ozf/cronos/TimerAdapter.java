package fr.ozf.cronos;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class TimerAdapter extends RecyclerView.Adapter<TimerAdapter.TimerViewHolder> {

    public interface OnTimerClickListener {
        void onTimerClick(int position, Timer timer);
        void onTimerStartStop(int position, Timer timer);
    }

    public interface OnTimerFinishListener {
        void onTimerFinish(int position, Timer timer);
    }

    private List<Timer> timerList;
    private OnTimerClickListener clickListener;
    private OnTimerFinishListener finishListener;
    private Context context;

    public TimerAdapter(List<Timer> timerList, OnTimerClickListener clickListener, OnTimerFinishListener finishListener, Context context) {
        this.timerList = timerList;
        this.clickListener = clickListener;
        this.finishListener = finishListener;
        this.context = context;
    }

    public void setTimers(List<Timer> newTimers) {
        this.timerList = newTimers;
        notifyDataSetChanged();
    }

        @Override
        public TimerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.timer_item, parent, false);
            return new TimerViewHolder(view, clickListener);
        }
    
        @Override
        public void onBindViewHolder(@NonNull TimerViewHolder holder, int position) {
            Timer timer = timerList.get(position);
            holder.bind(timer, position);
        }
    
        @Override
        public int getItemCount() {
            return timerList.size();
        }
    
    public class TimerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
                TextView timerLabel;
                TextView timerTime;
                Button timerButton;
                private int position;
                private Timer timer; // Add this line
        
                        public TimerViewHolder(@NonNull View itemView, OnTimerClickListener listener) {
                            super(itemView);
                            timerLabel = itemView.findViewById(R.id.timer_label);
                            timerTime = itemView.findViewById(R.id.timer_time);
                            timerButton = itemView.findViewById(R.id.timer_button);
                            itemView.setOnClickListener(this);
                        }
        
                        public void bind(Timer timer, int position) {
                            this.position = position;
                            this.timer = timer; // Add this line
                            timerLabel.setText(timer.getLabel());                    updateTimerText(timer);
                    updateButtonState(timer);
    
                    timerButton.setOnClickListener(v -> {
                        if (clickListener != null) {
                            clickListener.onTimerStartStop(position, timer);
                        }
                    });
                }
    
                @Override
                public void onClick(View v) {
                    if (clickListener != null) {
                        clickListener.onTimerClick(position, timer);
                    }
                }
    
                public void updateTimerText(Timer timer) {
                    int minutes = (int) (timer.getTimeLeftInMillis() / 1000) / 60;
                    int seconds = (int) (timer.getTimeLeftInMillis() / 1000) % 60;
                    timerTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                }
    
                public void updateButtonState(Timer timer) {
                    if (timer.isRunning()) {
                        timerButton.setText("Stop");
                    } else {
                        timerButton.setText("Start");
                    }
                }
            }}