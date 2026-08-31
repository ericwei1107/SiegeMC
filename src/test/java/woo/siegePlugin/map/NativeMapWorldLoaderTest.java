package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeMapWorldLoaderTest {

    @Test
    void paperDotWorldContainerStillResolvesAnActiveWorldAsADirectChild() {
        Path container = Path.of(".").toAbsolutePath().normalize();

        Path resolved = NativeMapWorldLoader.resolveRuntimeFolder(
                Path.of("."), "siege-active-123-iron_mountain1"
        );

        assertEquals(container, resolved.getParent());
        assertEquals(container.resolve("siege-active-123-iron_mountain1"), resolved);
    }

    @Test
    void nestedRuntimeWorldNamesCannotEscapeTheWorldContainer() {
        assertThrows(IllegalArgumentException.class, () ->
                NativeMapWorldLoader.resolveRuntimeFolder(Path.of("."), "nested/siege-active-map")
        );
    }
}
