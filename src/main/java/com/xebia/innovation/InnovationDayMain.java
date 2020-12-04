package com.xebia.innovation;

import com.uber.cadence.worker.Worker;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class InnovationDayMain {

    public static void main(String[] args) {
        Worker.Factory workerFactory = new Worker.Factory(DOMAIN);
        Worker worker = workerFactory.newWorker("innovationDayTaskList");
        worker.registerWorkflowImplementationTypes(
            InnovationDayWorkflowImpl.class
        );
        workerFactory.start();
    }
}
