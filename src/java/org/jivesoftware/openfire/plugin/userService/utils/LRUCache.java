package org.jivesoftware.openfire.plugin.userService.utils;

/**
 * Created by dusanklinec on 08.07.15.
 */

import java.util.*;

public class LRUCache<K, V> implements Map<K, V> {
    private final int cacheSize;
    private final LinkedHashMap<K, V> map;

    public LRUCache(int cacheSize) {
        this.cacheSize = cacheSize;
        this.map = new LinkedHashMap<K, V>(cacheSize, 0.75f, true) {
            private static final long serialVersionUID = 1;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LRUCache.this.cacheSize;
            }
        };
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() >= cacheSize;
    }

    /**
     * Retrieves an entry from the cache.<br>
     * The retrieved entry becomes the MRU (most recently used) entry.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value associated to this key, or null if no value with this key exists in the cache.
     */
    public synchronized V get(Object key) {
        return map.get(key);
    }

    /**
     * Adds an entry to this cache.
     * The new entry becomes the MRU (most recently used) entry.
     * If an entry with the specified key already exists in the cache, it is replaced by the new entry.
     * If the cache is full, the LRU (least recently used) entry is removed from the cache.
     *
     * @param key   the key with which the specified value is to be associated.
     * @param value a value to be associated with the specified key.
     */
    public synchronized V put(K key, V value) {
        return map.put(key, value);
    }

    /**
     * Clears the cache.
     */
    public synchronized void clear() {
        map.clear();
    }

    /**
     * Returns the number of used entries in the cache.
     *
     * @return the number of entries currently in the cache.
     */
    public synchronized int usedEntries() {
        return map.size();
    }

    /**
     * Returns a <code>Collection</code> that contains a copy of all cache entries.
     *
     * @return a <code>Collection</code> with a copy of the cache content.
     */
    public synchronized Collection<Entry<K, V>> getAll() {
        return new ArrayList<Entry<K, V>>(map.entrySet());
    }

    @Override
    public synchronized int size() {
        return map.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public synchronized boolean containsKey(Object o) {
        return map.containsKey(o);
    }

    @Override
    public synchronized boolean containsValue(Object o) {
        return map.containsValue(o);
    }

    @Override
    public synchronized V remove(Object o) {
        return map.remove(o);
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> mapx) {
        map.putAll(mapx);
    }

    @Override
    public synchronized Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public synchronized Collection<V> values() {
        return map.values();
    }

    @Override
    public synchronized Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }
} // end class LRUCache
