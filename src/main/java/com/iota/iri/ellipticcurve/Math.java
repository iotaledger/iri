package com.iota.iri.ellipticcurve;
import java.math.BigInteger;


public final class Math {

    /**
     * Fast way to multiply point and scalar in elliptic curves
     *
     * @param p First Point to multiply
     * @param n Scalar to multiply
     * @param nN Order of the elliptic curve
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @param aA Coefficient of the first-order term of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return Point that represents the sum of First and Second Point
     */
    public static Point multiply(Point p, BigInteger n, BigInteger nN, BigInteger aA, BigInteger pP) {
        return fromJacobian(jacobianMultiply(toJacobian(p), n, nN, aA, pP), pP);
    }

    /**
     * Fast way to add two points in elliptic curves
     *
     * @param p First Point you want to add
     * @param q Second Point you want to add
     * @param aA Coefficient of the first-order term of the equation Y^2 = X^3 + A*X + B (mod P)
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return Point that represents the sum of First and Second Point
     */
    public static Point add(Point p, Point q, BigInteger aA, BigInteger pP) {
        return fromJacobian(jacobianAdd(toJacobian(p), toJacobian(q), aA, pP), pP);
    }

    /**
     * Extended Euclidean Algorithm. It's the 'division' in elliptic curves
     *
     * @param x Divisor
     * @param n Mod for division
     * @return Value representing the division
     */
    public static BigInteger inv(BigInteger x, BigInteger n) {
        if (x.compareTo(BigInteger.ZERO) == 0) {
            return BigInteger.ZERO;
        }
        BigInteger lm = BigInteger.ONE;
        BigInteger hm = BigInteger.ZERO;
        BigInteger high = n;
        BigInteger low = x.mod(n);
        BigInteger r, nm, nw;
        while (low.compareTo(BigInteger.ONE) > 0) {
            r = high.divide(low);
            nm = hm.subtract(lm.multiply(r));
            nw = high.subtract(low.multiply(r));
            high = low;
            hm = lm;
            low = nw;
            lm = nm;
        }
        return lm.mod(n);
    }

    /**
     * Convert point to Jacobian coordinates
     *
     * @param p the point you want to transform
     * @return Point in Jacobian coordinates
     */
    public static Point toJacobian(Point p) {
        return new Point(p.x, p.y, BigInteger.ONE);
    }

    /**
     * Convert point back from Jacobian coordinates
     *
     * @param p the point you want to transform
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return Point in default coordinates
     */
    public static Point fromJacobian(Point p, BigInteger pP) {
        BigInteger z = inv(p.z, pP);
        BigInteger x = p.x.multiply(z.pow(2)).mod(pP);
        BigInteger y = p.y.multiply(z.pow(3)).mod(pP);
        return new Point(x, y, BigInteger.ZERO);
    }

    /**
     * Double a point in elliptic curves
     *
     * @param p the point you want to transform
     * @param aA Coefficient of the first-order term of the equation Y^2 = X^3 + A*X + B (mod P)
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return the result point doubled in elliptic curves
     */
    public static Point jacobianDouble(Point p, BigInteger aA, BigInteger pP) {
        if (p.y == null || p.y.compareTo(BigInteger.ZERO) == 0) {
            return new Point(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        }
        BigInteger ysq = p.y.pow(2).mod(pP);
        BigInteger sS = BigInteger.valueOf(4).multiply(p.x).multiply(ysq).mod(pP);
        BigInteger mM = BigInteger.valueOf(3).multiply(p.x.pow(2)).add(aA.multiply(p.z.pow(4))).mod(pP);
        BigInteger nx = mM.pow(2).subtract(BigInteger.valueOf(2).multiply(sS)).mod(pP);
        BigInteger ny = mM.multiply(sS.subtract(nx)).subtract(BigInteger.valueOf(8).multiply(ysq.pow(2))).mod(pP);
        BigInteger nz = BigInteger.valueOf(2).multiply(p.y).multiply(p.z).mod(pP);
        return new Point(nx, ny, nz);
    }

    /**
     * Add two points in elliptic curves
     *
     * @param p First Point you want to add
     * @param q Second Point you want to add
     * @param aA Coefficient of the first-order term of the equation Y^2 = X^3 + A*X + B (mod P)
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return Point that represents the sum of First and Second Point
     */
    public static Point jacobianAdd(Point p, Point q, BigInteger aA, BigInteger pP) {
        if (p.y == null || p.y.compareTo(BigInteger.ZERO) == 0) {
            return q;
        }
        if (q.y == null || q.y.compareTo(BigInteger.ZERO) == 0) {
            return p;
        }
        BigInteger uU1 = p.x.multiply(q.z.pow(2)).mod(pP);
        BigInteger uU2 = q.x.multiply(p.z.pow(2)).mod(pP);
        BigInteger sS1 = p.y.multiply(q.z.pow(3)).mod(pP);
        BigInteger sS2 = q.y.multiply(p.z.pow(3)).mod(pP);
        if (uU1.compareTo(uU2) == 0) {
            if (sS1.compareTo(sS2) != 0) {
                return new Point(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE);
            }
            return jacobianDouble(p, aA, pP);
        }
        BigInteger hH = uU2.subtract(uU1);
        BigInteger rR = sS2.subtract(sS1);
        BigInteger hH2 = hH.multiply(hH).mod(pP);
        BigInteger hH3 = hH.multiply(hH2).mod(pP);
        BigInteger uU1H2 = uU1.multiply(hH2).mod(pP);
        BigInteger nx = rR.pow(2).subtract(hH3).subtract(BigInteger.valueOf(2).multiply(uU1H2)).mod(pP);
        BigInteger ny = rR.multiply(uU1H2.subtract(nx)).subtract(sS1.multiply(hH3)).mod(pP);
        BigInteger nz = hH.multiply(p.z).multiply(q.z).mod(pP);
        return new Point(nx, ny, nz);
    }

    /**
     * Multiply point and scalar in elliptic curves
     *
     * @param p First Point to multiply
     * @param n Scalar to multiply
     * @param nN Order of the elliptic curve
     * @param aA Coefficient of the first-order term of the equation Y^2 = X^3 + A*X + B (mod P)
     * @param pP Prime number in the module of the equation Y^2 = X^3 + A*X + B (mod P)
     * @return Point that represents the product of First Point and scalar
     */
    public static Point jacobianMultiply(Point p, BigInteger n, BigInteger nN, BigInteger aA, BigInteger pP) {
        if (BigInteger.ZERO.compareTo(p.y) == 0 || BigInteger.ZERO.compareTo(n) == 0) {
            return new Point(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE);
        }
        if (BigInteger.ONE.compareTo(n) == 0) {
            return p;
        }
        if (n.compareTo(BigInteger.ZERO) < 0 || n.compareTo(nN) >= 0) {
            return jacobianMultiply(p, n.mod(nN), nN, aA, pP);
        }
        if (n.mod(BigInteger.valueOf(2)).compareTo(BigInteger.ZERO) == 0) {
            return jacobianDouble(jacobianMultiply(p, n.divide(BigInteger.valueOf(2)), nN, aA, pP), aA, pP);
        }
        if (n.mod(BigInteger.valueOf(2)).compareTo(BigInteger.ONE) == 0) {
            return jacobianAdd(jacobianDouble(jacobianMultiply(p, n.divide(BigInteger.valueOf(2)), nN, aA, pP), aA, pP), p, aA, pP);
        }
        return null;
    }
}
