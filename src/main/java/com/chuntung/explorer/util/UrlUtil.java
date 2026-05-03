package com.chuntung.explorer.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UrlUtil {
    // cache for long host that exceeds 63 chars limit which defined in RFC 1035
    // https://datatracker.ietf.org/doc/html/rfc1035#section-2.3.4
    public static Map<String, String> hostCache = new ConcurrentHashMap<>();

    /**
     * Split request url into remote url with query and proxy host only url.
     */
    public static List<URI> splitUris(URI requestURL, Map<String, String> hostMapping) {
        String concatHost = requestURL.getHost();
        int dotIdx = concatHost.indexOf('.');
        if (dotIdx == -1) {
            return Collections.emptyList();
        }

        String prefix = concatHost.substring(0, dotIdx);
        String h0st = parseQueryParam(requestURL.getRawQuery(), "h0st");
        String remoteHost = h0st != null ? h0st : decodeHost(prefix);
        int port = -1;
        int lastDot = remoteHost.lastIndexOf('.');
        if (lastDot > -1) {
            try {
                port = Integer.parseInt(remoteHost.substring(lastDot + 1));
                remoteHost = remoteHost.substring(0, lastDot);
            } catch (NumberFormatException e) {
                // NOOP
            }
        }

        if (remoteHost.indexOf('.') == -1 && !"localhost".equals(remoteHost)) {
            remoteHost = hostMapping != null
                    ? hostMapping.getOrDefault(remoteHost, remoteHost + ".com")
                    : remoteHost + ".com";
        }

        String scheme = "localhost".equals(remoteHost) ? "http" : "https";
        String rawPath = requestURL.getRawPath();
        String rawQuery = requestURL.getRawQuery();
        StringBuilder remoteUriSb = new StringBuilder();
        remoteUriSb.append(scheme).append("://").append(remoteHost);
        if (port > 0) remoteUriSb.append(':').append(port);
        if (rawPath != null && !rawPath.isEmpty()) remoteUriSb.append(rawPath);
        if (rawQuery != null) remoteUriSb.append('?').append(rawQuery);

        URI remoteURI;
        try {
            remoteURI = URI.create(remoteUriSb.toString());
        } catch (IllegalArgumentException e) {
            // fallback: strip query
            int q = remoteUriSb.indexOf("?");
            remoteURI = URI.create(q >= 0 ? remoteUriSb.substring(0, q) : remoteUriSb.toString());
        }

        String proxyHost = concatHost.substring(dotIdx + 1);
        StringBuilder proxyHostSb = new StringBuilder();
        proxyHostSb.append(requestURL.getScheme()).append("://").append(proxyHost);
        if (requestURL.getPort() > 0) proxyHostSb.append(':').append(requestURL.getPort());
        URI proxyHostURI = URI.create(proxyHostSb.toString());

        return Arrays.asList(remoteURI, proxyHostURI);
    }

    private static String decodeHost(String prefix) {
        if (prefix.startsWith("md5-") && hostCache.containsKey(prefix)) {
            prefix = hostCache.get(prefix);
        }
        return prefix.replace("--", "~")
                .replace('-', '.')
                .replace('~', '-');
    }

    /**
     * Wrap remote url with proxy host.
     * <p>
     * e.g.
     * {@code https://www.domain.com:8443/search?w=xxx}
     * will be converted to
     * {@code https://www-domain-com-8443.localhost:2024/search?w=xxx}
     * </p>
     */
    public static String proxyUrl(String remoteUrl, URI proxyURI) {
        String url = remoteUrl;
        if (remoteUrl.startsWith("//")) {
            remoteUrl = "https:" + remoteUrl;
        }

        if (remoteUrl.toLowerCase().startsWith("http://") || remoteUrl.toLowerCase().startsWith("https://")) {
            URI remoteURI = toURI(remoteUrl);
            String origHost = remoteURI.getHost();
            // may be redirected by remote server, avoid convert twice
            if (!origHost.endsWith(proxyURI.getHost())) {
                String encodedHost = encodeHost(origHost, remoteURI);
                String destHost = encodedHost + "." + proxyURI.getHost();

                String rawPath = remoteURI.getRawPath();
                String rawQuery = remoteURI.getRawQuery();
                if (encodedHost.startsWith("md5-")) {
                    String h0stParam = "h0st=" + origHost;
                    rawQuery = rawQuery == null ? h0stParam : rawQuery + "&" + h0stParam;
                }

                StringBuilder sb = new StringBuilder();
                sb.append(proxyURI.getScheme()).append("://").append(destHost);
                if (proxyURI.getPort() > 0) sb.append(':').append(proxyURI.getPort());
                if (rawPath != null && !rawPath.isEmpty()) sb.append(rawPath);
                if (rawQuery != null) sb.append('?').append(rawQuery);
                url = sb.toString();
            }
        }

        return url;
    }

    /** Extract the first value of {@code name} from a raw (already-decoded) query string. */
    private static String parseQueryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        String prefix = name + "=";
        for (String param : rawQuery.split("&")) {
            if (param.startsWith(prefix)) {
                return param.substring(prefix.length());
            }
        }
        return null;
    }

    private static String encodeHost(String origHost, URI remoteURI) {
        String encoded = origHost.replace("-", "--").replace('.', '-')
                + (remoteURI.getPort() > 0 ? "-" + remoteURI.getPort() : "");
        if (encoded.length() > 63) {
            String md5 = md5Hex(encoded.getBytes(StandardCharsets.UTF_8));
            String md5Host = "md5-" + md5;
            hostCache.put(md5Host, encoded);
            return md5Host;
        }
        return encoded;
    }

    public static URI toURI(String httpUrl) {
        if (httpUrl.startsWith("//")) {
            httpUrl = "https:" + httpUrl;
        }
        try {
            return URI.create(httpUrl);
        } catch (IllegalArgumentException e) {
            // fallback: strip query
            int q = httpUrl.indexOf('?');
            return URI.create(q >= 0 ? httpUrl.substring(0, q) : httpUrl);
        }
    }

    private static String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
