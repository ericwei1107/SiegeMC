package woo.siegePlugin.capture;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureStructureProtectionListenerTest {

    private final World world = world();
    private final Location banner = new Location(world, 10, 80, -5);

    @Test
    void protectsTheBannerBeaconAndEntireIronBase() {
        assertTrue(CaptureStructureProtectionListener.isProtected(new Location(world, 10, 80, -5), banner));
        assertTrue(CaptureStructureProtectionListener.isProtected(new Location(world, 10, 79, -5), banner));
        assertTrue(CaptureStructureProtectionListener.isProtected(new Location(world, 9, 78, -6), banner));
        assertTrue(CaptureStructureProtectionListener.isProtected(new Location(world, 11, 78, -4), banner));
    }

    @Test
    void leavesAdjacentMapBlocksAlone() {
        assertFalse(CaptureStructureProtectionListener.isProtected(new Location(world, 10, 78, -7), banner));
        assertFalse(CaptureStructureProtectionListener.isProtected(new Location(world, 10, 77, -5), banner));
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("equals") && args != null
                        ? proxy == args[0] : null
        );
    }
}
