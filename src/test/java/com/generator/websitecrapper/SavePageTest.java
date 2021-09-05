package com.generator.websitecrapper;

import com.generator.websitecrapper.domain.EventCallback;
import com.generator.websitecrapper.domain.Output;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SavePageTest {

    private static final String OUTPUT_PATH = "/tmp/";

    private WebSaveGenerator savePage;

    @Before
    public void setUp() {
        EventCallback eventCallback = new EventCallback() {
            @Override
            public void onProgressChanged(int progress, int maxProgress, boolean indeterminate) {

            }

            @Override
            public void onProgressMessage(String fileName) {

            }

            @Override
            public void onPageTitleAvailable(String pageTitle) {

            }

            @Override
            public void onLogMessage(String message) {

            }

            @Override
            public void onError(Throwable error) {

            }

            @Override
            public void onError(String errorMessage) {

            }

            @Override
            public void onFatalError(Throwable error, String pageUrl) {

            }
        };
        savePage = new WebSaveGenerator(eventCallback);
    }

    @Test
    public void shouldTestDownloadPage() {
        // Assign
        List<String> websiteList = new ArrayList<>();
        websiteList.add("https://www.google.com");
        websiteList.add("https://github.com");

        // Act
        List<Boolean> resultList = savePage.getPage(OUTPUT_PATH, websiteList);

        // Assert
        Assert.assertEquals(2, resultList.size());
    }

    @Test
    public void shouldTestFileStatistics() throws IOException {
        // Assign
        List<String> websiteList = new ArrayList<>();
        websiteList.add("https://www.google.com");
        websiteList.add("https://github.com");
        savePage.getPage(OUTPUT_PATH, websiteList);

        // Act
        Output output = savePage.getStatistics("https://github.com", OUTPUT_PATH);

        // Assert
        Assert.assertTrue(output.getImageCount() > 0);
    }
}
