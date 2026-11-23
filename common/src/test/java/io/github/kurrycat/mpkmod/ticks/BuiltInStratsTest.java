package io.github.kurrycat.mpkmod.ticks;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.kurrycat.mpkmod.save.Serializer;
import io.github.kurrycat.mpkmod.util.input.InputVector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BuiltInStratsTest {
    private static HashMap<String, Timing> base, withExtra;

    @BeforeAll
    static void loadBuiltInStrats() {
        Serializer.registerSerializer();
        base = load("strats.json");
        // Same order as TimingStorage with More Timings on
        withExtra = new HashMap<>(base);
        withExtra.putAll(load("strats-extra.json"));
    }

    private static HashMap<String, Timing> load(String file) {
        HashMap<String, Timing> strats = Serializer.deserializeAny(
                BuiltInStratsTest.class.getResourceAsStream("/assets/mpkmod/strats/" + file),
                new TypeReference<HashMap<String, Timing>>() {}
        );
        assertNotNull(strats);
        return strats;
    }

    @BeforeEach
    void useMoreTimings() {
        TimingStorage.patterns = withExtra;
    }

    @Test
    void oldStratsWithoutMoreTimings() {
        TimingStorage.patterns = base;
        assertEquals("2t Mark", match("D:J", "D:A", "WD:A"));
        assertEquals("Max Pessi", match(":J", "D:A", "WD:A"));
        assertEquals("HH 2t", match("D:G", "D:G", "D:J", "WD:A"));
    }

    /** Ticks after standing still, each as "keys:G|J|A" (grounded, jumping, airborne), plus ":P" when sprinting. */
    private static String match(String... ticks) {
        List<TimingInput> inputs = new ArrayList<>();
        inputs.add(TimingInput.stopTick());
        for (String tick : ticks) {
            String[] parts = tick.split(":", -1);
            GroundState g = parts[1].equals("G") ? GroundState.GROUNDED
                    : parts[1].equals("J") ? GroundState.JUMPING : GroundState.AIRBORNE;
            boolean sprint = parts.length > 2 && parts[2].equals("P");
            inputs.add(new TimingInput(InputVector.fromString(parts[0]), sprint, false, g));
        }
        return TimingStorage.match(inputs);
    }

    @Test
    void jamMark() {
        assertEquals("2t Mark", match("D:J", "D:A", "WD:A"));
    }

    @Test
    void markWithStrafeHeldBeforeJump() {
        assertEquals("1t Mark", match("D:G", "D:G", "D:J", "WD:A"));
        assertEquals("1t Mark", match("A:G", "A:J", "WA:A"));
    }

    @Test
    void pessiMark() {
        // Only the strafe-only ticks before W count
        assertEquals("Max Pessi 1t Mark", match(":J", "D:A", "WD:A"));
        assertEquals("Pessi -3t 2t Mark", match(":J", ":A", ":A", "D:A", "D:A", "WD:A"));
        assertEquals("Pessi -3t 2t Mark", match(":J", ":A", ":A", "A:A", "A:A", "WA:A"));
        assertEquals("Pessi -6t 2t Mark", match(":J", ":A", ":A", ":A", ":A", ":A", "D:A", "D:A", "WD:A"));
    }

    @Test
    void pessiFmm() {
        // Jumps again on the tick after landing
        assertEquals("Pessi -3t Max FMM", match(
                ":J", ":A", ":A", "W:A", "W:A",
                "W:G",
                "W:J", "W:A:P"));
        assertEquals("Max Pessi FMM 3t", match(
                ":J", "W:A",
                "W:G",
                "W:J", "W:A", "W:A", "W:A:P"));
    }

    @Test
    void a7hhFmm() {
        // No keys until the landing tick
        String[] ticks = new String[15];
        ticks[0] = ":J";
        for (int i = 1; i <= 11; i++) ticks[i] = ":A";
        ticks[12] = "W:G";
        ticks[13] = "W:J";
        ticks[14] = "W:A:P";
        assertEquals("A7HH Max FMM", match(ticks));
    }

    @Test
    void pessiFmmWithRunIsPaneFmm() {
        assertEquals("Pane FMM 3t Run 2t", match(
                ":J", ":A", ":A", "W:A", "W:A",
                "W:G", "W:G", "W:G",
                "W:J", "W:A:P"));
    }

    @Test
    void pessiIntoMarkOnNextJump() {
        // Strafe held through the landing into the next jump
        assertEquals("Pessi -3t 2t Mark", match(
                ":J", ":A", ":A", "D:A", "D:A",
                "D:G",
                "D:J", "D:A", "WD:A"));
        assertEquals("Max Pessi 1t Mark", match(
                ":J", "A:A",
                "A:G", "A:G", "A:G",
                "A:J", "WA:A"));
    }

    @Test
    void a7hhIntoMarkOnNextJump() {
        String[] ticks = new String[17];
        ticks[0] = ":J";
        for (int i = 1; i <= 11; i++) ticks[i] = ":A";
        ticks[12] = "D:G";
        ticks[13] = "D:J";
        ticks[14] = "D:A";
        ticks[15] = "D:A";
        ticks[16] = "WD:A";
        assertEquals("A7HH 3t Mark", match(ticks));
    }

    @Test
    void pessiStaysPessiUntilW() {
        assertEquals("Pessi -2t", match(":J", ":A", "D:A", "D:A"));
    }

    @Test
    void pressingWAndStrafeTogetherIsNotAMark() {
        String result = match(":J", ":A", "WD:A");
        assertFalse(result != null && result.contains("Mark"), result);
    }
}
