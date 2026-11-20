package io.github.kurrycat.mpkmod.landingblock;

import io.github.kurrycat.mpkmod.compatibility.API;
import io.github.kurrycat.mpkmod.compatibility.MCClasses.Player;
import io.github.kurrycat.mpkmod.compatibility.MCClasses.WorldInteraction;
import io.github.kurrycat.mpkmod.gui.infovars.InfoString;
import io.github.kurrycat.mpkmod.util.BoundingBox3D;
import io.github.kurrycat.mpkmod.util.Vector3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@InfoString.DataClass
public class LandingBlock {
    public static final int MAX_OFFSETS_SAVED = 500;
    private static final double EPS = 1e-7;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double PLAYER_WIDTH = 0.6;
    public LandingMode landingMode = LandingMode.LAND;
    @InfoString.Field
    public BoundingBox3D boundingBox;
    public boolean enabled = true;
    public boolean highlight = false;

    public List<Vector3D> offsets = new ArrayList<>();
    @InfoString.Field
    public Vector3D pb = null;
    @InfoString.Field
    public Vector3D pbX = null;
    @InfoString.Field
    public Vector3D pbZ = null;
    @InfoString.Field
    public Double totalOffset = null;
    @InfoString.Field
    public Double pbTotalOffset = null;

    public long lastTimeOffsetSaved = 0;

    // How much walls next to each edge shrink the landable area, see calculateWalls
    private double wallMinX = 0, wallMaxX = 0, wallMinZ = 0, wallMaxZ = 0;
    private boolean wallsCalculated = false;

    public static List<LandingBlock> asLandingBlocks(List<BoundingBox3D> collisionBoundingBoxes) {
        return collisionBoundingBoxes.stream().map(LandingBlock::new).collect(Collectors.toCollection(ArrayList<LandingBlock>::new));
    }

    public LandingBlock(BoundingBox3D boundingBox) {
        this.boundingBox = boundingBox;
    }

    public LandingBlock(BoundingBox3D boundingBox, LandingMode landingMode) {
        this.boundingBox = boundingBox;
        this.landingMode = landingMode;
    }

    @InfoString.Getter
    public Vector3D getOffset() {
        if (offsets.isEmpty()) return null;
        return offsets.get(offsets.size() - 1);
    }

    public Vector3D saveOffsetIfInRange() {
        if (!isTryingToLandOn()) return null;
        BoundingBox3D playerBB = landingMode.getPlayerBB();
        if (playerBB == null) return null;

        Vector3D offset = boundingBox.distanceTo(playerBB).mult(-1D);
        if (offset.getX() <= -0.3F || offset.getZ() <= -0.3F) return null;
        // Landing blocks not set by the player (e.g. by a server) are calculated on first landing
        if (!wallsCalculated) calculateWalls();
        offset = applyWalls(offset, playerBB);

        offsets.add(offset);
        while (offsets.size() > MAX_OFFSETS_SAVED)
            offsets.remove(0);

        totalOffset = totalOffset(offset);
        if (pb == null || totalOffset > pbTotalOffset) {
            pb = offset;
            pbTotalOffset = totalOffset;
        }
        if (pbX == null || offset.getX() > pbX.getX()) pbX = offset;
        if (pbZ == null || offset.getZ() > pbZ.getZ()) pbZ = offset;

        lastTimeOffsetSaved = API.tickTime;

        return offset.copy();
    }

    /**
     * Finds blocks at player height next to the landing block (like a neo wall). They keep the
     * player's whole hitbox from reaching over that edge, so the offset on that side shrinks.
     * Call when the landing block is set or edited.
     */
    public void calculateWalls() {
        BoundingBox3D playerBB = Player.getLatest() == null ? null : Player.getLatest().getBoundingBox();
        double height = playerBB == null ? PLAYER_HEIGHT : playerBB.maxY() - playerBB.minY();
        double width = playerBB == null ? PLAYER_WIDTH : playerBB.maxX() - playerBB.minX();
        double surfaceY = landingMode == LandingMode.ENTER ? boundingBox.minY() : boundingBox.maxY();

        wallMinX = wallAdjust(true, true, surfaceY, height, width);
        wallMaxX = wallAdjust(true, false, surfaceY, height, width);
        wallMinZ = wallAdjust(false, true, surfaceY, height, width);
        wallMaxZ = wallAdjust(false, false, surfaceY, height, width);
        wallsCalculated = true;
    }

    private Vector3D applyWalls(Vector3D offset, BoundingBox3D playerBB) {
        double x = axisOffset(boundingBox.minX(), boundingBox.maxX(), playerBB.minX(), playerBB.maxX(), wallMinX, wallMaxX);
        double z = axisOffset(boundingBox.minZ(), boundingBox.maxZ(), playerBB.minZ(), playerBB.maxZ(), wallMinZ, wallMaxZ);
        return new Vector3D(x, offset.getY(), z);
    }

