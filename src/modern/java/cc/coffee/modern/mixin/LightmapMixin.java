package cc.coffee.modern.mixin;

import cc.coffee.modern.*;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void coffee$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.FULLBRIGHT)) {
            state.brightness = 1;
            state.nightVisionEffectIntensity = 1;
            state.darknessEffectScale = 0;
            state.needsUpdate = true;
        }
    }
}
