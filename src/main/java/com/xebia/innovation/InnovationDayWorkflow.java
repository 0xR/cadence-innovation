package com.xebia.innovation;

import java.io.IOException;

import com.uber.cadence.workflow.WorkflowMethod;

public interface InnovationDayWorkflow {
  @WorkflowMethod(executionStartToCloseTimeoutSeconds = 300)
  void callUnreliableService() throws IOException, InterruptedException;
}
