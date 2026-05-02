package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;

@Singleton
public class DomainBlackListHandler implements BlockHandler {
    private final List<String> domainBlackList;

    public DomainBlackListHandler(ExplorerProperties explorerProperties) {
        domainBlackList = explorerProperties.getDomainBlackList();
    }

    @Override
    public boolean match(URI uri) {
        if (domainBlackList == null) return false;
        for (String domain : domainBlackList) {
            int idx = uri.getHost().lastIndexOf(domain);
            if (idx == 0 || (idx > 0 && uri.getHost().charAt(idx - 1) == '.')) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preHandle(URI uri, HttpRequest<?> request) {
        return false;
    }
}
