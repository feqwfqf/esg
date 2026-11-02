package cc.coffee.modern.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disable Minecraft's client telemetry event sender while Coffee is loaded. */
@Mixin(Minecraft.class)
public abstract class TelemetryMixin {
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void coffee$disableTelemetry(CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(false);
    }
}
