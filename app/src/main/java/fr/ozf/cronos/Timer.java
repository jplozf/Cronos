package fr.ozf.cronos;

public class Timer {
    private String label;
    private long totalTimeInMillis;
    private long timeLeftInMillis;
    private boolean isRunning;
    private String ringtoneUri;

    // No-argument constructor for Gson deserialization
    public Timer() {
        // Default values or leave for Gson to populate
    }

    public Timer(String label, long totalTimeInMillis) {
        this.label = label;
        this.totalTimeInMillis = totalTimeInMillis;
        this.timeLeftInMillis = totalTimeInMillis;
        this.isRunning = false;
        this.ringtoneUri = null; // Default to no specific ringtone
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getTotalTimeInMillis() {
        return totalTimeInMillis;
    }

    public void setTotalTimeInMillis(long totalTimeInMillis) {
        this.totalTimeInMillis = totalTimeInMillis;
    }

    public long getTimeLeftInMillis() {
        return timeLeftInMillis;
    }

    public void setTimeLeftInMillis(long timeLeftInMillis) {
        this.timeLeftInMillis = timeLeftInMillis;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public String getRingtoneUri() {
        return ringtoneUri;
    }

    public void setRingtoneUri(String ringtoneUri) {
        this.ringtoneUri = ringtoneUri;
    }

    public void reset() {
        timeLeftInMillis = totalTimeInMillis;
        isRunning = false;
    }
}