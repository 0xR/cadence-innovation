package com.xebia.innovation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.IntStream;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class InnovationDayMain {
    static final String TASK_LIST = "innovationDayTaskList";
    static final String HTTP_BIN_TASK_LIST = "httpBin";

    public interface RestActivities {
        @ActivityMethod(scheduleToCloseTimeoutSeconds = 300, taskList = HTTP_BIN_TASK_LIST)
        String callRestService();
    }

    static class RestActivitiesImpl implements RestActivities {
        @Override
        public String callRestService() {
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
                    throw new RuntimeException("service call failed");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return "success";
        }
    }

    public interface InnovationDayWorkflow {
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = 300, taskList = TASK_LIST)
        void runWorkflow();
    }


    public static class InnovationDayWorkflowImpl implements InnovationDayWorkflow {
        private final RestActivities activities =
            Workflow.newActivityStub(
                RestActivities.class,
                new ActivityOptions.Builder()
                    .setRetryOptions(
                        new RetryOptions.Builder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(1.2)
                            .setMaximumAttempts(10)
                            .build()
                    )
                    .build());


        @Override
        public void runWorkflow() {
            for (int i = 0; i < 10; i++) {
                activities.callRestService();
            }
        }
    }


    public static void main(String[] args) {
        Worker.Factory workerFactory = new Worker.Factory(DOMAIN);
        Worker workflowWorker = workerFactory.newWorker(
            TASK_LIST,
            // should not be needed
            new WorkerOptions.Builder()
                .setTaskListActivitiesPerSecond(0.1)
                .setWorkerActivitiesPerSecond(0.1)
                .build()
        );
        workflowWorker.registerWorkflowImplementationTypes(
            InnovationDayWorkflowImpl.class
        );

        Worker httpbinWorker = workerFactory.newWorker(
            HTTP_BIN_TASK_LIST,
            // doesn't seem to be enforced
            new WorkerOptions.Builder()
                .setTaskListActivitiesPerSecond(0.1)
                .setWorkerActivitiesPerSecond(0.1)
                .build()
        );
        httpbinWorker.registerActivitiesImplementations(new RestActivitiesImpl());

        workerFactory.start();

        // Start a workflow execution. Usually this is done from another program.
        WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
        // Get a workflow stub using the same task list the worker uses.
        InnovationDayWorkflow innovationDayWorkflow = workflowClient.newWorkflowStub(
            InnovationDayWorkflow.class
        );
        // Execute a workflow waiting for it to complete.
        IntStream.range(0, 10).parallel().forEach((i) -> {
            innovationDayWorkflow.runWorkflow();
        });

        System.exit(0);
    }
}
