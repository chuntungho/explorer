package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Block request which domain exists in black list.
 */
@Component
public class DomainBlackListHandler implements BlockHandler {
    private List<String> domainBlackList;

    public DomainBlackListHandler(ExplorerProperties explorerProperties) {
        domainBlackList = explorerProperties.getDomainBlackList();
    }

    @Override
    public boolean match(URI uri) {
        if (domainBlackList == null) {
            return false;
        }
        for (String domain : domainBlackList) {
            int idx = uri.getHost().lastIndexOf(domain);
            if (idx == 0 || (idx > 0 && uri.getHost().charAt(idx - 1) == '.')) {
                return true;
            }
        }
        return false;
    }

    public boolean preHandle(URI uri, RequestEntity<?> requestEntity) {
        return false;
    }
}
