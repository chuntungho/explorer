package com.chuntung.explorer.util;

import org.springframework.util.DigestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UrlUtil {
    // cache for long host that exceeds 63 chars limit which defined in RFC 1035
    // https://datatracker.ietf.org/doc/html/rfc1035
    public static Map<String, String> hostCache = new ConcurrentHashMap<>();

    /**
     * Split request url into remote url with query and proxy host only url.
     *
     * @param requestURL
     * @return
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public static List<URI> splitUris(URI requestURL, Map<String, String> hostMapping) {
        String concatHost = requestURL.getHost();
        int dotIdx = concatHost.indexOf('.');
        if (dotIdx == -1) {
            return Collections.emptyList();
        }

        String prefix = concatHost.substring(0, dotIdx);
        String remoteHost = decodeHost(prefix);
        int port = -1;
        int lastDot = remoteHost.lastIndexOf('.');
        // try parsing port
        if (lastDot > -1) {
            try {
                port = Integer.parseInt(remoteHost.substring(lastDot + 1));
                remoteHost = remoteHost.substring(0, lastDot);
            } catch (NumberFormatException e) {
                // NOOP
            }
        }

        // append .com automatically
        if (remoteHost.indexOf('.') == -1 && !"localhost".equals(remoteHost)) {
            remoteHost = hostMapping.getOrDefault(remoteHost, remoteHost + ".com");
        }

        // replace scheme, host and port for remote uri
        UriComponentsBuilder remoteUriBuilder = UriComponentsBuilder.fromUri(requestURL)
                .scheme("localhost".equals(remoteHost) ? "http" : "https").host(remoteHost).port(port);
        URI remoteURI;
        try {
            remoteURI = remoteUriBuilder.build(true).toUri();
        } catch (IllegalArgumentException e) {
            // fallback: remove query, such as `q=paq:[0,0,null,null,0]`
            remoteURI = remoteUriBuilder.query(null).build(true).toUri();
        }

        String proxyHost = concatHost.substring(dotIdx + 1);
        URI proxyHostURI = UriComponentsBuilder.fromUri(requestURL)
                .host(proxyHost).replacePath(null).query(null).build(true).toUri();

        return Arrays.asList(remoteURI, proxyHostURI);
    }

    private static String decodeHost(String prefix) {
        if (prefix.startsWith("md5-") && hostCache.containsKey(prefix)) {
            prefix = hostCache.get(prefix);
        }
        return prefix.replace("--", "~") // decode '--'
                .replace('-', '.') // replace '-' with '.'
                .replace('~', '-');
    }

    /**
     * Wrap remote url with proxy host.
     * <p>
     * e.g.
     * {@code https://www.domain.com:8443/search?w=xxx}
     * will be converted to
     * {@code https://www-damain-com_8443.localhost:2024/search?w=xxx}
     * </p>
     *
     * @param remoteUrl
     * @param proxyURI
     * @return
     */
    public static String proxyUrl(String remoteUrl, URI proxyURI) {
        String url = remoteUrl;
        if (remoteUrl.startsWith("//")) {
            remoteUrl = "https:" + remoteUrl;
        }

        if (remoteUrl.toLowerCase().startsWith("http://") || remoteUrl.toLowerCase().startsWith("https://")) {
            //  convert host and prepend to proxy host
            URI remoteURI = toURI(remoteUrl);
            String origHost = remoteURI.getHost();
            // may be redirected by remote server, avoid convert twice
            if (!origHost.endsWith(proxyURI.getHost())) {
                String encodedHost = encodeHost(origHost, remoteURI);
                String destHost = encodedHost + "." + proxyURI.getHost();
                url = UriComponentsBuilder.fromUriString(remoteUrl)
                        .scheme(proxyURI.getScheme()).host(destHost).port(proxyURI.getPort())
                        .build().toString();
            }
        }

        return url;
    }

    private static String encodeHost(String origHost, URI remoteURI) {
        String encoded = origHost.replace("-", "--").replace('.', '-')
                + (remoteURI.getPort() > 0 ? "-" + remoteURI.getPort() : "");
        if (encoded.length() > 63) {
            String md5 = DigestUtils.md5DigestAsHex(encoded.getBytes(StandardCharsets.UTF_8));
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
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(httpUrl);
        try {
            return builder.build().toUri();
        } catch (Exception e) {
            // fallback ignore query
            return builder.query(null).build().toUri();
        }
    }
}