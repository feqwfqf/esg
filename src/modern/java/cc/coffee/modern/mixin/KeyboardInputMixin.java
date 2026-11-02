package cc.coffee.modern.mixin;

import cc.coffee.modern.combat.*;
import cc.coffee.modern.navigation.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void coffee$correctMovement(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var navigation = Navigator.INSTANCE.drive();
        if (navigation != null && client.gui.screen() == null && client.isWindowActive() && !client.isPaused() && client.player != null) {
            if (keyPresses.forward() || keyPresses.backward() || keyPresses.left() || keyPresses.right() || keyPresses.jump() || keyPresses.shift()) {
                Navigator.INSTANCE.stop(); return;
            }
            var movement = MovementFix.correct(0, navigation.speed(), navigation.yaw(), client.player.getYRot());
            moveVector = new Vec2(movement.strafe(), movement.forward());
            keyPresses = new Input(movement.forward() > 1e-4, movement.forward() < -1e-4,
                movement.strafe() > 1e-4, movement.strafe() < -1e-4, navigation.jump(), false, false);
            return;
        }
        var aura = KillAura.INSTANCE;
        if (!aura.movementFixActive(client)) return;
        var corrected = MovementFix.correct(moveVector.x, moveVector.y, aura.movementReferenceYaw(), client.player.getYRot());
        moveVector = new Vec2(corrected.strafe(), corrected.forward());
        Input keys = keyPresses;
        keyPresses = new Input(corrected.forward() > 1e-4, corrected.forward() < -1e-4,
            corrected.strafe() > 1e-4, corrected.strafe() < -1e-4,
            keys.jump(), keys.shift(), keys.sprint() && corrected.forward() > .8);
    }
}
