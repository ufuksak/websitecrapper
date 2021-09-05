package com.generator.websitecrapper;

import com.generator.websitecrapper.domain.EventCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SavePageMain {
    private static final String OUTPUT_PATH = "/tmp/";

    public static void main(String[] args) throws IOException {
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

        WebSaveGenerator webSaveGenerator = new WebSaveGenerator(eventCallback);
        if (args.length == 2 && args[0].equals("--metadata")) {
            webSaveGenerator.getStatistics(args[1], OUTPUT_PATH);
        } else if (args.length == 2 && args[0].equals("--zip")) {
            webSaveGenerator.createZip(args[1], OUTPUT_PATH);
        } else {
            List<String> websiteList = new ArrayList<>(Arrays.asList(args));
            webSaveGenerator.getPage(OUTPUT_PATH, websiteList);
        }
    }
}
