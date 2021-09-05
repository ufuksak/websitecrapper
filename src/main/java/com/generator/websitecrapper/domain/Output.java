package com.generator.websitecrapper.domain;

public class Output {
    private int linkCount;
    private int imageCount;
    private String lastFetch;

    public Output(final int linkCount, int imageCount, String lastFetch) {
        this.linkCount = linkCount;
        this.imageCount = imageCount;
        this.lastFetch = lastFetch;
    }

    public String getLastFetch() {
        return lastFetch;
    }

    public int getImageCount() {
        return imageCount;
    }

    public int getLinkCount() {
        return linkCount;
    }

    public String toString() {
        return lastFetch + " - " + imageCount + " - " + linkCount;
    }
}
