package com.generator.websitecrapper;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class IconGrabber {
    private static IconGrabber INSTANCE = new IconGrabber();
    private OkHttpClient client = new OkHttpClient();

    private final String[] htmlIconCssQueries = {
            "meta[property=\"og:image\"]",
            "meta[name=\"msapplication-TileImage\"]",
            "link[rel=\"icon\"]",
            "link[rel=\"shortcut icon\"]",
            "link[rel=\"apple-touch-icon\"]",
            "link[rel=\"apple-touch-icon-precomposed\"]",
            "img[alt=\"Logo\"]",
            "img[alt=\"logo\"]"
    };

    private final String[] hardcodedIconPaths = {
            "/favicon.ico",
            "/apple-touch-icon.png",
            "/apple-touch-icon-precomposed.png",
    };

    private IconGrabber() {
    }

    static IconGrabber getInstance() {
        return INSTANCE;
    }

    String getFaviconUrl(Document document) {
        List<String> potentialIcons = getPotentialFaviconUrls(document);
        return pickBestIconUrl(potentialIcons);
    }

    private List<String> getPotentialFaviconUrls(Document document) {
        List<String> iconUrls = new ArrayList<>();
        HttpUrl base = HttpUrl.parse(document.baseUri());

        for (String cssQuery : htmlIconCssQueries) {
            for (Element e : document.select(cssQuery)) {
                if (e.hasAttr("href")) {
                    iconUrls.add(e.attr("href"));
                }

                if (e.hasAttr("content")) {
                    iconUrls.add(e.attr("content"));
                }

                if (e.hasAttr("src")) {
                    iconUrls.add(e.attr("src"));
                }
            }
        }

        for (String path : hardcodedIconPaths) {
            HttpUrl url = HttpUrl.parse("http://" + HttpUrl.parse(document.baseUri()).host() + path);
            iconUrls.add(url.toString());
        }

        for (ListIterator<String> i = iconUrls.listIterator(); i.hasNext(); ) {
            HttpUrl httpUrl = base.resolve(i.next());
            if (httpUrl != null) {
                i.set(httpUrl.toString());
            } else {
                i.remove();
            }
        }

        return iconUrls;
    }

    private String pickBestIconUrl(List<String> urls) {
        String bestIconUrl = null;

        for (String url : urls) {
            bestIconUrl = getBitmapDimensFromUrl(url);
        }

        return bestIconUrl;
    }

    private String getBitmapDimensFromUrl(String url) {

        Request request = new Request.Builder()
                .url(url)
                .build();
        String options = "";
        try {
            Response response = client.newCall(request).execute();
            InputStream is = response.body().byteStream();

            response.body().close();
            is.close();

            return options;

        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
