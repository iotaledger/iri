package com.iota.iri.ellipticcurve;
import com.iota.iri.ellipticcurve.utils.ByteString;
import com.iota.iri.ellipticcurve.utils.Der;
import com.iota.iri.ellipticcurve.utils.BinaryAscii;
import java.util.Arrays;
import static com.iota.iri.ellipticcurve.Curve.secp256k1;
import static com.iota.iri.ellipticcurve.Curve.supportedCurves;


public class PublicKey {

    public Point point;
    public Curve curve;

    public PublicKey(Point point, Curve curve) {
        this.point = point;
        this.curve = curve;
    }

    public ByteString toByteString() {
        return toByteString(false);
    }

    public ByteString toByteString(boolean encoded) {
        ByteString xStr = BinaryAscii.stringFromNumber(point.x, curve.length());
        ByteString yStr = BinaryAscii.stringFromNumber(point.y, curve.length());
        xStr.insert(yStr.getBytes());
        if(encoded) {
            xStr.insert(0, new byte[]{0, 4} );
        }
        return xStr;
    }

    public ByteString toDer() {
        long[] oidEcPublicKey = new long[]{1, 2, 840, 10045, 2, 1};
        ByteString encodeEcAndOid = Der.encodeSequence(Der.encodeOid(oidEcPublicKey), Der.encodeOid(curve.oid));
        return Der.encodeSequence(encodeEcAndOid, Der.encodeBitString(this.toByteString(true)));
    }

    public String toPem() {
        return Der.toPem(this.toDer(), "PUBLIC KEY");
    }

    public static PublicKey fromPem(String string) {
        return PublicKey.fromDer(Der.fromPem(string));
    }

    public static PublicKey fromDer(ByteString string) {
        ByteString[] str = Der.removeSequence(string);
        ByteString s1 = str[0];
        ByteString empty = str[1];
        if (!empty.isEmpty()) {
            throw new RuntimeException (String.format("trailing junk after DER pubkey: %s", BinaryAscii.hexFromBinary(empty)));
        }
        str = Der.removeSequence(s1);
        ByteString s2 = str[0];
        ByteString pointStrBitstring = str[1];
        Object[] o = Der.removeObject(s2);
        ByteString rest = (ByteString) o[1];
        o = Der.removeObject(rest);
        long[] oidCurve = (long[]) o[0];
        empty = (ByteString) o[1];
        if (!empty.isEmpty()) {
            throw new RuntimeException (String.format("trailing junk after DER pubkey objects: %s", BinaryAscii.hexFromBinary(empty)));
        }

        Curve curve = (Curve) Curve.curvesByOid.get(Arrays.hashCode(oidCurve));
        if (curve == null) {
            throw new RuntimeException(String.format("Unknown curve with oid %s. I only know about these: %s", Arrays.toString(oidCurve), Arrays.toString(supportedCurves.toArray())));
        }

        str = Der.removeBitString(pointStrBitstring);
        ByteString pointStr = str[0];
        empty = str[1];
        if (!empty.isEmpty()) {
            throw new RuntimeException (String.format("trailing junk after pubkey pointstring: %s", BinaryAscii.hexFromBinary(empty)));
        }
        return PublicKey.fromString(pointStr.substring(2), curve);
    }

    public static PublicKey fromString(ByteString string, Curve curve, boolean validatePoint) {
        int baselen = curve.length();

        ByteString xs = string.substring(0, baselen);
        ByteString ys = string.substring(baselen);

        Point p = new Point(BinaryAscii.numberFromString(xs.getBytes()), BinaryAscii.numberFromString(ys.getBytes()));

        if (validatePoint && !curve.contains(p)) {
            throw new  RuntimeException(String.format("point (%s,%s) is not valid", p.x, p.y));
        }

        return new PublicKey(p, curve);
    }

    public static PublicKey fromString(ByteString string, Curve curve) {
        return fromString(string, curve, true);
    }

    public static PublicKey fromString(ByteString string, boolean validatePoint) {
        return fromString(string, secp256k1, validatePoint);
    }

    public static PublicKey fromString(ByteString string) {
        return fromString(string, true);
    }
}
