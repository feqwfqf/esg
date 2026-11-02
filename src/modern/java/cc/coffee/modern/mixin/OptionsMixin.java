package cc.coffee.modern.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Options.class)
public abstract class OptionsMixin {
    // Restrict the change to renderDistance: simulation distance retains vanilla limits.
    @ModifyArg(method = "<init>",
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=options.renderDistance"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDistance:Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance$IntRange;<init>(IIZ)V"), index = 1)
    private int coffee$extendedRenderDistance(int vanillaMaximum) { return 256; }

    @ModifyArg(method = "buildPlayerInformation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ClientInformation;<init>(Ljava/lang/String;ILnet/minecraft/world/entity/player/ChatVisiblity;ZILnet/minecraft/world/entity/HumanoidArm;ZZLnet/minecraft/server/level/ParticleStatus;)V"), index = 1)
    private int coffee$protocolViewDistance(int requested) { return Math.clamp(requested, 2, 127); }
}
