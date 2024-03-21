package com.chuntung.explorer.controller;

import java.util.*;

public class ExplorerSetting {
    private static final String CHARSET = "cs";
    private static final String READABLE = "re";
    private static final String BLOCK_AD = "ba";
    private static final String REMOVE_SCRIPT = "rs";
    private static final String REMOVE_MEDIA_RESOURCES = "rmr";

    private static final Set<String> BLOCKED_MEDIA = Set.of("gif", "swf");

    // all keys should be registered here
    private static final List<String> settingKeys;

    static {
        settingKeys = Arrays.asList(CHARSET, READABLE, BLOCK_AD, REMOVE_SCRIPT, REMOVE_MEDIA_RESOURCES);

        // check duplicated keys, use assert to check coding error
        Set<String> keys = new HashSet<>(settingKeys);
        if (keys.size() != settingKeys.size()) {
            throw new AssertionError("Duplicated setting key found");
        }
    }

    private Map<String, String[]> settings = new HashMap<>();

    ExplorerSetting(Map<String, String[]> params) {
        if (params != null) {
            for (String settingKey : settingKeys) {
                if (params.containsKey(settingKey) && !params.get(settingKey)[0].isEmpty()) {
                    settings.put(settingKey, params.get(settingKey));
                }
            }
        }
    }

    ExplorerSetting(String query) {
        this(parseQueryString(query));
    }

    static Map<String, String[]> parseQueryString(String queryString) {
        Map<String, String[]> map = new HashMap<>();
        if (queryString == null) {
            return Collections.emptyMap();
        }
        StringTokenizer tokenizer = new StringTokenizer(queryString, "&");
        while (tokenizer.hasMoreTokens()) {
            String pair = tokenizer.nextToken();
            if (pair.contains("=")) {
                // not special char here, not to decode
                int idx = pair.indexOf('=');
                String key = pair.substring(0, idx);
                String val = pair.substring(idx + 1);
                String[] values;
                if (map.containsKey(key)) {
                    List<String> list = new ArrayList<>();
                    list.addAll(Arrays.asList(map.get(key)));
                    list.add(val);
                    values = new String[0];
                } else {
                    values = new String[]{val};
                }
                map.put(key, values);
            } else {
                map.put(pair, null);
            }
        }
        return map;
    }

    public String toQueryString(boolean useCharset) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String[]> entry : settings.entrySet()) {
            if (!useCharset && entry.getKey().equalsIgnoreCase(CHARSET)) {
                continue;
            }

            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }

            for (String val : values) {
                // omit value for boolean
                if (val == null || val.isEmpty()) {
                    sb.append('&').append(entry.getKey());
                } else {
                    sb.append('&').append(entry.getKey()).append("=").append(val);
                }
            }
        }

        return !sb.isEmpty() ? sb.substring(1) : null;
    }

    public String getCharset() {
        return settings.get(CHARSET) != null ? settings.get(CHARSET)[0] : null;
    }


    public boolean isReadable() {
        return settings.containsKey(READABLE);
    }

    public boolean isBlockAd() {
        return settings.containsKey(BLOCK_AD);
    }

    public boolean isRemoveScript() {
        String[] arr = settings.get(REMOVE_SCRIPT);
        return arr!=null && "true".equalsIgnoreCase(arr[0]);
    }

    public Set<String> getRemoveMediaTypes() {
        return settings.containsKey(REMOVE_MEDIA_RESOURCES) ? Set.copyOf(Arrays.asList(settings.get(REMOVE_MEDIA_RESOURCES))) : BLOCKED_MEDIA;
    }
}
