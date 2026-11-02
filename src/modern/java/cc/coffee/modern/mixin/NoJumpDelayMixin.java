package cc.coffee.modern.mixin;

import cc.coffee.modern.ClientSettings;
import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class NoJumpDelayMixin {
    @Shadow private int noJumpDelay;
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void coffee$jumpDelay(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if ((Object) this == client.player && CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.NO_JUMP_DELAY)) noJumpDelay = 0;
    }
}
