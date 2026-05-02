package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.BlockContent;
import com.chuntung.explorer.config.BlockRule;
import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
public class BlockRuleHandler implements BlockHandler {
    private final ExplorerProperties explorerProperties;
    private List<BlockRule> blockRequest = Collections.emptyList();
    private List<BlockRule> blockResponse = Collections.emptyList();

    public BlockRuleHandler(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
        init();
    }

    void init() {
        blockRequest = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockPaths()))
                .collect(Collectors.toList());
        blockResponse = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockContents()) || Objects.nonNull(x.getBlockHeaders()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean match(URI uri) {
        return true;
    }

    @Override
    public boolean preHandle(URI uri, HttpRequest<?> request) {
        for (BlockRule rule : blockRequest) {
            if (uri.getHost().matches(rule.getHostPattern())) {
                for (String blockPath : rule.getBlockPaths()) {
                    if (uri.getPath().matches(blockPath)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
        blockResponse.stream()
                .filter(x -> uri.getHost().matches(x.getHostPattern()))
                .forEach(x -> {
                    if (x.getBlockHeaders() != null) {
                        x.getBlockHeaders().forEach((k, v) -> {
                            if (v != null && !v.isEmpty()) {
                                responseHeaders.add(k, v);
                            } else {
                                responseHeaders.remove(k);
                            }
                        });
                    }
                    if (x.getBlockContents() != null) {
                        x.getBlockContents().forEach(y -> blockContent(uri, document, y));
                    }
                });
    }

    private void blockContent(URI uri, Document document, BlockContent cfg) {
        Elements elements = document.select(cfg.getSelector());
        switch (cfg.getAction()) {
            case REMOVE -> elements.remove();
            case HIDE -> elements.attr("style", "display:none");
            case CUSTOM -> customize(uri, elements, cfg);
        }
    }

    private void customize(URI uri, Elements elements, BlockContent cfg) {
        if (cfg.getAttributes() != null) {
            cfg.getAttributes().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    elements.attr(k, v);
                } else {
                    elements.removeAttr(k);
                }
            });
        }
        elements.forEach(x -> {
            if (cfg.getReplace() != null) {
                String replacement = cfg.getReplace();
                if (replacement.contains("{") && replacement.contains("}")) {
                    replacement = replacement.replace("{CURRENT_DATE}", LocalDate.now().toString());
                }
                x.html(replacement);
            }
            if (cfg.getPrepend() != null) x.prepend(cfg.getPrepend());
            if (cfg.getAppend() != null) x.append(cfg.getAppend());
        });
    }
}
