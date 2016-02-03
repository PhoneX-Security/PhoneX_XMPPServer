package org.jivesoftware.openfire.plugin.userService.db;

import org.jivesoftware.openfire.plugin.userService.platformPush.TokenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.Date;

/**
 * Dataabse storage for translated strings.
 * Created by dusanklinec on 03.02.16.
 */
public class DbStrings implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(DbStrings.class);

    public static final String TABLE_NAME = "ofPhxStrings";
    public static final String FIELD_ID = "id";
    public static final String FIELD_DATE_CREATED = "ofDateCreated";
    public static final String FIELD_KEY = "ofStringKey";
    public static final String FIELD_LOCALE = "ofStringLocale";
    public static final String FIELD_PLURAL_TYPE = "ofPluralType";
    public static final String FIELD_TRANSLATABLE = "ofTranslatable";
    public static final String FIELD_VALUE = "ofStringValue";

    private Long id;
    private Date dateCreated;
    private String key;
    private String locale;
    private String plural;
    private Integer translatable;
    private String value;

    /**
     * Creates a token object from the result set.
     * @param rs
     * @return
     */
    public static DbStrings fromRes(ResultSet rs){
        if (rs == null){
            return null;
        }

        DbStrings str = null;
        try {
            str = new DbStrings();
            str.setId(rs.getLong(FIELD_ID));

            final Long ts = DbEntityManager.getTimeStamp(rs, FIELD_DATE_CREATED);
            str.setDateCreated(ts == null ? null : new Date(ts));

            str.setKey(rs.getString(FIELD_KEY));
            str.setLocale(rs.getString(FIELD_LOCALE));
            str.setPlural(rs.getString(FIELD_PLURAL_TYPE));
            str.setTranslatable(rs.getInt(FIELD_TRANSLATABLE));
            str.setValue(rs.getString(FIELD_VALUE));

        } catch(Exception e){
            log.error("Exception in loading DbPushMessage from RS", e);
            str = null;
        }

        return str;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getPlural() {
        return plural;
    }

    public void setPlural(String plural) {
        this.plural = plural;
    }

    public Integer getTranslatable() {
        return translatable;
    }

    public void setTranslatable(Integer translatable) {
        this.translatable = translatable;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DbStrings dbStrings = (DbStrings) o;

        if (id != null ? !id.equals(dbStrings.id) : dbStrings.id != null) return false;
        if (dateCreated != null ? !dateCreated.equals(dbStrings.dateCreated) : dbStrings.dateCreated != null)
            return false;
        if (key != null ? !key.equals(dbStrings.key) : dbStrings.key != null) return false;
        if (locale != null ? !locale.equals(dbStrings.locale) : dbStrings.locale != null) return false;
        if (plural != null ? !plural.equals(dbStrings.plural) : dbStrings.plural != null) return false;
        if (translatable != null ? !translatable.equals(dbStrings.translatable) : dbStrings.translatable != null)
            return false;
        return !(value != null ? !value.equals(dbStrings.value) : dbStrings.value != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (dateCreated != null ? dateCreated.hashCode() : 0);
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (locale != null ? locale.hashCode() : 0);
        result = 31 * result + (plural != null ? plural.hashCode() : 0);
        result = 31 * result + (translatable != null ? translatable.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DbStrings{" +
                "id=" + id +
                ", dateCreated=" + dateCreated +
                ", key='" + key + '\'' +
                ", locale='" + locale + '\'' +
                ", plural='" + plural + '\'' +
                ", translatable=" + translatable +
                ", value='" + value + '\'' +
                '}';
    }
}
