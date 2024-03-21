package com.chuntung.explorer.config;

import java.util.List;
import java.util.Map;

public class BlockRule {
    private String hostPattern;
    private List<String> blockPaths;

    private Map<String, String> blockHeaders;
    private List<BlockContent> blockContents;

    public String getHostPattern() {
        return hostPattern;
    }

    public void setHostPattern(String hostPattern) {
        this.hostPattern = hostPattern;
    }

    public List<String> getBlockPaths() {
        return blockPaths;
    }

    public void setBlockPaths(List<String> blockPaths) {
        this.blockPaths = blockPaths;
    }

    public Map<String, String> getBlockHeaders() {
        return blockHeaders;
    }

    public void setBlockHeaders(Map<String, String> blockHeaders) {
        this.blockHeaders = blockHeaders;
    }

    public List<BlockContent> getBlockContents() {
        return blockContents;
    }

    public void setBlockContents(List<BlockContent> blockContents) {
        this.blockContents = blockContents;
    }
}
