package com.xebia.innovation;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class InnovationDayMain {
    static final String TASK_LIST = "innovationDayTaskList";

    public interface RestActivities {
        @ActivityMethod(scheduleToCloseTimeoutSeconds = 300)
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
        void callUnreliableService() throws IOException, InterruptedException;
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
        public void callUnreliableService() {
            List<Promise<String>> promiseList = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                promiseList.add(
                    Async.function(activities::callRestService)
                );
            }
            Promise.allOf(promiseList).get();
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        Worker.Factory workerFactory = new Worker.Factory(DOMAIN);
        Worker worker = workerFactory.newWorker(TASK_LIST, new WorkerOptions.Builder()
            .setTaskListActivitiesPerSecond(1)
            .build());
        worker.registerWorkflowImplementationTypes(
            InnovationDayWorkflowImpl.class
        );
        worker.registerActivitiesImplementations(new RestActivitiesImpl());
        workerFactory.start();

        RetryOptions retryOptions = new RetryOptions.Builder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setMaximumAttempts(10)
            .build();
        // Start a workflow execution. Usually this is done from another program.
        WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
        // Get a workflow stub using the same task list the worker uses.
        InnovationDayWorkflow innovationDayWorkflow = workflowClient.newWorkflowStub(
            InnovationDayWorkflow.class
//            new WorkflowOptions.Builder().setRetryOptions(retryOptions).build()
        );
        // Execute a workflow waiting for it to complete.
        innovationDayWorkflow.callUnreliableService();
        System.exit(0);
    }
}
