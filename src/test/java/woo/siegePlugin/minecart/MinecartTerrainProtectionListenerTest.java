package woo.siegePlugin.minecart;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.arena.ArenaRegion;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MinecartTerrainProtectionListenerTest {

    @Test
    void preservesArenaBlocksForAnOrdinaryUntypedEntityExplosion() {
        MinecartArenaProtection protection = new MinecartArenaProtection(Optional.of(
                ArenaRegion.between("siegeworld", 0, 0, 0, 10, 10, 10)
        ));
        MinecartTerrainProtectionListener listener = new MinecartTerrainProtectionListener(protection);
        Block protectedBlock = block("siegeworld", 5, 5);
        Block outsideBlock = block("siegeworld", 11, 5);
        List<Block> affectedBlocks = new ArrayList<>(List.of(protectedBlock, outsideBlock));

        listener.onEntityExplode(new EntityExplodeEvent(
                entity(),
                new Location(world("siegeworld"), 5, 64, 5),
                affectedBlocks,
                1.0F,
                ExplosionResult.DESTROY
        ));

        assertEquals(1, affectedBlocks.size());
        assertSame(outsideBlock, affectedBlocks.getFirst());
    }

    private static Entity entity() {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Block block(String worldName, int x, int z) {
        World world = world(worldName);
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getZ" -> z;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class || type == short.class || type == int.class || type == long.class || type == float.class || type == double.class) {
            return 0;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive return type: " + type);
    }
}
