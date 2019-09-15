package io.confluent.examples.streams;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.opentracing.propagation.TextMap;
import redis.clients.jedis.Jedis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JedisTextMapAdapter implements TextMap {

    private final Jedis jedis;
    private final String mapKey;

    public JedisTextMapAdapter(final Jedis jedis, final String mapKey) {
        this.jedis = jedis;
        this.mapKey = mapKey.toLowerCase();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        final String smap = jedis.get(mapKey);
        final Map<String,String> map;
        if (smap == null) {
            map = Collections.emptyMap();
        } else {
            map = new Gson().fromJson(smap, new TypeToken<Map<String, String>>() {}.getType());
        }
        return map.entrySet().iterator();
    }

    @Override
    public void put(final String key, final String value) {
        final String smap = jedis.get(mapKey);
        final Map<String, String> map;
        if (smap == null) {
            map = new HashMap<String,String>();
        } else {
            map = new Gson().fromJson(smap, new TypeToken<Map<String, String>>() {}.getType());
        }
        map.put(key, value);
        jedis.set(mapKey, new Gson().toJson(map));
    }

}
