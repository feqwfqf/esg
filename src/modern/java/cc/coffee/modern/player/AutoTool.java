package cc.coffee.modern.player;

import cc.coffee.modern.ClientSettings;
import cc.coffee.modern.CoffeeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest hotbar tool for the block being mined. */
public final class AutoTool {
    private AutoTool() {}
    public static void tick(Minecraft client) {
        if (!CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.AUTO_TOOL) || client.level == null || client.player == null
            || client.gui.screen() != null || !client.isWindowActive() || !client.options.keyAttack.isDown()
            || !(client.hitResult instanceof BlockHitResult hit)) return;
        var state = client.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) return;
        var inventory = client.player.getInventory();
        int bestSlot = inventory.getSelectedSlot();
        float bestSpeed = inventory.getItem(bestSlot).getDestroySpeed(state);
        for (int slot = 0; slot < 9; slot++) {
            float speed = inventory.getItem(slot).getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = slot; }
        }
        inventory.setSelectedSlot(bestSlot);
    }
}
