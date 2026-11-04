import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.time.LocalTime;
import java.time.ZoneId;

public class Mover  implements Runnable {

    // Only jiggle if nobody has touched the mouse/keyboard for this long
    static final long IDLE_THRESHOLD_MS = 30000;

    // How often to look at the idle time
    static final long CHECK_EVERY_MS = 1000;

    // Quit for the day at this time (checked every loop, so it also catches waking from sleep)
    static final ZoneId STOP_ZONE = ZoneId.of("America/Costa_Rica");
    static final LocalTime STOP_AT = LocalTime.of(17, 0);

    static final String OS =System.getProperty("os.name").toLowerCase();

    // Windows functions for reading idle time, set up on first use
    static MethodHandle getLastInputInfo, getTickCount;

    int time, pixels;
    boolean startStop, left = true;
    Point lastMovedTo, lastSeen;
    long lastMoveAt, lastActivityAt = System.currentTimeMillis();
    boolean paused;

    public Mover(int x, boolean b, int pix) {

        time = x;
        startStop = b;
        pixels = pix;

    }

    public void run() {

        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
            return;
        }

        while (startStop) {

            if (!LocalTime.now(STOP_ZONE).isBefore(STOP_AT)) {
                System.out.println("It's past " + STOP_AT + " Costa Rica time, exiting");
                System.exit(0);
            }

            Point current = null;
            try
            {
                current = MouseInfo.getPointerInfo().getLocation();
            }catch (NullPointerException e)
            {
                //JOptionPane.showMessageDialog(null, "AHHH LA VARA SE DESVERGOOOO");
                //System.exit(1);
            }

            if (current != null) {
                if (getIdleMillis(current) < IDLE_THRESHOLD_MS) {
                    if (!paused) {
                        System.out.println("User active, pausing");
                        paused = true;
                    }
                }
                else if (System.currentTimeMillis() - lastMoveAt >= time) {
                    if (paused) {
                        System.out.println("User idle, resuming");
                        paused = false;
                    }
                    int newX = left ? current.x - pixels : current.x + pixels;
                    robot.mouseMove(newX, current.y);
                    lastMovedTo = new Point(newX, current.y);
                    lastMoveAt = System.currentTimeMillis();
                    left = !left;
                }
            }

            try {
                Thread.sleep(CHECK_EVERY_MS);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }// Close while loop
    }

    // Time since the user last touched an input. Our own mouse moves may reset the system
    // idle timer; that's fine because we check every second instead of once per interval.
    private long getIdleMillis(Point current) {
        long idle = getSystemIdleMillis();
        if (idle >= 0) {
            return idle;
        }
        // Fallback when idle time isn't available: a pointer move we didn't make counts as activity
        if (lastSeen != null && !current.equals(lastSeen) && !current.equals(lastMovedTo)) {
            lastActivityAt = System.currentTimeMillis();
        }
        lastSeen = current;
        return System.currentTimeMillis() - lastActivityAt;
    }

    // Time since the last keyboard/mouse input, or -1 if this OS isn't supported
    private static long getSystemIdleMillis() {
        if (OS.startsWith("windows")) {
            return getWindowsIdleMillis();
        }
        if (OS.startsWith("mac")) {
            return getMacIdleMillis();
        }
        return -1;
    }

    private static long getMacIdleMillis() {
        try {
            Process p = new ProcessBuilder("ioreg", "-c", "IOHIDSystem", "-d", "1", "-r", "-k", "HIDIdleTime").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int i = line.indexOf("\"HIDIdleTime\" = ");
                    if (i >= 0) {
                        long nanos = Long.parseLong(line.substring(i + 16).trim());
                        return nanos / 1000000;
                    }
                }
            } finally {
                p.destroy();
            }
        } catch (Exception e) {
            // ioreg unavailable
        }
        return -1;
    }

    // Calls the Windows API directly: GetTickCount() - GetLastInputInfo().dwTime
    private static long getWindowsIdleMillis() {
        try {
            if (getLastInputInfo == null) {
                Linker linker = Linker.nativeLinker();
                getLastInputInfo = linker.downcallHandle(
                        SymbolLookup.libraryLookup("user32", Arena.global()).findOrThrow("GetLastInputInfo"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                getTickCount = linker.downcallHandle(
                        SymbolLookup.libraryLookup("kernel32", Arena.global()).findOrThrow("GetTickCount"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT));
            }
            try (Arena arena = Arena.ofConfined()) {
                // LASTINPUTINFO { UINT cbSize; DWORD dwTime; }
                MemorySegment info = arena.allocate(8);
                info.set(ValueLayout.JAVA_INT, 0, 8);
                if ((int) getLastInputInfo.invokeExact(info) == 0) {
                    return -1;
                }
                int lastInput = info.get(ValueLayout.JAVA_INT, 4);
                int now = (int) getTickCount.invokeExact();
                // Both are 32-bit tick counts that wrap, so subtract as unsigned
                return Integer.toUnsignedLong(now - lastInput);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    void clearData(){

        time = 0;
        pixels = 0;
    }
    void setTime(int x){

        time = x * 1000;
    }

    void setPixels(int x){
        pixels = x;
    }


    public void setStartStop(boolean value) {
        startStop = value;
    }

}
