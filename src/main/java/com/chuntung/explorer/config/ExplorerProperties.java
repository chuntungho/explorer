package com.chuntung.explorer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("explorer")
public class ExplorerProperties {

    // support proxy
    private ProxyProperties proxy;

    // explorer url, use wildcard host if not specified
    // e.g. http://localhost:2024
    private String explorerUrl;

    // wildcard host, use host header if not specified
    // e.g. localhost:2024
    private String wildcardHost;

    private String interceptorUrl;

    private String assetsPath = "static";

    /**
     * the headers passed by reverse proxy
     */
    private Set<String> proxyHeaders;

    /**
     * the headers should not be sent to client
     */
    private Set<String> excludedHeaders;

    /**
     * Url in headers should be transformed to real url.
     */
    private Set<String> transformHeaders = Set.of("Origin", "Referer");

    /**
     * Note that host mapping may cause cors issue, it only supports for static request.
     */
    private Map<String, String> hostMappings;

    private List<BlockRule> blockRules;

    public ProxyProperties getProxy() {
        return proxy;
    }

    public void setProxy(ProxyProperties proxy) {
        this.proxy = proxy;
    }

    public String getExplorerUrl() {
        return explorerUrl;
    }

    public void setExplorerUrl(String explorerUrl) {
        this.explorerUrl = explorerUrl;
    }

    public String getWildcardHost() {
        return wildcardHost;
    }

    public void setWildcardHost(String wildcardHost) {
        this.wildcardHost = wildcardHost;
    }

    public String getInterceptorUrl() {
        return interceptorUrl;
    }

    public void setInterceptorUrl(String interceptorUrl) {
        this.interceptorUrl = interceptorUrl;
    }

    public String getAssetsPath() {
        return assetsPath;
    }

    public void setAssetsPath(String assetsPath) {
        this.assetsPath = assetsPath;
    }

    public Set<String> getProxyHeaders() {
        return proxyHeaders;
    }

    public void setProxyHeaders(Set<String> proxyHeaders) {
        this.proxyHeaders = proxyHeaders;
    }

    public Set<String> getExcludedHeaders() {
        return excludedHeaders;
    }

    public void setExcludedHeaders(Set<String> excludedHeaders) {
        this.excludedHeaders = excludedHeaders;
    }

    public Set<String> getTransformHeaders() {
        return transformHeaders;
    }

    public void setTransformHeaders(Set<String> transformHeaders) {
        this.transformHeaders = transformHeaders;
    }

    public Map<String, String> getHostMappings() {
        return hostMappings;
    }

    public void setHostMappings(Map<String, String> hostMappings) {
        this.hostMappings = hostMappings;
    }

    public List<BlockRule> getBlockRules() {
        return blockRules;
    }

    public void setBlockRules(List<BlockRule> blockRules) {
        this.blockRules = blockRules;
    }
}
