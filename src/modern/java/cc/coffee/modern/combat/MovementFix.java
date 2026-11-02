package cc.coffee.modern.combat;

/** Converts keyboard movement between yaw bases without changing its magnitude. */
public final class MovementFix {
    private MovementFix() {}
    public record Movement(float strafe, float forward) {}
    public static Movement correct(float strafe, float forward, double referenceYaw, double cameraYaw) {
        double radians = Math.toRadians(MouseAim.wrap(referenceYaw - cameraYaw));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new Movement((float) (strafe * cos - forward * sin), (float) (strafe * sin + forward * cos));
    }
}
