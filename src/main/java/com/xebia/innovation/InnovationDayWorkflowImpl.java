package com.xebia.innovation;

import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.workflow.Workflow;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


public class InnovationDayWorkflowImpl implements InnovationDayWorkflow {
    @Override
    public void callUnreliableService() {
        Workflow.retry(new RetryOptions.Builder().setInitialInterval(Duration.ofSeconds(1)).setMaximumAttempts(10).build(), () -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://httpbin.org/status/500,200"))
                        .build();
                HttpResponse<String> send = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (send.statusCode() == 500) {
                    throw new Error("service call failed");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
