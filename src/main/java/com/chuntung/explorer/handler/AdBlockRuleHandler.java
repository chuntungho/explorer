package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.BlockContent;
import com.chuntung.explorer.config.BlockRule;
import com.chuntung.explorer.config.ExplorerProperties;
import jakarta.annotation.PostConstruct;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Block by defined rules.
 */
@Component
public class AdBlockRuleHandler implements AdBlockHandler {
    private ExplorerProperties explorerProperties;

    private List<BlockRule> blockRequest = Collections.emptyList();
    private List<BlockRule> blockResponse = Collections.emptyList();

    public AdBlockRuleHandler(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    @PostConstruct
    void init() {
        blockRequest = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockPaths()))
                .collect(Collectors.toList());
        blockResponse = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockContents()) || Objects.nonNull(x.getBlockHeaders()))
                .collect(Collectors.toList());
    }

    public boolean match(URI uri) {
        return true;
    }

    public boolean preHandle(URI uri, RequestEntity<?> requestEntity) {
        boolean allowed = true;
        for (BlockRule rule : blockRequest) {
            if (uri.getHost().matches(rule.getHostPattern())) {
                for (String blockPath : rule.getBlockPaths()) {
                    if (uri.getPath().matches(blockPath)) {
                        allowed = false;
                    }
                }
            }
        }

        return allowed;
    }

    public void postHandle(URI proxyURI, URI uri, HttpHeaders responseHeaders, Document document) {
        blockResponse.stream()
                .filter(x -> uri.getHost().matches(x.getHostPattern()))
                .forEach(x -> {
                    if (x.getBlockHeaders() != null) {
                        x.getBlockHeaders().forEach((k, v) -> {
                            if (StringUtils.hasLength(v)) {
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
                if (StringUtils.hasLength(v)) {
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
            if (cfg.getPrepend() != null) {
                x.prepend(cfg.getPrepend());
            }
            if (cfg.getAppend() != null) {
                x.append(cfg.getAppend());
            }
        });
    }
}
