package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateSupplyIndexTest {
    @Test
    void roundTripsPortableChestPairsAndIgnoresDamagedEntries() {
        UUID id = UUID.fromString("bfff34c8-80f6-48fc-bfca-cd57bbd1c0ac");
        String encoded = TemplateSupplyIndex.encode(List.of(new TemplateSupplyIndex.Entry(id, -2, 93, 7, -2, 93, 8)));

        Map<UUID, TemplateSupplyIndex.Entry> decoded = TemplateSupplyIndex.decode(encoded + ";broken;not-a-uuid,1,2,3,4,5,6");

        assertEquals(new TemplateSupplyIndex.Entry(id, -2, 93, 7, -2, 93, 8), decoded.get(id));
        assertEquals(1, decoded.size());
    }
}