    /** Offset on one axis from the limiting edge; equals the plain overlap when both adjustments are 0. */
    static double axisOffset(double lbMin, double lbMax, double pMin, double pMax, double adjMin, double adjMax) {
        double fromMin = pMax - lbMin + adjMin;
        double fromMax = lbMax - pMin + adjMax;
        if (fromMax > Math.abs(fromMin)) return fromMin;
        if (fromMin > Math.abs(fromMax)) return fromMax;
        return Math.max(fromMin, fromMax);
    }

    /**
     * How much a wall next to one edge shrinks the landable area (0 or negative).
     * A wall must span the whole edge and be closer than the player's size to it.
     */
    private double wallAdjust(boolean xAxis, boolean minSide, double surfaceY, double height, double playerSize) {
        double edge = xAxis ? (minSide ? boundingBox.minX() : boundingBox.maxX())
                : (minSide ? boundingBox.minZ() : boundingBox.maxZ());
        double spanMin = xAxis ? boundingBox.minZ() : boundingBox.minX();
        double spanMax = xAxis ? boundingBox.maxZ() : boundingBox.maxX();

        int fromCol = (int) Math.floor(minSide ? edge - playerSize : edge);
        int toCol = (int) Math.floor(minSide ? edge : edge + playerSize);
        // Start one block lower for hitboxes taller than a block (fences, walls)
        int fromY = (int) Math.floor(surfaceY) - 1;
        int toY = (int) Math.floor(surfaceY + height - EPS);

        double adjust = 0;
        for (int col = fromCol; col <= toCol; col++) {
            for (int span = (int) Math.floor(spanMin); span <= (int) Math.floor(spanMax - EPS); span++) {
                for (int y = fromY; y <= toY; y++) {
                    Vector3D pos = xAxis ? new Vector3D(col, y, span) : new Vector3D(span, y, col);
                    for (BoundingBox3D box : WorldInteraction.getCollisionBoundingBoxes(pos)) {
                        if (box.maxY() <= surfaceY + EPS || box.minY() >= surfaceY + height - EPS) continue;

                        double boxSpanMin = xAxis ? box.minZ() : box.minX();
                        double boxSpanMax = xAxis ? box.maxZ() : box.maxX();
                        if (boxSpanMin > spanMin + EPS || boxSpanMax < spanMax - EPS) continue;

                        double gap = minSide ? edge - (xAxis ? box.maxX() : box.maxZ())
                                : (xAxis ? box.minX() : box.minZ()) - edge;
                        if (gap < -EPS || gap >= playerSize) continue;

                        adjust = Math.min(adjust, gap - playerSize);
                    }
                }
            }
        }
        return adjust;
    }

    public boolean isTryingToLandOn() {
        if (Player.getLatest() == null) return false;

        BoundingBox3D playerBB = Player.getLatest().getBoundingBox();
        BoundingBox3D lastPlayerBB = Player.getLatest().getLastBoundingBox();

        if (landingMode != LandingMode.ENTER)
            return playerBB.minY() <= boundingBox.maxY() && lastPlayerBB.minY() > boundingBox.maxY();
        else
            return playerBB.minY() < boundingBox.maxY() && playerBB.minY() >= boundingBox.minY() && playerBB.minY() < lastPlayerBB.minY();
    }

    /** Corner distance if both axes are on or both off the block, otherwise the missed axis' offset. */
    public static double totalOffset(Vector3D offset) {
        double xSign = Math.signum(offset.getX());
        double zSign = Math.signum(offset.getZ());

        if (xSign <= 0 && zSign >= 0) {
            return offset.getX();
        } else if (xSign >= 0 && zSign <= 0) {
            return offset.getZ();
        } else if (xSign <= 0 && zSign <= 0) {
            return -offset.lengthXZ();
        } else {
            return offset.lengthXZ();
        }
    }

    @Override
    public int hashCode() {
        return this.boundingBox.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LandingBlock && this.boundingBox.equals(((LandingBlock) obj).boundingBox);
    }

    public enum LandingMode {
        LAND("Land"),
        HIT("Hit"),
        Z_NEO("Z Neo"),
        ENTER("Enter");

        public final String displayString;

        LandingMode(String displayString) {
            this.displayString = displayString;
        }

        public BoundingBox3D getPlayerBB() {
            if (Player.getLatest() == null) return null;

            switch (this) {
                case Z_NEO:
                    if (Player.getBeforeLatest() == null) return null;
                    return Player.getBeforeLatest().getLastBoundingBox();
                case HIT:
                case ENTER:
                    return Player.getLatest().getBoundingBox();
                case LAND:
                default:
                    return Player.getLatest().getLastBoundingBox();
            }
        }

        public LandingMode getNext() {
            return LandingMode.values()[(Arrays.asList(LandingMode.values()).indexOf(this) + 1) % LandingMode.values().length];
        }


        @Override
        public String toString() {
            return displayString;
        }
    }
}
