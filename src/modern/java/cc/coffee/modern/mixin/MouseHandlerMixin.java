package cc.coffee.modern.mixin;

import cc.coffee.modern.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void coffee$mouseCounts(double frameTime, CallbackInfo ci) {
        var counts = KillAura.INSTANCE.mouseCounts(minecraft, accumulatedDX, accumulatedDY);
        accumulatedDX += minecraft.options.invertMouseX().get() ? -counts.x() : counts.x();
        accumulatedDY += minecraft.options.invertMouseY().get() ? -counts.y() : counts.y();
    }
}
