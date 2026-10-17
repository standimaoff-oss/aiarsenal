package ru.aiforge.realwar.animation;

/** Eight real client ticks of displacement; remote interpolation has legitimate zero-motion ticks. */
public final class MovementSpeedWindow {
    private final double[] samples = new double[8];
    private int cursor, count;
    private double sum;

    public double tick(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > 1) {
            java.util.Arrays.fill(samples, 0);
            cursor = count = 0;
            sum = 0;
            return 0;
        }
        sum += distance - samples[cursor];
        samples[cursor] = distance;
        cursor = (cursor + 1) % samples.length;
        count = Math.min(samples.length, count + 1);
        return Math.max(0, sum / count);
    }
}
