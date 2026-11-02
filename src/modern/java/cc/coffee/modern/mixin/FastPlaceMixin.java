package cc.coffee.modern.mixin;

import cc.coffee.modern.ClientSettings;
import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FastPlaceMixin {
    @Shadow private int rightClickDelay;
    @Inject(method = "tick", at = @At("HEAD"))
    private void coffee$fastPlace(CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        if (client.player != null && client.level != null && client.gui.screen() == null
            && CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.FAST_PLACE)
            && client.player.getMainHandItem().getItem() instanceof BlockItem) rightClickDelay = Math.min(rightClickDelay, 1);
    }
}
