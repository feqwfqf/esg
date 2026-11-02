package cc.coffee.modern.mixin;

import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockEntityData", at = @At("TAIL"))
    private void coffee$metadataChanged(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        CoffeeClient.SCANNER.changed(((ClientPacketListener) (Object) this).getLevel(), packet.getPos());
    }
}
