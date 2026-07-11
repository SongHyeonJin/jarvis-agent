package com.jarvis.domain.port.in;

import java.util.Map;

public interface BrowserUseCase {
    String openUrl(String url);
    String controlYoutube(String action, int n);
    Map<String, Object> pollYoutubeCommand();
}
