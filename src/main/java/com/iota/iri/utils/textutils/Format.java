package com.iota.iri.utils.textutils;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public final class Format {

    public static String leftpad(Object object, int size) {
        return StringUtils.leftPad(Objects.toString(object, ""), size);
    }

    public static String rightpad(Object object, int size) {
        return StringUtils.rightPad(Objects.toString(object, ""), size);
    }

    public static String readableBytes(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + "B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.2f%sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String readableNumber(long number) {
        int unit = 1000;
        if (number < unit) return String.valueOf(number);
        int exp = (int) (Math.log(number) / Math.log(unit));
        String pre = String.valueOf("kMGTPE".charAt(exp - 1));
        return String.format("%.2f%s", number / Math.pow(unit, exp), pre);
    }
}
