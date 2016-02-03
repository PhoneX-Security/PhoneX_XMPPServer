package org.jivesoftware.openfire.plugin.userService.strings;

import java.util.Locale;

/**
 * From commons.
 * Created by dusanklinec on 03.02.16.
 */
public class LocaleUtils {

    public static Locale toLocale(String str) {
        if(str == null) {
            return null;
        } else {
            int len = str.length();
            if(len != 2 && len != 5 && len < 7) {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            } else {
                char ch0 = str.charAt(0);
                char ch1 = str.charAt(1);
                if(ch0 >= 97 && ch0 <= 122 && ch1 >= 97 && ch1 <= 122) {
                    if(len == 2) {
                        return new Locale(str, "");
                    } else if(str.charAt(2) != 95) {
                        throw new IllegalArgumentException("Invalid locale format: " + str);
                    } else {
                        char ch3 = str.charAt(3);
                        if(ch3 == 95) {
                            return new Locale(str.substring(0, 2), "", str.substring(4));
                        } else {
                            char ch4 = str.charAt(4);
                            if(ch3 >= 65 && ch3 <= 90 && ch4 >= 65 && ch4 <= 90) {
                                if(len == 5) {
                                    return new Locale(str.substring(0, 2), str.substring(3, 5));
                                } else if(str.charAt(5) != 95) {
                                    throw new IllegalArgumentException("Invalid locale format: " + str);
                                } else {
                                    return new Locale(str.substring(0, 2), str.substring(3, 5), str.substring(6));
                                }
                            } else {
                                throw new IllegalArgumentException("Invalid locale format: " + str);
                            }
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Invalid locale format: " + str);
                }
            }
        }
    }
}
