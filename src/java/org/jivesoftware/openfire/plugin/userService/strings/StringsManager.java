package org.jivesoftware.openfire.plugin.userService.strings;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.plugin.userService.db.DbEntityManager;
import org.jivesoftware.openfire.plugin.userService.db.DbStrings;
import org.jivesoftware.openfire.plugin.userService.db.PluralFormsEnum;
import org.jivesoftware.openfire.plugin.userService.utils.LRUCache;
import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Loading localized strings from database.
 *
 * Created by dusanklinec on 05.01.16.
 */
public class StringsManager {
    private static final Logger log = LoggerFactory.getLogger(StringsManager.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final long STRING_CACHE_TTL_MILLI = 1000*10;
    private static final String SQL_FETCH_STRINGS = String.format(
            "SELECT * FROM `%s` WHERE %s=? " +
                    "AND %s=? " +
                    "AND %s IN (%%s)",
            DbStrings.TABLE_NAME,
            DbStrings.FIELD_KEY,
            DbStrings.FIELD_PLURAL_TYPE,
            DbStrings.FIELD_LOCALE);

    /**
     * String cache holding all recently loaded records.
     */
    private final LRUCache<StringCacheKey, StringCacheValue> stringCache = new LRUCache<StringCacheKey, StringCacheValue>(1024);

    /**
     * Loads string translation from DB, using local cache for better performance udner heavy load.
     * Cache entry has TTL 10 seconds.
     * @param key
     * @param locales
     * @param pluralForm
     * @return
     */
    public DbStrings loadStringCached(String key, List<Locale> locales, PluralFormsEnum pluralForm){
        final StringCacheKey cKey = new StringCacheKey(key, locales, pluralForm);
        final StringCacheValue cVal = stringCache.get(cKey);
        final long now = System.currentTimeMillis();
        if (cVal != null && (now - cVal.getDateLoaded()) < STRING_CACHE_TTL_MILLI){
            return cVal.getString();
        }

        final DbStrings dbString = loadString(key, locales, pluralForm);
        stringCache.put(cKey, new StringCacheValue(dbString, now));

        return dbString;
    }

    public DbStrings loadStringCached(String key, List<Locale> locales){
        return loadStringCached(key, locales, PluralFormsEnum.NONE);
    }

    /**
     * Loads string with given locale and plural form from the database.
     * If given string is not found in a given locale according to the prefered order, default one is loaded.
     *
     * @param key
     * @param locales
     * @param pluralForm
     * @return
     */
    public DbStrings loadString(String key, List<Locale> locales, PluralFormsEnum pluralForm){
        final List<DbStrings> strings = new ArrayList<DbStrings>();
        final List<Locale> realLocales = fixupLocales(locales, true);
        final Set<String> localeStringSet = new HashSet<String>();
        final List<String> localeString = new ArrayList<String>(realLocales.size());
        for (Locale l : realLocales){
            localeStringSet.add(l.getLanguage());
            localeString.add(l.getLanguage());
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        final int cnt = localeStringSet.size();
        final String query = String.format(SQL_FETCH_STRINGS, DbEntityManager.genPlaceholders(cnt));
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(query);
            int ctr = 0;

            pstmt.setString(++ctr, key);
            pstmt.setString(++ctr, pluralForm.toString());
            for (String lcl : localeStringSet) {
                pstmt.setString(++ctr, lcl);
            }

            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    final DbStrings curStr = DbStrings.fromRes(rs);
                    strings.add(curStr);

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        catch (SQLException e) {
            log.error(String.format("Exception: %s, cnt: %s, locales: %s, query: %s",
                    e.getMessage(), cnt, localeStringSet, query), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (strings.isEmpty()){
            return null;
        }

        // Pick in the given order.
        final Map<String, DbStrings> stringMap = new HashMap<String, DbStrings>();
        for(DbStrings str : strings){
            stringMap.put(str.getLocale(), str);
        }

        for(String strLoc : localeString){
            final DbStrings tmpStr = stringMap.get(strLoc);
            if (tmpStr != null){
                return tmpStr;
            }
        }

        return null;
    }

    /**
     * Loads string with given locale and plural form from the database.
     * If given string is not found, default one is loaded.
     * @param key
     * @param locale
     * @param pluralForm
     * @return
     */
    public DbStrings loadString(String key, Locale locale, PluralFormsEnum pluralForm){
        return loadString(key, Collections.singletonList(locale), pluralForm);
    }

    public DbStrings loadString(String key, List<Locale> locales){
        return loadString(key, locales, PluralFormsEnum.NONE);
    }

    public DbStrings loadString(String key, Locale locale){
        return loadString(key, locale, PluralFormsEnum.NONE);
    }

    public DbStrings loadString(String key, PluralFormsEnum pluralForm){
        return loadString(key, DEFAULT_LOCALE, pluralForm);
    }

    public DbStrings loadString(String key){
        return loadString(key, DEFAULT_LOCALE, PluralFormsEnum.NONE);
    }

    /**
     * Reads list of locales, separated by comma and creates a list of locale objects from it.
     * @param localeList
     * @return
     */
    public static List<Locale> extractLocales(String localeList){
        if (MiscUtils.isEmpty(localeList)){
            return Collections.emptyList();
        }

        final List<Locale> locales = new LinkedList<Locale>();
        final String[] parts = localeList.split(",");
        for(String part : parts){
            if (MiscUtils.isEmpty(part)){
                continue;
            }

            final String tPart = part.trim();
            if (tPart.isEmpty()){
                continue;
            }

            try {
                locales.add(LocaleUtils.toLocale(tPart.replace("-", "_")));
            } catch(Exception e){
                log.error("Exception converting string to locale", e);
            }
        }

        return locales;
    }

    /**
     * Converts array of strings to array of locales.
     * @param localeList
     * @return
     */
    public static List<Locale> getLocales(Collection<String> localeList){
        if (MiscUtils.isEmpty(localeList)){
            return Collections.emptyList();
        }

        final List<Locale> locales = new LinkedList<Locale>();
        for(String part : localeList){
            if (MiscUtils.isEmpty(part)){
                continue;
            }

            final String tPart = part.trim();
            if (tPart.isEmpty()){
                continue;
            }

            try {
                locales.add(LocaleUtils.toLocale(tPart.replace("-", "_")));
            } catch(Exception e){
                log.error("Exception converting string to locale", e);
            }
        }

        return locales;
    }

    /**
     * Removes duplicates from locale list, adds default to the end if not present already.
     * @param locales
     * @param addDefault
     * @return
     */
    public static List<Locale> fixupLocales(List<Locale> locales, boolean addDefault){
        if (locales == null){
            locales = Collections.emptyList();
        }

        final Set<Locale> localeSet = new HashSet<Locale>(locales);
        if (localeSet.size() == locales.size()){
            if (!addDefault){
                return locales;
            }

            if (localeSet.contains(DEFAULT_LOCALE)){
                return locales;
            }

            final ArrayList<Locale> srcLocale = new ArrayList<Locale>(locales);
            srcLocale.add(DEFAULT_LOCALE);
            return srcLocale;
        }

        // Make a copy.
        final ArrayList<Locale> srcLocale = new ArrayList<Locale>(locales);
        final ArrayList<Locale> dstLocale = new ArrayList<Locale>(localeSet.size()+1);
        final Set<Locale> dstSet = new HashSet<Locale>();

        // Size differs, so prunning is required anyway. If default locale should be added, do it now.
        if (addDefault) {
            srcLocale.add(DEFAULT_LOCALE);
            localeSet.add(DEFAULT_LOCALE);
        }

        for(Locale curLcl : srcLocale){
            if (dstSet.contains(curLcl)){
                continue;
            }

            dstLocale.add(curLcl);
            dstSet.add(curLcl);
        }

        return dstLocale;
    }

    public void init() {

    }

    public void deinit() {

    }

    /**
     * Key to the string cache.
     */
    private static class StringCacheKey implements Serializable {
        private String key;
        private String localeList;
        private PluralFormsEnum pluralForm = PluralFormsEnum.NONE;

        public StringCacheKey() {
        }

        public StringCacheKey(String key, List<Locale> locale, PluralFormsEnum pluralForm) {
            this.key = key;
            setLocale(locale);
            this.pluralForm = pluralForm;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StringCacheKey that = (StringCacheKey) o;

            if (key != null ? !key.equals(that.key) : that.key != null) return false;
            if (localeList != null ? !localeList.equals(that.localeList) : that.localeList != null) return false;
            return pluralForm == that.pluralForm;

        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (localeList != null ? localeList.hashCode() : 0);
            result = 31 * result + (pluralForm != null ? pluralForm.hashCode() : 0);
            return result;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public void setLocale(List<Locale> locale) {
            if (locale == null || locale.isEmpty()){
                this.localeList = "";

            } else {
                final StringBuilder sb = new StringBuilder();
                for(Locale lcl : locale){
                    sb.append(lcl.getLanguage()).append(";");
                }

                this.localeList = sb.toString();
            }
        }

        public PluralFormsEnum getPluralForm() {
            return pluralForm;
        }

        public void setPluralForm(PluralFormsEnum pluralForm) {
            this.pluralForm = pluralForm;
        }
    }

    /**
     * Value to the string cache.
     */
    private static class StringCacheValue implements Serializable {
        private DbStrings string;
        private long dateLoaded;

        public StringCacheValue() {
        }

        public StringCacheValue(DbStrings string, long dateLoaded) {
            this.string = string;
            this.dateLoaded = dateLoaded;
        }

        public DbStrings getString() {
            return string;
        }

        public void setString(DbStrings string) {
            this.string = string;
        }

        public long getDateLoaded() {
            return dateLoaded;
        }

        public void setDateLoaded(long dateLoaded) {
            this.dateLoaded = dateLoaded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StringCacheValue that = (StringCacheValue) o;

            if (dateLoaded != that.dateLoaded) return false;
            return !(string != null ? !string.equals(that.string) : that.string != null);

        }

        @Override
        public int hashCode() {
            int result = string != null ? string.hashCode() : 0;
            result = 31 * result + (int) (dateLoaded ^ (dateLoaded >>> 32));
            return result;
        }
    }

}
