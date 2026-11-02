package cc.coffee.modern.player;

import cc.coffee.modern.ClientSettings;
import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.Minecraft;

/** Ports the original Spider module's default Vanilla mode. */
public final class Spider {
    private Spider() {}

    public static void tick(Minecraft client) {
        var player = client.player;
        if (!CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.SPIDER) || player == null
            || client.level == null || client.gui.screen() != null || client.isPaused()
            || !client.isWindowActive() || !player.isAlive() || player.isPassenger()
            || player.getAbilities().flying || player.isInWater() || player.isInLava()) return;
        if (player.horizontalCollision) player.jumpFromGround();
    }
}
