package io.github.kurrycat.mpkmod.compatibility.forge_1_8;

import com.sun.jna.Native;
import io.github.kurrycat.mpkmod.compatibility.API;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ~1ms key timestamps on Windows, where LWJGL 2 event times only advance in ~15.6ms steps.
 * Samples the watched keys on a background thread and records each press/release.
 */
public final class WindowsKeyTimer {
    private static final long MAX_AGE_NANOS = 5_000_000_000L;
    private static final int MAX_PENDING = 32;
    private static final long IDLE_SLEEP_MS = 50;

    private static final Object LOCK = new Object();
    private static final Map<Integer, ArrayDeque<Transition>> pending = new HashMap<>();

    private static int[] lwjglToVk;
    private static volatile boolean running = false;
    private static volatile boolean recording = false;
    private static volatile int[] watchedKeys = new int[0];
    private static int[] lastRequestedKeys = new int[0];

    private WindowsKeyTimer() {
    }

    /** Does nothing outside of Windows or if the native calls are unavailable. */
    public static void start() {
        if (running) return;
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) return;

        try {
            lwjglToVk = buildLwjglToVkMap();
            User32.GetAsyncKeyState(0); // loads the native library
        } catch (Throwable t) {
            API.LOGGER.warn("Precise key timing unavailable, falling back to LWJGL event times", t);
            return;
        }

        Thread thread = new Thread(WindowsKeyTimer::sampleLoop, "MPKMod key timer");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        running = true;
        thread.start();
        API.LOGGER.info("Precise key timing enabled");
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Called by the game thread every frame and tick.
     *
     * @param keys      LWJGL key codes to watch; non-keyboard codes are ignored
     * @param recording whether timings are currently needed
     */
    public static void update(int[] keys, boolean recording) {
        if (!running) return;

        if (!Arrays.equals(keys, lastRequestedKeys)) {
            lastRequestedKeys = keys.clone();
            watchedKeys = Arrays.stream(keys).filter(k -> k > 0 && k < 256).distinct().toArray();
        }

        if (WindowsKeyTimer.recording != recording) {
            WindowsKeyTimer.recording = recording;
            synchronized (LOCK) {
                pending.clear();
            }
        }
    }

    /** Oldest unclaimed {@link System#nanoTime()} at which the key changed to this state, or null. */
    public static Long take(int keyCode, boolean down) {
        if (!running) return null;
        long now = System.nanoTime();

        synchronized (LOCK) {
            ArrayDeque<Transition> queue = pending.get(keyCode);
            if (queue == null) return null;

            Iterator<Transition> it = queue.iterator();
            while (it.hasNext()) {
                Transition t = it.next();
                it.remove();
                // Mismatching or stale entries come from missed changes
                if (t.down == down && now - t.nanos <= MAX_AGE_NANOS) return t.nanos;
            }
            return null;
        }
    }

    private static void record(int keyCode, long nanos, boolean down) {
        synchronized (LOCK) {
            ArrayDeque<Transition> queue = pending.get(keyCode);
            if (queue == null) {
                queue = new ArrayDeque<>();
                pending.put(keyCode, queue);
            }
            if (queue.size() >= MAX_PENDING) queue.removeFirst();
            queue.addLast(new Transition(nanos, down));
        }
    }

    private static void sampleLoop() {
        boolean[] known = new boolean[256];
        boolean[] lastDown = new boolean[256];
        int[] lastKeys = null;
        long previous = System.nanoTime();

        try {
            while (true) {
                int[] keys = watchedKeys;
                if (keys != lastKeys) {
                    Arrays.fill(known, false);
                    lastKeys = keys;
                }

                if (!recording) {
                    Arrays.fill(known, false);
                    Thread.sleep(IDLE_SLEEP_MS);
                    previous = System.nanoTime();
                    continue;
                }

                long now = System.nanoTime();
                // A change happened between the previous sample and now
                long estimate = previous + (now - previous) / 2;

                for (int keyCode : keys) {
                    int vk = lwjglToVk[keyCode];
                    if (vk == 0) continue;

                    boolean down = (User32.GetAsyncKeyState(vk) & 0x8000) != 0;
                    if (!known[keyCode]) {
                        known[keyCode] = true;
                        lastDown[keyCode] = down;
                    } else if (down != lastDown[keyCode]) {
                        lastDown[keyCode] = down;
                        record(keyCode, estimate, down);
                    }
                }

                previous = now;
                Thread.sleep(1);
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable t) {
            API.LOGGER.warn("Precise key timing stopped", t);
        } finally {
            running = false;
        }
    }

    /** LWJGL key code to Windows virtual key, by reversing LWJGL's own mapping. */
    private static int[] buildLwjglToVkMap() throws Exception {
        Class<?> keycodes = Class.forName("org.lwjgl.opengl.WindowsKeycodes");
        Method map = keycodes.getDeclaredMethod("mapVirtualKeyToLWJGLCode", int.class);
        map.setAccessible(true);

        int[] table = new int[256];
        for (int vk = 1; vk < 256; vk++) {
            // Skip generic shift/ctrl/alt in favor of the left/right ones
            if (vk == 0x10 || vk == 0x11 || vk == 0x12) continue;
            int code = (Integer) map.invoke(null, vk);
            if (code > 0 && code < 256 && table[code] == 0) table[code] = vk;
        }
        return table;
    }

    private static final class Transition {
        final long nanos;
        final boolean down;

        Transition(long nanos, boolean down) {
            this.nanos = nanos;
            this.down = down;
        }
    }

    private static final class User32 {
        static {
            Native.register("user32");
        }

        static native short GetAsyncKeyState(int vKey);
    }
}
