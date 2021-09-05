package com.generator.websitecrapper;

import com.generator.websitecrapper.domain.EventCallback;
import com.generator.websitecrapper.domain.Options;
import com.generator.websitecrapper.domain.Output;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WebSaveGenerator {
    private EventCallback eventCallback;

    private OkHttpClient client = new OkHttpClient();
    private final String HTTP_REQUEST_TAG = "TAG";

    private boolean isCancelled = false;
    private Options options = new Options();

    private List<String> filesToGrab = new ArrayList<>();
    private List<String> framesToGrab = new ArrayList<>();
    private List<String> cssToGrab = new ArrayList<>();

    private String title = "";
    private String pageIconUrl = "";

    private String indexFileName = "index.html";

    private final Pattern fileNameReplacementPattern = Pattern.compile("[^a-zA-Z0-9-_\\.]");

    private Options getOptions() {
        return this.options;
    }

    public WebSaveGenerator(EventCallback callback) {
        this.eventCallback = callback;

        client.setConnectTimeout(20, TimeUnit.SECONDS);
        client.setReadTimeout(20, TimeUnit.SECONDS);
        client.setWriteTimeout(20, TimeUnit.SECONDS);

        client.setFollowRedirects(true);
        client.setFollowSslRedirects(true);
    }

    public Output getStatistics(final String url, final String outputDir) throws IOException {
        String dynamicFileName = url.replace("http://", "").replace("https://", "");
        String dynamicOutputPath = outputDir + dynamicFileName + ".html";

        DateFormat df = new SimpleDateFormat("EEE MMM dd yyyy HH:mm z");
        File outputFile = new File(dynamicOutputPath, dynamicFileName);
        Document doc = Jsoup.parse(outputFile, "UTF-8", "http://example.com/");
        BasicFileAttributes attr = Files.readAttributes(outputFile.toPath(), BasicFileAttributes.class);
        String dateCreated = df.format(attr.creationTime().toMillis());

        Elements links = doc.select("a[href]"); // a with href
        Elements images = doc.select("img[src~=(?i)\\.(png|jpe?g|gif|svg|webp)]");
        System.out.println("site: " + dynamicFileName);
        System.out.println("num_links: " + links.size());
        System.out.println("images: " + images.size());
        System.out.println("last_fetch: " + dateCreated);
        return new Output(links.size(), images.size(), dateCreated);
    }

    public void createZip(final String url, final String outputDir) throws IOException {
        String dynamicFileName = url.replace("http://", "").replace("https://", "");
        String dynamicOutputPath = outputDir + dynamicFileName + ".html";

        Path p = Files.createFile(Paths.get(dynamicOutputPath + ".zip"));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            Path pp = Paths.get(dynamicOutputPath);
            Files.walk(pp)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    });
        }
    }

    public List<Boolean> getPage(String outputDirPath, List<String> indexFilenameList) {
        List<Boolean> pageDownloadList = new ArrayList<>();
        for (String indexFileName : indexFilenameList) {
            String dynamicFileName = indexFileName.replace("http://", "").replace("https://", "");
            String dynamicOutputPath = outputDirPath + dynamicFileName + ".html";
            pageDownloadList.add(getPage(indexFileName, dynamicOutputPath, dynamicFileName));
        }
        return pageDownloadList;
    }

    private boolean getPage(String url, String outputDirPath, String indexFilename) {

        this.indexFileName = indexFilename;

        File outputDir = new File(outputDirPath);

        if (!outputDir.exists() && outputDir.mkdirs() == false) {
            eventCallback.onFatalError(new IOException("File " + outputDirPath + "could not be created"), url);
            return false;
        }

        //download main html and parse -- isExtra parameter should be false
        boolean success = downloadHtmlAndParseLinks(url, outputDirPath, false);
        if (isCancelled || !success) {
            return false;
        }

        //download and parse html frames - use iterator because our list may be modified as frames can contain other frames
        for (Iterator<String> i = framesToGrab.iterator(); i.hasNext(); ) {
            downloadHtmlAndParseLinks(i.next(), outputDirPath, true);
            if (isCancelled) return true;
        }

        //download and parse css files
        for (Iterator<String> i = cssToGrab.iterator(); i.hasNext(); ) {
            if (isCancelled) return true;
            downloadCssAndParse(i.next(), outputDirPath);
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS, new BlockingDownloadTaskQueue<Runnable>());

        for (Iterator<String> i = filesToGrab.iterator(); i.hasNext(); ) {
            if (isCancelled) {
                eventCallback.onProgressMessage("Cancelling...");
                shutdownExecutor(pool, 10, TimeUnit.SECONDS);
                return success;
            }

            String urlToDownload = i.next();

            eventCallback.onProgressMessage("Saving file: " + getFileName(urlToDownload));
            eventCallback.onProgressChanged(filesToGrab.indexOf(urlToDownload), filesToGrab.size(), false);

            pool.submit(new DownloadTask(urlToDownload, outputDir));
        }
        pool.submit(new DownloadTask(pageIconUrl, outputDir, "saveForOffline_icon.png"));

        eventCallback.onProgressMessage("Finishing file downloads...");
        shutdownExecutor(pool, 60, TimeUnit.SECONDS);

        return success;
    }

    private boolean downloadHtmlAndParseLinks(final String url, final String outputDir, final boolean isExtra) {
        String filename;

        if (isExtra) {
            filename = getFileName(url);
        } else {
            filename = indexFileName;
        }

        String baseUrl = url;
        if (url.endsWith("/")) {
            baseUrl = url + filename;
        }
        try {
            eventCallback.onProgressMessage(isExtra ? "Getting HTML frame file: " + filename : "Getting main HTML file");
            String htmlContent = getStringFromUrl(url);
            eventCallback.onProgressMessage(isExtra ? "Processing HTML frame file: " + filename : "Processing main HTML file");
            htmlContent = parseHtmlForLinks(htmlContent, baseUrl);

            eventCallback.onProgressMessage(isExtra ? "Saving HTML frame file: " + filename : "Saving main HTML file");
            File outputFile = new File(outputDir, filename);
            saveStringToFile(htmlContent, outputFile);
            return true;

        } catch (IOException | IllegalStateException e) {
            if (isExtra) {
                eventCallback.onError(e);
            } else {
                eventCallback.onFatalError(e, url);
            }
            e.printStackTrace();
            return false;
        }
    }

    private void downloadCssAndParse(final String url, final String outputDir) {

        String filename = getFileName(url);
        File outputFile = new File(outputDir, filename);

        try {
            eventCallback.onProgressMessage("Getting CSS file: " + filename);
            String cssContent = getStringFromUrl(url);

            eventCallback.onProgressMessage("Processing CSS file: " + filename);
            cssContent = parseCssForLinks(cssContent, url);

            eventCallback.onProgressMessage("Saving CSS file: " + filename);
            saveStringToFile(cssContent, outputFile);
        } catch (IOException e) {
            eventCallback.onError(e);
            e.printStackTrace();
        }
    }

    private class DownloadTask implements Runnable {

        private String url;
        private File outputDir;
        private String fileName;

        public DownloadTask(String url, File toPath) {
            this.url = url;
            this.outputDir = toPath;
        }

        public DownloadTask(String url, File toPath, String fileName) {
            this.url = url;
            this.outputDir = toPath;
            this.fileName = fileName;
        }

        @Override
        public void run() {
            if (fileName == null) {
                fileName = getFileName(url);
            }

            File outputFile = new File(outputDir, fileName);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", getOptions().getUserAgent())
                    .tag(HTTP_REQUEST_TAG)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                InputStream is = response.body().byteStream();

                FileOutputStream fos = new FileOutputStream(outputFile);
                final byte[] buffer = new byte[1024 * 32]; // read in batches of 32K
                int length;
                while ((length = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }

                response.body().close();
                fos.flush();
                fos.close();
                is.close();

            } catch (IllegalArgumentException | IOException e) {
                IOException ex = new IOException("File download failed, URL: " + url + ", com.generator.websitecrapper.domain.Output file path: " + outputFile.getPath());

                if (isCancelled) {
                    ex.initCause(new IOException("Save was cancelled, isCancelled is true").initCause(e));
                    eventCallback.onError(ex);
                } else {
                    eventCallback.onError(ex.initCause(e));
                }
            }
        }
    }

    private String getStringFromUrl(String url) throws IOException, IllegalStateException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", getOptions().getUserAgent())
                .tag(HTTP_REQUEST_TAG)
                .build();
        Response response = client.newCall(request).execute();
        String out = response.body().string();
        response.body().close();
        return out;
    }

    private void saveStringToFile(String ToSave, File outputFile) throws IOException {

        if (outputFile.exists()) {
            return;
        }

        outputFile.createNewFile();

        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(ToSave.getBytes());

        fos.flush();
        fos.close();
    }

    private String parseHtmlForLinks(String htmlToParse, String baseUrl) {
        //get all links from this webpage and add them to LinksToVisit ArrayList
        Document document = Jsoup.parse(htmlToParse, baseUrl);
        document.outputSettings().escapeMode(Entities.EscapeMode.extended);

        if (title.isEmpty()) {
            title = document.title();
            eventCallback.onPageTitleAvailable(title);
        }

        if (pageIconUrl.isEmpty()) {
            eventCallback.onProgressMessage("Getting icon...");
            pageIconUrl = IconGrabber.getInstance().getFaviconUrl(document);
        }

        eventCallback.onProgressMessage("Processing HTML...");

        String urlToGrab;

        Elements links;

        if (getOptions().saveFrames()) {
            links = document.select("frame[src]");
            eventCallback.onLogMessage("Got " + links.size() + " frames");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, framesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            links = document.select("iframe[src]");
            eventCallback.onLogMessage("Got " + links.size() + " iframes");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");

                addLinkToList(urlToGrab, framesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().saveOther()) {
            // Get all the links
            links = document.select("link[href]");
            eventCallback.onLogMessage("Got " + links.size() + " link elements with a href attribute");
            for (Element link : links) {
                urlToGrab = link.attr("abs:href");

                //if it is css, parse it later to extract urls (images referenced from "background" attributes for example)
                if (link.attr("rel").equals("stylesheet")) {
                    cssToGrab.add(link.attr("abs:href"));
                } else {
                    addLinkToList(urlToGrab, filesToGrab);
                }

                String replacedURL = getFileName(urlToGrab);
                link.attr("href", replacedURL);
            }

            //get links in embedded css also, and modify the links to point to local files
            links = document.select("style[type=text/css]");
            eventCallback.onLogMessage("Got " + links.size() + " embedded stylesheets, parsing CSS");
            for (Element link : links) {
                String cssToParse = link.data();
                String parsedCss = parseCssForLinks(cssToParse, baseUrl);
                if (link.dataNodes().size() != 0) {
                    link.dataNodes().get(0).setWholeData(parsedCss);
                }
            }

            //get input types with an image type
            links = document.select("input[type=image]");
            eventCallback.onLogMessage("Got " + links.size() + " input elements with type = image");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            //get everything which has a background attribute
            links = document.select("[background]");
            eventCallback.onLogMessage("Got " + links.size() + " elements with a background attribute");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            links = document.select("[style]");
            eventCallback.onLogMessage("Got " + links.size() + " elements with a style attribute, parsing CSS");
            for (Element link : links) {
                String cssToParse = link.attr("style");
                String parsedCss = parseCssForLinks(cssToParse, baseUrl);
                link.attr("style", parsedCss);
            }

        }

        if (getOptions().saveScripts()) {
            links = document.select("script[src]");
            eventCallback.onLogMessage("Got " + links.size() + " script elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().saveImages()) {
            links = document.select("img[src]");
            eventCallback.onLogMessage("Got " + links.size() + " image elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
                link.removeAttr("srcset"); //we don't use this for now, so remove it.
            }

            links = document.select("img[data-canonical-src]");
            eventCallback.onLogMessage("Got " + links.size() + " image elements, w. data-canonical-src");
            for (Element link : links) {
                urlToGrab = link.attr("abs:data-canonical-src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("data-canonical-src", replacedURL);
                link.removeAttr("srcset"); //we don't use this for now, so remove it.
            }
        }

        if (getOptions().saveVideo()) {
            //video src is sometimes in a child element
            links = document.select("video:not([src])");
            eventCallback.onLogMessage("Got " + links.size() + " video elements without src attribute");
            for (Element link : links.select("[src]")) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            links = document.select("video[src]");
            eventCallback.onLogMessage("Got " + links.size() + " video elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().makeLinksAbsolute()) {
            //make links absolute, so they are not broken
            links = document.select("a[href]");
            eventCallback.onLogMessage("Making " + links.size() + " links absolute");
            for (Element link : links) {
                String absUrl = link.attr("abs:href");
                link.attr("href", absUrl);
            }
        }
        return document.outerHtml();
    }

    private String parseCssForLinks(String cssToParse, String baseUrl) {

        String patternString = "url(\\s*\\(\\s*['\"]*\\s*)(.*?)\\s*['\"]*\\s*\\)"; //I hate regexes...

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(cssToParse);

        eventCallback.onLogMessage("Parsing CSS");

        //find everything inside url(" ... ")
        while (matcher.find()) {
            if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
                cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));

            }
            addLinkToList(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl, filesToGrab);
        }

        // find css linked with @import  -  needs testing
        //todo: test this to see if it actually works
        String importString = "@(import\\s*['\"])()([^ '\"]*)";
        pattern = Pattern.compile(importString);
        matcher = pattern.matcher(cssToParse);
        matcher.reset();

        while (matcher.find()) {
            if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
                cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));
            }
            addLinkToList(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl, cssToGrab);
        }
        return cssToParse;
    }

    private boolean isLinkValid(String url) {
        if (url == null || url.length() == 0) {
            return false;
        } else return url.startsWith("http");
    }

    private void addLinkToList(String link, List<String> list) {
        if (!isLinkValid(link) || list.contains(link)) {
            return;
        } else {
            list.add(link);
        }
    }

    private void addLinkToList(String link, String baseUrl, List<String> list) {
        if (link.startsWith("data:image")) {
            return;
        }
        try {
            URL u = new URL(new URL(baseUrl), link);
            link = u.toString();
        } catch (MalformedURLException e) {
            return;
        }

        if (!isLinkValid(link) || list.contains(link)) {
            return;
        } else {
            list.add(link);
        }
    }

    private String getFileName(String url) {

        String filename = url.substring(url.lastIndexOf('/') + 1);

        if (filename.trim().length() == 0) {
            filename = String.valueOf(url.hashCode());
        }

        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?")) + filename.substring(filename.indexOf("?") + 1).hashCode();
        }

        filename = fileNameReplacementPattern.matcher(filename).replaceAll("_");
        filename = filename.substring(0, Math.min(200, filename.length()));
        ;

        return filename;
    }

    private void shutdownExecutor(ExecutorService e, int waitTime, TimeUnit waitTimeUnit) {
        e.shutdown();
        try {
            if (!e.awaitTermination(waitTime, waitTimeUnit)) {
                eventCallback.onError("Executor pool did not termimate after " + waitTime + " " + waitTimeUnit.toString() + ", doing shutdownNow()");
                e.shutdownNow();
            }
        } catch (InterruptedException ie) {
            eventCallback.onError(ie);
        }
    }

    private class BlockingDownloadTaskQueue<E> extends SynchronousQueue<E> {
        BlockingDownloadTaskQueue() {
            super();
        }

        @Override
        public boolean offer(E e) {
            try {
                put(e);
                return true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                eventCallback.onError(ie);

                return false;
            }
        }
    }

}
