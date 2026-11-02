package cc.coffee.modern.combat;

import java.util.random.RandomGenerator;

/** Plans a curved path, then emits integer mouse counts, never absolute rotations. */
public final class MouseAim {
    public record Counts(int x, int y) { public static final Counts ZERO = new Counts(0, 0); }
    private final RandomGenerator random;
    private final Axis yaw = new Axis(), pitch = new Axis();
    private double startYaw, startPitch, endYaw, endPitch, controlYaw1, controlYaw2, controlPitch1, controlPitch2;
    private double elapsed, duration, sensitivity = Double.NaN;
    private boolean planned;

    public MouseAim(RandomGenerator random) { this.random = random; }
    public void reset() { planned = false; yaw.reset(); pitch.reset(); sensitivity = Double.NaN; }
    public static double degreesPerCount(double sensitivity) {
        // Match MouseHandler's promoted float constants and Entity.turn's 0.15 scale.
        double factor = Math.clamp(sensitivity, 0, 1) * (double) 0.6F + (double) 0.2F;
        return (float) (factor * factor * factor * 8) * 0.15F;
    }
    public static double wrap(double angle) { return angle - Math.floor((angle + 180) / 360) * 360; }
    public Counts step(double currentYaw, double currentPitch, double goalYaw, double goalPitch,
                       double sensitivity, double seconds, double yawRate, double pitchRate) {
        if (!Double.isFinite(currentYaw + currentPitch + goalYaw + goalPitch + sensitivity + seconds + yawRate + pitchRate)
            || seconds <= 0 || seconds > .25 || yawRate <= 0 || pitchRate <= 0) {
            reset(); return Counts.ZERO;
        }
        if (this.sensitivity != sensitivity) { reset(); this.sensitivity = sensitivity; }
        double dt = Math.min(seconds, .05); // No catch-up turns after a stalled frame.
        double step = degreesPerCount(sensitivity);
        double targetYaw = currentYaw + wrap(goalYaw - currentYaw);
        double targetPitch = Math.clamp(goalPitch, -90, 90);
        double targetChange = Math.max(Math.abs(wrap(targetYaw - endYaw)), Math.abs(targetPitch - endPitch));
        if (!planned || (elapsed >= .12 && targetChange > 3) || (elapsed >= duration && targetChange > step * .5)) {
            plan(currentYaw, currentPitch, targetYaw, targetPitch, yawRate, pitchRate);
        }
        elapsed = Math.min(duration, elapsed + dt);
        double t = elapsed / duration;
        t = t * t * t * (t * (t * 6 - 15) + 10); // Smooth start/stop before spatial Bézier evaluation.
        double wantedYaw = bezier(startYaw, controlYaw1, controlYaw2, endYaw, t);
        double wantedPitch = Math.clamp(bezier(startPitch, controlPitch1, controlPitch2, endPitch, t), -90, 90);
        double yawError = wrap(wantedYaw - currentYaw), pitchError = wantedPitch - currentPitch;
        // At the endpoint, settle within half a count rather than oscillating across the grid.
        int dx = yaw.counts(yawError, dt, step, yawRate);
        int dy = pitch.counts(pitchError, dt, step, pitchRate);
        // Whole counts only: never let the synthetic input itself cross the pitch poles.
        dy = Math.clamp(dy, (int) Math.ceil((-90 - currentPitch) / step), (int) Math.floor((90 - currentPitch) / step));
        return new Counts(dx, dy);
    }
    private void plan(double yaw, double pitch, double targetYaw, double targetPitch, double yawRate, double pitchRate) {
        startYaw = yaw; startPitch = pitch; endYaw = targetYaw; endPitch = targetPitch;
        double dx = targetYaw - yaw, dy = targetPitch - pitch, length = Math.hypot(dx, dy);
        double bend = Math.min(4, length * .12) * random.nextDouble(-1, 1);
        double sideYaw = length == 0 ? 0 : -dy / length * bend;
        double sidePitch = length == 0 ? 0 : dx / length * bend;
        double first = random.nextDouble(.15, .35), second = random.nextDouble(.65, .85);
        controlYaw1 = yaw + dx * first + sideYaw;
        controlPitch1 = Math.clamp(pitch + dy * first + sidePitch, -90, 90);
        controlYaw2 = yaw + dx * second + sideYaw * .5;
        controlPitch2 = Math.clamp(pitch + dy * second + sidePitch * .5, -90, 90);
        duration = Math.max(.18, Math.max(Math.abs(dx) / yawRate, Math.abs(dy) / pitchRate) * random.nextDouble(1.5, 1.9));
        elapsed = 0; planned = true;
    }
    private static double bezier(double a, double b, double c, double d, double t) {
        double u = 1 - t;
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
    }
    private static final class Axis {
        double velocity, remainder, credit;
        void reset() { velocity = remainder = credit = 0; }
        int counts(double error, double dt, double step, double rate) {
            if (Math.abs(error) <= step * .5) { velocity = remainder = 0; return 0; }
            if (Math.signum(error) != Math.signum(remainder)) remainder = 0;
            double acceleration = rate * 3;
            double wanted = Math.copySign(Math.min(rate, Math.min(Math.abs(error) / dt, Math.sqrt(2 * acceleration * Math.abs(error)))), error);
            velocity += Math.clamp(wanted - velocity, -acceleration * dt, acceleration * dt);
            credit = Math.min(Math.max(1, rate * dt / step), credit + rate * dt / step);
            double requested = velocity * dt / step + remainder;
            int available = (int) Math.floor(credit);
            int count = Math.clamp((int) requested, -available, available);
            remainder = Math.clamp(requested - count, -1, 1);
            credit -= Math.abs(count);
            return count;
        }
    }
}
