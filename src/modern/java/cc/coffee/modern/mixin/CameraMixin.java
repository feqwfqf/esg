package cc.coffee.modern.mixin;

import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void coffee$zoom(float partialTick, CallbackInfoReturnable<Float> result) {
        if (CoffeeClient.zooming()) result.setReturnValue((float) (result.getReturnValue() / CoffeeClient.SETTINGS.zoom));
    }
}
