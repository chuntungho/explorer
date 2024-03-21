package com.chuntung.explorer.controller;

import com.chuntung.explorer.util.UrlUtil;
import org.brotli.dec.BrotliInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class HtmlResolver {
    private static final Logger logger = LoggerFactory.getLogger(HtmlResolver.class);
    public static final String REMOTE_URL_META = "<meta name=\"remote-url\" content=\"{remoteUrl}\">";
    public static final String INTERCEPTOR_SCRIPT = "<script src='{proxyHost}/dist/interceptor.js?v1'></script>";

    private AdBlocker adBlocker;

    public HtmlResolver(AdBlocker adBlocker) {
        this.adBlocker = adBlocker;
    }

    public ByteArrayResource resolve(Resource resource, HttpHeaders responseHeaders, URI remoteURI, URI proxyURI, ExplorerSetting setting)
            throws IOException {
        String charset = getCharset(responseHeaders, setting);
        String encoding = responseHeaders.getFirst("Content-Encoding");
        InputStream in = resource.getInputStream();
        if (StringUtils.hasLength(encoding)) {
            in = decode(in, encoding);
        }

        Document document = Jsoup.parse(in, charset, "");

        // remove script
        if (setting.isRemoveScript()) {
            document.getElementsByTag("script").forEach(Node::remove);
        }

        // intercept dom elements with 'src' attribute
        interceptDom(document, remoteURI, proxyURI);

        // element with url attribute
        Set<String> removeMediaTypes = setting.getRemoveMediaTypes();
        for (String attr : new String[]{"src", "data-src", "href", "data-href"}) {
            document.getElementsByAttribute(attr).forEach(e -> {
                String src = e.attr(attr);
                //  remove blocked media
                String ext = src.substring(src.lastIndexOf('.') + 1);
                if (removeMediaTypes.contains(ext)) {
                    e.remove();
                    return;
                }
                try {
                    e.attr(attr, UrlUtil.proxyUrl(src, proxyURI));
                } catch (IllegalStateException exception) {
                    logger.warn("Invalid src: {}", src);
                    // NOOP
                }
            });
        }

        // form action
        document.getElementsByTag("form")
                .forEach(x -> x.attr("action", UrlUtil.proxyUrl(x.attr("action"), proxyURI)));

        // post block ads
        adBlocker.postHandle(remoteURI, responseHeaders, document);

        byte[] resolved = document.toString().getBytes(charset);
        if (StringUtils.hasLength(encoding)) {
            resolved = encode(resolved, encoding);
            // overwrite to gzip
            responseHeaders.set("Content-Encoding", "gzip");
        }

        // content changed, overwrite content length by spring mvc
        responseHeaders.remove("Content-Length");

        return new ByteArrayResource(resolved);
    }

    private static String getCharset(HttpHeaders responseHeaders, ExplorerSetting setting) {
        MediaType contentType = responseHeaders.getContentType();
        // priority: content charset > setting charset > fallback charset
        String charset = "utf-8";
        if (contentType != null && contentType.getCharset() != null) {
            charset = contentType.getCharset().toString();
        } else if (setting.getCharset() != null) {
            charset = setting.getCharset();
        }
        return charset;
    }

    void interceptDom(Document document, URI remoteURI, URI proxyURI) {
        document.head().append(REMOTE_URL_META.replace("{remoteUrl}", remoteURI.toString()));
        document.head().prepend(INTERCEPTOR_SCRIPT.replace("{proxyHost}", proxyURI.toString()));
    }

    private static byte[] encode(byte[] resolved, String encoding) throws IOException {
        if (StringUtils.hasLength(encoding)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStream zipped = out;
            if ("gzip".equals(encoding)) {
                zipped = new GZIPOutputStream(out, true);
            } else if ("br".equals(encoding)) {
                zipped = new GZIPOutputStream(out, true);
            }
            zipped.write(resolved);
            zipped.flush();
            resolved = out.toByteArray();
        }
        return resolved;
    }

    private static InputStream decode(InputStream in, String encoding) throws IOException {
        if ("gzip".equals(encoding)) {
            in = new GZIPInputStream(in);
        } else if ("br".equals(encoding)) {
            in = new BrotliInputStream(in);
        }
        return in;
    }
}