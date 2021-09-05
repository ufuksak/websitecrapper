package com.generator.websitecrapper.domain;

public interface EventCallback {
    void onProgressChanged(int progress, int maxProgress, boolean indeterminate);

    void onProgressMessage(String fileName);
	
	void onPageTitleAvailable (String pageTitle);

    void onLogMessage (String message);

    void onError(Throwable error);
	
	void onError(String errorMessage);
	
	void onFatalError (Throwable error, String pageUrl);
}