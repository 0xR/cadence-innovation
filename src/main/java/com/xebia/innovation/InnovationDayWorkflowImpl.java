package com.xebia.innovation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class InnovationDayWorkflowImpl implements InnovationDayWorkflow {
    @Override
    public void callUnreliableService() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://httpbin.org/status/500,200"))
            .build();
        HttpResponse<String> send = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        if (send.statusCode() <= 500 && send.statusCode() < 600) {
            throw new Error("service call failed");
        }
    }
}
