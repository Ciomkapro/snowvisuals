package pl.snowvisuals;

public final class VisibilityMath {

    private VisibilityMath() {}

    public static boolean intersects(
            double ex,
            double ey,
            double ez,

            double tx0,
            double ty0,
            double tz0,

            double tx1,
            double ty1,
            double tz1,

            double bx0,
            double by0,
            double bz0,

            double bx1,
            double by1,
            double bz1
    ) {

        double[] t = {0, 1};

        return axis(t, ex, tx0, tx1, bx0, bx1)
                && axis(t, ey, ty0, ty1, by0, by1)
                && axis(t, ez, tz0, tz1, bz0, bz1);
    }

    private static boolean axis(
            double[] t,
            double e,
            double min,
            double max,
            double b0,
            double b1
    ) {

        return limit(t, min - e, b1 - e - 1e-7)
                && limit(t, e - max, e - b0 - 1e-7);
    }

    private static boolean limit(
            double[] t,
            double a,
            double b
    ) {

        if (Math.abs(a) < 1e-12) {
            return b >= 0;
        }

        if (a > 0) {
            t[1] = Math.min(t[1], b / a);
        } else {
            t[0] = Math.max(t[0], b / a);
        }

        return t[0] <= t[1];
    }
}