package com.chuntung.explorer.config;

import java.util.Map;

public class BlockContent {

    private String selector;
    private BlockAction action = BlockAction.CUSTOM;
    private Map<String, String> attributes;
    private String replace;
    private String prepend;
    private String append;

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public BlockAction getAction() {
        return action;
    }

    public void setAction(BlockAction action) {
        this.action = action;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getReplace() {
        return replace;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }

    public String getPrepend() {
        return prepend;
    }

    public void setPrepend(String prepend) {
        this.prepend = prepend;
    }

    public String getAppend() {
        return append;
    }

    public void setAppend(String append) {
        this.append = append;
    }
}
