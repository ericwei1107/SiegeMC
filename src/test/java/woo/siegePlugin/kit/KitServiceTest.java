package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class KitServiceTest {

    @Test
    void invalidStartupSnapshotLeavesOnlyTheKitUnavailable() {
        KitService service = new KitService(null, null, List.of("slots.8 has an invalid item"));

        assertFalse(service.isConfigured());
        assertFalse(service.configurationProblems().isEmpty());
    }
}
