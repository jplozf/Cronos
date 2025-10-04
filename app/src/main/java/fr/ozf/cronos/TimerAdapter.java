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
        CountDownTimer countDownTimer;
        Timer currentTimer;
        private int position;

        public TimerViewHolder(@NonNull View itemView, OnTimerClickListener listener) {
            super(itemView);
            // The listener is now a member of the outer class, so we don't need to store it here
            timerLabel = itemView.findViewById(R.id.timer_label);
            timerTime = itemView.findViewById(R.id.timer_time);
            timerButton = itemView.findViewById(R.id.timer_button);
            itemView.setOnClickListener(this);
        }

        public void bind(Timer timer, int position) {
            this.position = position;
            currentTimer = timer;
            timerLabel.setText(timer.getLabel());
            updateTimerText();
            updateButtonState();

            timerButton.setOnClickListener(v -> {
                if (currentTimer.isRunning()) {
                    pauseTimer();
                } else {
                    startTimer();
                }
                updateButtonState();
            });

            if (timer.isRunning()) {
                startTimerInternal();
            }
        }

        @Override
        public void onClick(View v) {
            if (clickListener != null) {
                clickListener.onTimerClick(position, currentTimer);
            }
        }

        private void startTimer() {
            currentTimer.setRunning(true);
            startTimerInternal();
        }

        private void startTimerInternal() {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            countDownTimer = new CountDownTimer(currentTimer.getTimeLeftInMillis(), 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    currentTimer.setTimeLeftInMillis(millisUntilFinished);
                    updateTimerText();
                }

                @Override
                public void onFinish() {
                    currentTimer.setRunning(false);
                    currentTimer.reset();
                    updateTimerText();
                    updateButtonState();
                    MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                    if (finishListener != null) {
                        finishListener.onTimerFinish(position, currentTimer);
                    }
                }
            }.start();
        }

        private void pauseTimer() {
            currentTimer.setRunning(false);
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
        }

        private void updateTimerText() {
            int minutes = (int) (currentTimer.getTimeLeftInMillis() / 1000) / 60;
            int seconds = (int) (currentTimer.getTimeLeftInMillis() / 1000) % 60;
            timerTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        }

        private void updateButtonState() {
            if (currentTimer.isRunning()) {
                timerButton.setText("Stop");
            } else {
                timerButton.setText("Start");
            }
        }
    }
}