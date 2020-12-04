package com.xebia.innovation;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class InnovationDayMain {

    public interface InnovationDayWorkflow {
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = 300)
        void callUnreliableService() throws IOException, InterruptedException;
    }

    public interface RestActivities {
        @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
        void callRestService();
    }

    public static class InnovationDayWorkflowImpl implements InnovationDayWorkflow {
        private final RestActivities activities =
                Workflow.newActivityStub(RestActivities.class);
        @Override
        public void callUnreliableService() {
            Workflow.retry(new RetryOptions.Builder().setInitialInterval(Duration.ofSeconds(1)).setMaximumAttempts(10).build(), () -> activities.callRestService());
        }
    }

    static class RestActivitiesImpl implements RestActivities {
        @Override
        public void callRestService() {
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
        }
    }

    public static void main(String[] args) {
        Worker.Factory workerFactory = new Worker.Factory(DOMAIN);
        Worker worker = workerFactory.newWorker("innovationDayTaskList", new WorkerOptions.Builder()
                .setTaskListActivitiesPerSecond(1)
                .build());
        worker.registerWorkflowImplementationTypes(
            InnovationDayWorkflowImpl.class
        );
        worker.registerActivitiesImplementations(new RestActivitiesImpl());
        workerFactory.start();
    }
}
