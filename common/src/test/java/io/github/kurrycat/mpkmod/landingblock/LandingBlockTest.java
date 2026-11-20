package io.github.kurrycat.mpkmod.landingblock;

import io.github.kurrycat.mpkmod.util.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LandingBlockTest {
    private static final double DELTA = 1e-9;

    @Test
    void axisOffsetWithoutWallsIsPlainOverlap() {
        // Player 0.6 wide, 0.1 over the min edge of a block from 0 to 1
        assertEquals(0.1, LandingBlock.axisOffset(0, 1, -0.5, 0.1, 0, 0), DELTA);
        // 0.1 over the max edge
        assertEquals(0.1, LandingBlock.axisOffset(0, 1, 0.9, 1.5, 0, 0), DELTA);
        // Missed the min edge by 0.05
        assertEquals(-0.05, LandingBlock.axisOffset(0, 1, -0.65, -0.05, 0, 0), DELTA);
    }

    @Test
    void axisOffsetSubtractsAdjacentWall() {
        // Wall flush with the min edge: the whole 0.6 hitbox must be past the edge
        assertEquals(-0.5, LandingBlock.axisOffset(0, 1, -0.5, 0.1, -0.6, 0), DELTA);
        // Standing right against the wall is exactly 0
        assertEquals(0, LandingBlock.axisOffset(0, 1, 0, 0.6, -0.6, 0), DELTA);
        // Wall on the other side does not matter when this edge limits
        assertEquals(0.1, LandingBlock.axisOffset(0, 1, -0.5, 0.1, 0, -0.6), DELTA);
    }

    @Test
    void axisOffsetIgnoresFloatingPointNoise() {
        // Flush against a wall at real coordinates, where -359.7 - 0.3 is not exactly -360
        double z = -359.7;
        double offset = LandingBlock.axisOffset(-360, -359, z - 0.3, z + 0.3, -0.6, 0);
        assertEquals(0, offset);
        assertEquals(0.8, LandingBlock.totalOffset(new Vector3D(0.8, 0, offset)), DELTA);
    }

    @Test
    void totalOffsetCountsZeroAsLanded() {
        assertEquals(0.77528, LandingBlock.totalOffset(new Vector3D(0.77528, 0, 0)), DELTA);
        assertEquals(0.5, LandingBlock.totalOffset(new Vector3D(0.3, 0, 0.4)), DELTA);
        assertEquals(-0.1, LandingBlock.totalOffset(new Vector3D(-0.1, 0, 0.4)), DELTA);
        assertEquals(-0.5, LandingBlock.totalOffset(new Vector3D(-0.3, 0, -0.4)), DELTA);
    }

    @Test
    void axisOffsetPicksWallSideWhenItLimits() {
        // Centered slightly past the middle, but the wall on the min side is closer to failing
        assertEquals(0.45, LandingBlock.axisOffset(0, 1, 0.45, 1.05, -0.6, 0), DELTA);
    }
}
