package com.generator.websitecrapper.domain;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;
import java.io.IOException;

public class Options {
    private boolean makeLinksAbsolute = true;

    private OkHttpClient client = new OkHttpClient();
    private boolean saveImages = true;
    private boolean saveFrames = true;
    private boolean saveOther = true;
    private boolean saveScripts = true;
    private boolean saveVideo = false;

    private String userAgent = " ";

    public void setCache(File cacheDirectory, long maxCacheSize) {
        Cache cache = (new Cache(cacheDirectory, maxCacheSize));
        client.setCache(cache);
    }

    public void clearCache() throws IOException {
        client.getCache().evictAll();
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean makeLinksAbsolute() {
        return makeLinksAbsolute;
    }

    public void makeLinksAbsolute(final boolean makeLinksAbsolute) {
        this.makeLinksAbsolute = makeLinksAbsolute;
    }

    public boolean saveImages() {
        return saveImages;
    }

    public void saveImages(final boolean saveImages) {
        this.saveImages = saveImages;
    }

    public boolean saveFrames() {
        return saveFrames;
    }

    public void saveFrames(final boolean saveFrames) {
        this.saveFrames = saveFrames;
    }

    public boolean saveScripts() {
        return saveScripts;
    }

    public void saveScripts(final boolean saveScripts) {
        this.saveScripts = saveScripts;
    }

    public boolean saveOther() {
        return saveOther;
    }

    public void saveOther(final boolean saveOther) {
        this.saveOther = saveOther;
    }

    public boolean saveVideo() {
        return saveVideo;
    }

    public void saveVideo(final boolean saveVideo) {
        this.saveVideo = saveVideo;
    }
}