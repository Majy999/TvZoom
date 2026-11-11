package tvzoom;

import android.graphics.Rect;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Display;
import android.view.SurfaceControl;

import java.lang.reflect.Method;

public final class TvZoom {
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;

    private TvZoom() {}

    public static void main(String[] args) {
        try {
            String action = args.length > 0 ? args[0] : "status";
            switch (action) {
                case "status":
                    status();
                    break;
                case "set": {
                    int percent = args.length > 1 ? Integer.parseInt(args[1]) : 90;
                    if (percent < 10 || percent > 100) {
                        throw new IllegalArgumentException("percent must be 10..100");
                    }
                    setPercent(percent);
                    break;
                }
                case "fit": {
                    if (args.length < 3) {
                        throw new IllegalArgumentException("fit needs WIDTH HEIGHT");
                    }
                    fit(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                    break;
                }
                case "scale": {
                    if (args.length < 3) {
                        throw new IllegalArgumentException("scale needs WIDTH HEIGHT [X Y]");
                    }
                    int x = args.length > 4 ? Integer.parseInt(args[3]) : Integer.MIN_VALUE;
                    int y = args.length > 4 ? Integer.parseInt(args[4]) : Integer.MIN_VALUE;
                    scale(Integer.parseInt(args[1]), Integer.parseInt(args[2]), x, y);
                    break;
                }
                case "rect": {
                    if (args.length < 5) {
                        throw new IllegalArgumentException("rect needs LEFT TOP RIGHT BOTTOM");
                    }
                    rect(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    break;
                }
                case "crop": {
                    if (args.length < 5) {
                        throw new IllegalArgumentException(
                                "crop needs LEFT TOP RIGHT BOTTOM");
                    }
                    crop(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    break;
                }
                case "reset":
                    reset();
                    break;
                default:
                    usage();
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Display getDisplay() throws Exception {
        Class<?> globalClass = Class.forName(
                "android.hardware.display.DisplayManagerGlobal");
        Object global = globalClass.getMethod("getInstance").invoke(null);
        return (Display) globalClass.getMethod("getRealDisplay", int.class)
                .invoke(global, Display.DEFAULT_DISPLAY);
    }

    private static int[] getPhysicalSize(Display display) {
        Display.Mode mode = display.getMode();
        if (mode != null) {
            return new int[] { mode.getPhysicalWidth(), mode.getPhysicalHeight() };
        }
        return new int[] { DEFAULT_WIDTH, DEFAULT_HEIGHT };
    }

    private static void status() throws Exception {
        Display display = getDisplay();
        int[] size = getPhysicalSize(display);
        System.out.println("displayId=" + display.getDisplayId());
        System.out.println("address=" + getDisplayAddress(display));
        System.out.println("physical=" + size[0] + "x" + size[1]);
        System.out.println("token=" + getDisplayToken(display));
    }

    private static void setPercent(int percent) throws Exception {
        Display display = getDisplay();
        int[] physical = getPhysicalSize(display);
        int w = physical[0] * percent / 100;
        int h = physical[1] * percent / 100;
        int left = (physical[0] - w) / 2;
        int top = (physical[1] - h) / 2;
        apply(display, new Rect(0, 0, physical[0], physical[1]),
                new Rect(left, top, left + w, top + h));
        System.out.println("set " + percent + "% -> displayRect="
                + new Rect(left, top, left + w, top + h));
    }

    private static void fit(int w, int h) throws Exception {
        scale(w, h, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private static void scale(int w, int h, int x, int y) throws Exception {
        Display display = getDisplay();
        int[] physical = getPhysicalSize(display);
        if (w <= 0 || h <= 0 || w > physical[0] || h > physical[1]) {
            throw new IllegalArgumentException("target size is outside the display");
        }
        int left = x == Integer.MIN_VALUE ? (physical[0] - w) / 2 : x;
        int top = y == Integer.MIN_VALUE ? (physical[1] - h) / 2 : y;
        Rect dest = new Rect(left, top, left + w, top + h);
        if (left < 0 || top < 0 || dest.right > physical[0] || dest.bottom > physical[1]) {
            throw new IllegalArgumentException("target rect is outside the display");
        }
        apply(display, new Rect(0, 0, physical[0], physical[1]), dest);
        System.out.println("scale " + physical[0] + "x" + physical[1]
                + " -> " + w + "x" + h + " at " + left + "," + top);
    }

    private static void rect(int left, int top, int right, int bottom) throws Exception {
        Display display = getDisplay();
        int[] physical = getPhysicalSize(display);
        Rect dest = new Rect(left, top, right, bottom);
        if (dest.isEmpty() || left < 0 || top < 0
                || right > physical[0] || bottom > physical[1]) {
            throw new IllegalArgumentException("target rect is outside the display");
        }
        apply(display, new Rect(0, 0, physical[0], physical[1]), dest);
        System.out.println("rect " + dest + " from full "
                + physical[0] + "x" + physical[1]);
    }

    private static void crop(int left, int top, int right, int bottom) throws Exception {
        Display display = getDisplay();
        int[] physical = getPhysicalSize(display);
        Rect source = new Rect(left, top, right, bottom);
        if (source.isEmpty() || left < 0 || top < 0
                || right > physical[0] || bottom > physical[1]) {
            throw new IllegalArgumentException("crop is outside the display");
        }
        int w = right - left;
        int h = bottom - top;
        int destLeft = (physical[0] - w) / 2;
        int destTop = (physical[1] - h) / 2;
        apply(display, source,
                new Rect(destLeft, destTop, destLeft + w, destTop + h));
        System.out.println("crop " + source + " -> displayRect="
                + new Rect(destLeft, destTop, destLeft + w, destTop + h));
    }

    private static void reset() throws Exception {
        Display display = getDisplay();
        int[] physical = getPhysicalSize(display);
        apply(display, new Rect(0, 0, physical[0], physical[1]),
                new Rect(0, 0, physical[0], physical[1]));
        System.out.println("reset to " + physical[0] + "x" + physical[1]);
    }

    private static void apply(Display display, Rect source, Rect dest) throws Exception {
        IBinder token = getDisplayToken(display);
        if (token == null) {
            throw new IllegalStateException("cannot obtain display token");
        }
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        Method m = SurfaceControl.Transaction.class.getMethod(
                "setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
        m.invoke(tx, token, 0, source, dest);
        tx.apply();
        tx.close();
    }

    private static IBinder getDisplayToken(Display display) throws Exception {
        long physicalId = -1L;
        Object address = getDisplayAddress(display);
        if (address != null
                && "android.view.DisplayAddress$Physical".equals(
                address.getClass().getName())) {
            physicalId = ((Long) address.getClass()
                    .getMethod("getPhysicalDisplayId")
                    .invoke(address)).longValue();
        }

        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder sf = (IBinder) getService.invoke(null, "SurfaceFlingerAIDL");
        if (sf == null) {
            sf = (IBinder) getService.invoke(null, "SurfaceFlinger");
        }
        if (sf == null) {
            throw new IllegalStateException("SurfaceFlinger service not found");
        }

        try {
            Class<?> iface = Class.forName("android.gui.ISurfaceComposer");
            Class<?> stub = Class.forName("android.gui.ISurfaceComposer$Stub");
            Object composer = stub.getMethod("asInterface", IBinder.class).invoke(null, sf);
            long[] ids = (long[]) iface.getMethod("getPhysicalDisplayIds").invoke(composer);
            long id = pickId(ids, physicalId);
            return (IBinder) iface.getMethod("getPhysicalDisplayToken", long.class)
                    .invoke(composer, id);
        } catch (Throwable ignored) {
            return getDisplayTokenByParcel(sf, physicalId);
        }
    }

    private static Object getDisplayAddress(Display display) {
        try {
            return Display.class.getMethod("getAddress").invoke(display);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long pickId(long[] ids, long preferred) {
        if (ids == null || ids.length == 0) {
            throw new IllegalStateException("no physical display ids");
        }
        for (long candidate : ids) {
            if (candidate == preferred) {
                return candidate;
            }
        }
        return ids[0];
    }

    private static IBinder getDisplayTokenByParcel(IBinder sf, long physicalId)
            throws Exception {
        int idsCode = transactionCode("getPhysicalDisplayIds");
        int tokenCode = transactionCode("getPhysicalDisplayToken");

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        long[] ids;
        try {
            data.writeInterfaceToken("android.gui.ISurfaceComposer");
            sf.transact(idsCode, data, reply, 0);
            reply.readException();
            ids = reply.createLongArray();
        } finally {
            data.recycle();
            reply.recycle();
        }

        long id = pickId(ids, physicalId);
        data = Parcel.obtain();
        reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.gui.ISurfaceComposer");
            data.writeLong(id);
            sf.transact(tokenCode, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static int transactionCode(String name) throws Exception {
        try {
            Class<?> stub = Class.forName("android.gui.ISurfaceComposer$Stub");
            return stub.getField("TRANSACTION_" + name).getInt(null);
        } catch (ClassNotFoundException e) {
            if (name.equals("getPhysicalDisplayIds")) {
                return 6;
            }
            if (name.equals("getPhysicalDisplayToken")) {
                return 7;
            }
            throw e;
        }
    }

    private static void usage() {
        System.out.println("usage: TvZoom status");
        System.out.println("       TvZoom set PERCENT");
        System.out.println("       TvZoom fit WIDTH HEIGHT");
        System.out.println("       TvZoom scale WIDTH HEIGHT [X Y]");
        System.out.println("       TvZoom rect LEFT TOP RIGHT BOTTOM");
        System.out.println("       TvZoom crop LEFT TOP RIGHT BOTTOM");
        System.out.println("       TvZoom reset");
    }
}
