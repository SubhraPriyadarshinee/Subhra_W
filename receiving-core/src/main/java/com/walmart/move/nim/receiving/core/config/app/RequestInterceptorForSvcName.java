package com.walmart.move.nim.receiving.core.config.app;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class RequestInterceptorForSvcName implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        HttpHeaders headers = request.getHeaders();

        boolean headerExists = headers.entrySet().stream()
                .anyMatch(entry -> entry.getKey().equalsIgnoreCase("WM_SVC.NAME"));

        if (!headerExists) {
            String appName = System.getProperty("runtime.context.appName");
            if (appName != null) {
                headers.add("WM_SVC.NAME", appName);
            }
            else {
                headers.add("WM_SVC.NAME", "AppName");
            }
        }
        return execution.execute(request, body);
    }
}
