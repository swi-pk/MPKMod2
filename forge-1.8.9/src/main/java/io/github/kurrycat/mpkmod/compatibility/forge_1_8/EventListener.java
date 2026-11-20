package io.github.kurrycat.mpkmod.compatibility.forge_1_8;

import io.github.kurrycat.mpkmod.compatibility.API;
import io.github.kurrycat.mpkmod.compatibility.MCClasses.InputConstants;
import io.github.kurrycat.mpkmod.compatibility.MCClasses.Player;
import io.github.kurrycat.mpkmod.settings.Settings;
import io.github.kurrycat.mpkmod.ticks.ButtonMS;
import io.github.kurrycat.mpkmod.ticks.ButtonMSList;
import io.github.kurrycat.mpkmod.util.BoundingBox3D;
import io.github.kurrycat.mpkmod.util.Mouse;
import io.github.kurrycat.mpkmod.util.Vector3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.util.Map;

@SuppressWarnings("unused")
public class EventListener {
    private static final ButtonMSList timeQueue = new ButtonMSList();

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void onEvent(InputEvent.KeyInputEvent event) {
        int keyCode = Keyboard.getEventKey();
        String key = Keyboard.getKeyName(keyCode);
        boolean pressed = Keyboard.getEventKeyState();

        if (keyCode == 0) {
            char c = Keyboard.getEventCharacter();
            keyCode = 256 + c;
            key = String.valueOf(c);
        }

        int[] keys = movementKeyCodes();

        Long eventNanos = null;
        for (int i = 0; i < keys.length; i++) {
            if (keyCode == keys[i]) {
                if (eventNanos == null) eventNanos = getEventNanos(keyCode, pressed);
                timeQueue.add(ButtonMS.of(ButtonMS.Button.values()[i], eventNanos, pressed));
            }
        }


        API.Events.onKeyInput(InputConstants.convert(keyCode), key, pressed);

        if (pressed) {
            checkKeyBinding(keyCode);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        API.Events.onMouseInput(
                Mouse.Button.fromInt(event.button),
                event.button == -1 ? Mouse.State.NONE : (event.buttonstate ? Mouse.State.DOWN : Mouse.State.UP),
                event.x, event.y, event.dx, event.dy,
                event.dwheel, event.nanoseconds
        );

        if (event.buttonstate)
            checkKeyBinding(event.button - 100);
    }

    /** In {@link ButtonMS.Button} order. */
    private static int[] movementKeyCodes() {
        return fillMovementKeyCodes(new int[7]);
    }

    private static int[] fillMovementKeyCodes(int[] keys) {
        GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;
        keys[0] = gameSettings.keyBindForward.getKeyCode();
        keys[1] = gameSettings.keyBindLeft.getKeyCode();
        keys[2] = gameSettings.keyBindBack.getKeyCode();
        keys[3] = gameSettings.keyBindRight.getKeyCode();
        keys[4] = gameSettings.keyBindSprint.getKeyCode();
        keys[5] = gameSettings.keyBindSneak.getKeyCode();
        keys[6] = gameSettings.keyBindJump.getKeyCode();
        return keys;
    }

    /** Prefers {@link WindowsKeyTimer}, as LWJGL event times are only ~15.6ms precise on Windows. */
    private static long getEventNanos(int keyCode, boolean pressed) {
        if (!WindowsKeyTimer.isRunning()) return Keyboard.getEventNanoseconds();

        Long nanos = WindowsKeyTimer.take(keyCode, pressed);
        // Missed by the sampler: fall back to the same clock
        return nanos != null ? nanos : System.nanoTime();
    }

    private static final int[] keyTimerKeys = new int[7];

    private static void updateKeyTimer() {
        if (!WindowsKeyTimer.isRunning()) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean needed = Settings.renderLastTimingMS.getValue()
                && Display.isActive()
                && mc.currentScreen == null;
        WindowsKeyTimer.update(fillMovementKeyCodes(keyTimerKeys), needed);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) updateKeyTimer();
    }

    private void checkKeyBinding(int keyCode) {
        if (Minecraft.getMinecraft().currentScreen != null) return;

        for (Map.Entry<String, KeyBinding> keyBindingEntry : MPKMod.keyBindingMap.entrySet()) {
            KeyBinding keyBinding = keyBindingEntry.getValue();
            String keyBindingId = keyBindingEntry.getKey();

            if (keyBinding.getKeyCode() == keyCode) {
                API.Events.onKeybind(keyBindingId);
                return;
            }
        }
    }


    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) updateKeyTimer();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isGamePaused() || mc.theWorld == null) return;
        Entity cameraEntity = mc.getRenderViewEntity();

        if (e.type != TickEvent.Type.CLIENT) return;
        if (e.side != Side.CLIENT) return;

        if (cameraEntity != null && e.phase == TickEvent.Phase.END) {
            AxisAlignedBB cameraEntityBB = cameraEntity.getEntityBoundingBox();
            Player mpkPlayer = new Player()
                    .setPos(new Vector3D(cameraEntity.posX, cameraEntity.posY, cameraEntity.posZ))
                    .setLastPos(new Vector3D(cameraEntity.lastTickPosX, cameraEntity.lastTickPosY, cameraEntity.lastTickPosZ))
                    .setMotion(new Vector3D(cameraEntity.motionX, cameraEntity.motionY, cameraEntity.motionZ))
                    .setRotation(cameraEntity.rotationYaw, cameraEntity.rotationPitch)
                    .setOnGround(cameraEntity.onGround)
                    .setSprinting(cameraEntity.isSprinting())
                    .setBoundingBox(new BoundingBox3D(
                        new Vector3D(cameraEntityBB.minX, cameraEntityBB.minY, cameraEntityBB.minZ),
                        new Vector3D(cameraEntityBB.maxX, cameraEntityBB.maxY, cameraEntityBB.maxZ)
                    ))
                    .constructKeyInput()
                    .setKeyMSList(timeQueue.copy());

            if (cameraEntity instanceof EntityPlayer) {
                mpkPlayer.setFlying(((EntityPlayer) cameraEntity).capabilities.isFlying);
            }

            mpkPlayer.buildAndSave();

            timeQueue.clear();
        }
        if (e.phase == TickEvent.Phase.START) {
            API.Events.onTickStart();
        } else if (e.phase == TickEvent.Phase.END)
            API.Events.onTickEnd();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent e) {
        if (e.type == RenderGameOverlayEvent.ElementType.TEXT && e instanceof RenderGameOverlayEvent.Text)
            API.Events.onRenderOverlay();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void onRenderWorld(RenderWorldLastEvent e) {
        API.Events.onRenderWorldOverlay(e.partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent e) {
        API.Events.onServerConnect(e.isLocal);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        API.Events.onServerDisconnect();
    }
}
