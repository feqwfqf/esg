package cc.coffee.modern.scanner;

import cc.coffee.modern.StorageIndex;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkKeyTest {
    @Test void chunkEventsAndBlockUpdatesUseTheSameCoordinates() {
        for (int x : new int[] {-300, -1, 0, 7, 200}) {
            for (int z : new int[] {-250, -2, 0, 3, 100}) {
                var position = new StorageIndex.Position(x * 16 + 5, 64, z * 16 + 9);
                long key = ChunkScanner.key(new ChunkPos(x, z));
                assertEquals(position.chunk(), key);
                var index = new StorageIndex();
                index.replaceChunk(key, java.util.Map.of(position, StorageIndex.Kind.CHEST), 0);
                index.unload(position.chunk(), false);
                assertTrue(index.visible(new cc.coffee.modern.ClientSettings(), 0, 0, 0).isEmpty());
            }
        }
    }
}
