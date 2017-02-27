package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Created by paul on 2/15/17.
 */
public class Trinary {

    public static int not(int t) {
        return -t;
    }
    public static int inc(int t) {
        return t == 1 ? -1 : t + 1 ;
    }
    public static int dec(int t) {
        return t == -1 ? 1 : t - 1 ;
    }
    public static int is_false(int t) {
        return t == -1? 1: -1;
    }
    public static int is_undefined(int t) {
        return t == 0? 1: -1;
    }
    public static int is_true(int t) {
        return t == 1? 1: -1;
    }
    public static int min(int t) {
        return t == -1? 1: 0;
    }
    public static int max(int t) {
        return t == 1? 1: 0;
    }
    public static IntFunction<Integer> and(int a) {
        return (b) -> a == -1 || b== -1 ? -1 : (a + b == 2 ? 1 : 0);
    }
    public static IntFunction<Integer> or(int a) {
        return (b) -> a == 1 || b == 1 ? 1 : a + b == -2 ? -1 : 0;
    }

    public static IntFunction<Integer> xor(int a) {
        return (b) -> a == 0 || b == 0 ? 0 : ((a ^ b) == 0 ? 1 : -1);
    }

    public static IntFunction<Integer> cons(int a) {
        return (b) -> (a ^ b) == 0 ? a :  0;
    }
    public static IntFunction<Integer> any(int a) {
        return (b) -> a + b == -2 ? -1 : (a + b == 2 ? 1 : a + b);
    }
    public static IntFunction<Integer> comp(int a) {
        return (b) -> a == b ? 1 : -1;
    }
    public static IntFunction<Integer> sum (int a) {
        return (b) -> {
            int s = a + b;
            return s == 2 ? -1 : s == -2 ? 1 : s;
        };
    }
    public static Function<Integer, int[]> halfAdd(int a) {
        return (carry) -> new int[]{sum(a).apply(carry), cons(a).apply(carry)};
    }
    public static Function<int[], int[]> fullAdd(int a) {
        return (in) -> {
            int b = in[0];
            int carryIn = in[1];
            int[] sCa = halfAdd(a).apply(b);
            int[] siCb = halfAdd(sCa[0]).apply(carryIn);
            return new int[]{siCb[0], any(sCa[1]).apply(siCb[1])};
        };
    }

    private static Function<Integer, Function<Integer, int[]>> lookAheadNode(int c0) {
        return (p0) -> (p1) -> new int[]{ cons(c0).apply(p0), cons(p0).apply(p1) };
    }
    private static IntFunction<Integer> lookAheadRoot(int c0) {
        return (p0) -> cons(c0).apply(p0);
    }

    private static int carry(final int[] a, final int i) {
        if(a[i] > Converter.MAX_TRIT_VALUE) {
            a[i] = Converter.MIN_TRIT_VALUE;
            return Converter.MAX_TRIT_VALUE;
        } else if (a[i] < Converter.MIN_TRIT_VALUE) {
            a[i] = Converter.MAX_TRIT_VALUE;
            return Converter.MIN_TRIT_VALUE;
        }
        return 0;
    }

    public static void addSeries (final int[] out, final int[] b) {
        int carry = 0, i;
        for(i = 0; i < b.length; i++) {
            if(i == out.length) return;
            out[i] = carry + b[i];
            carry = carry(out, i);
        }
        while(i < out.length) {
            if(carry == 0) {
                break;
            } else {
                out[i] += carry;
                carry = carry(out, i++);
            }
        }
    }
    /*
    public static int[] increment(int[] trits) {
        int lz = 31-Integer.numberOfLeadingZeros(trits.length);
        Function<Integer, int[]> tree = (i) -> new int[]{1};
        int c_out = IntStream.range(0, lz)
                        .map(i -> {
                            if(lz-i-1 != 0) {
                                lookAhead(left, right)
                            } else {
                                lookAhead(x%(2^i))??????
                            }
                        });
        return trits;
    }
    */
}
