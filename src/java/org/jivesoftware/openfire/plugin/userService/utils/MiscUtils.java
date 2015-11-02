package org.jivesoftware.openfire.plugin.userService.utils;

import org.json.JSONException;
import org.json.JSONObject;

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

    /**
     * Tries to extract json parameter as an integer.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Boolean tryGetAsBoolean(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);
        if (obj == null){
            return null;
        }

        if(!obj.equals(Boolean.FALSE) && (!(obj instanceof String) || !((String)obj).equalsIgnoreCase("false"))) {
            if(!obj.equals(Boolean.TRUE) && (!(obj instanceof String) || !((String)obj).equalsIgnoreCase("true"))) {
                final Integer asInt = tryGetAsInteger(json, key);
                if (asInt == null){
                    return null;
                }

                return asInt!=0;

            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Tries to extract json parameter as an integer.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Integer tryGetAsInteger(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);

        if (obj instanceof String){
            try {
                return Integer.parseInt((String) obj);
            } catch(Exception e){
                return null;
            }
        }

        try {
            return obj instanceof Number ? ((Number) obj).intValue() : (int) json.getDouble(key);
        } catch(Exception e){
            return null;
        }
    }

    /**
     * Tries to extract json parameter as a long.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Long tryGetAsLong(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);

        if (obj instanceof String){
            try {
                return Long.parseLong((String) obj);
            } catch(Exception e){
                return null;
            }
        }

        try {
            return obj instanceof Number ? ((Number) obj).longValue() : (long) json.getDouble(key);
        } catch(Exception e){
            return null;
        }
    }

    public static long getAsLong(JSONObject json, String key) throws JSONException {
        final Long toret = tryGetAsLong(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }

    public static int getAsInteger(JSONObject json, String key) throws JSONException {
        final Integer toret = tryGetAsInteger(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }

    public static boolean getAsBoolean(JSONObject json, String key) throws JSONException {
        final Boolean toret = tryGetAsBoolean(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }
}
