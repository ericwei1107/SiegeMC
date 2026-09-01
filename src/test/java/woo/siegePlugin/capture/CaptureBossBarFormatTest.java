package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureBossBarFormatTest {
    @Test void usesOneDecimalMinuteFormatLikeSiegeWar() { assertEquals("5.7", CaptureService.formatRemainingMinutes(Duration.ofSeconds(342))); }
}
