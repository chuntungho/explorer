package com.chuntung.explorer.manager;

import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.brotli.dec.BrotliInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Singleton
public class HtmlResolver {
    private static final Logger logger = LoggerFactory.getLogger(HtmlResolver.class);
    private final BlockManager adBlocker;

    public HtmlResolver(BlockManager adBlocker) {
        this.adBlocker = adBlocker;
    }

    public byte[] resolve(byte[] htmlBytes, MutableHttpHeaders responseHeaders, URI remoteURI, URI proxyURI, ExplorerSetting setting)
            throws IOException {
        if (htmlBytes.length == 0) return htmlBytes;

        String charset = getCharset(responseHeaders, setting);
        String encoding = responseHeaders.get("Content-Encoding");

        InputStream in = new ByteArrayInputStream(htmlBytes);
        if (encoding != null && !encoding.isEmpty()) {
            in = decode(in, encoding);
        }

        Document document = Jsoup.parse(in, charset, "");

        if (setting.isRemoveScript()) {
            document.getElementsByTag("script").forEach(Node::remove);
        }

        Set<String> removeMediaTypes = setting.getRemoveMediaTypes();
        for (String attr : new String[]{"src", "data-src", "href", "data-href"}) {
            document.getElementsByAttribute(attr).forEach(e -> {
                String src = e.attr(attr);
                String ext = src.substring(src.lastIndexOf('.') + 1);
                if (removeMediaTypes.contains(ext)) {
                    e.remove();
                    return;
                }
                try {
                    e.attr(attr, UrlUtil.proxyUrl(src, proxyURI));
                } catch (IllegalStateException ex) {
                    logger.warn("Invalid src: {}", src);
                }
            });
        }

        document.getElementsByTag("form")
                .forEach(x -> x.attr("action", UrlUtil.proxyUrl(x.attr("action"), proxyURI)));

        adBlocker.postHandle(proxyURI, remoteURI, responseHeaders, document);

        byte[] resolved = document.toString().getBytes(charset);
        if (encoding != null && !encoding.isEmpty()) {
            resolved = encode(resolved, encoding);
            responseHeaders.set("Content-Encoding", "gzip");
        }

        responseHeaders.remove("Content-Length");
        return resolved;
    }

    private static String getCharset(MutableHttpHeaders headers, ExplorerSetting setting) {
        String contentType = headers.get("Content-Type");
        if (contentType != null && contentType.contains("charset=")) {
            int idx = contentType.indexOf("charset=") + 8;
            int end = contentType.indexOf(';', idx);
            String cs = end == -1 ? contentType.substring(idx).trim() : contentType.substring(idx, end).trim();
            if (Charset.isSupported(cs)) return cs;
        }
        return setting.getCharset() != null ? setting.getCharset() : "utf-8";
    }

    private static byte[] encode(byte[] bytes, String encoding) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out, true)) {
            gz.write(bytes);
        }
        return out.toByteArray();
    }

    private static InputStream decode(InputStream in, String encoding) throws IOException {
        if ("gzip".equals(encoding)) return new GZIPInputStream(in);
        if ("br".equals(encoding)) return new BrotliInputStream(in);
        return in;
    }
}
