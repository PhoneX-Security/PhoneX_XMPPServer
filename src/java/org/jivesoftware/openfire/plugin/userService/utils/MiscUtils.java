package org.jivesoftware.openfire.plugin.userService.utils;

import java.util.Collection;

/**
 * Created by dusanklinec on 15.07.15.
 */
public class MiscUtils {
    public static int collectionSize(Collection c){
        return c==null ? 0 : c.size();
    }

    /**
     * Integer modulus.
     *
     * @param x
     * @param n
     * @return
     */
    public static int mod(int x, int n){
        return ((x % n) + n) % n;
    }

    public static int numberOfLeadingZeros(int i) {
        // HD, Figure 5-6
        if (i == 0)
            return 32;
        int n = 1;
        if (i >>> 16 == 0) { n += 16; i <<= 16; }
        if (i >>> 24 == 0) { n +=  8; i <<=  8; }
        if (i >>> 28 == 0) { n +=  4; i <<=  4; }
        if (i >>> 30 == 0) { n +=  2; i <<=  2; }
        n -= i >>> 31;
        return n;
    }

    public static int lg2(int v){
        int r = 0; // r will be lg(v)

        while ((v >>= 1) > 0) // unroll for more speed...
        {
            r++;
        }

        return r;
    }
}
