package fr.ozf.cronos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TrainingSession {
    private long timestamp;
    private String schemeName;
    private double distanceKm;
    private long durationMillis;
    private double meanSpeedKmH;

    public TrainingSession(String schemeName, double distanceKm, long durationMillis, double meanSpeedKmH) {
        this.timestamp = System.currentTimeMillis();
        this.schemeName = schemeName;
        this.distanceKm = distanceKm;
        this.durationMillis = durationMillis;
        this.meanSpeedKmH = meanSpeedKmH;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public double getMeanSpeedKmH() {
        return meanSpeedKmH;
    }

    public void setMeanSpeedKmH(double meanSpeedKmH) {
        this.meanSpeedKmH = meanSpeedKmH;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedDuration() {
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMillis));
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
}