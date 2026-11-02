package cc.coffee.modern.mixin;

import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "setBlock", at = @At("RETURN"))
    private void coffee$chunkChanged(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) CoffeeClient.SCANNER.changed((ClientLevel) (Object) this, pos);
    }
}
