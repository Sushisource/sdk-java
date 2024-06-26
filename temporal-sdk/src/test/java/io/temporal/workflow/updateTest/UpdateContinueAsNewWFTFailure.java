/*
 * Copyright (C) 2022 Temporal Technologies, Inc. All Rights Reserved.
 *
 * Copyright (C) 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this material except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.temporal.workflow.updateTest;

import static org.junit.Assume.assumeTrue;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.internal.SDKTestOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.workflow.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class UpdateContinueAsNewWFTFailure {
  private static final Semaphore workflowTaskProcessed = new Semaphore(1);

  private static final CompletableFuture<Boolean> continueAsNew = new CompletableFuture<>();

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder().setWorkflowTypes(TestUpdateWorkflowImpl.class).build();

  @Test
  public void testUpdateContinueAsNewAfterWFTFailure() throws InterruptedException {
    // TODO(https://github.com/temporalio/sdk-java/issues/1903)
    assumeTrue("Test Server hangs here", SDKTestWorkflowRule.useExternalService);

    String workflowId = UUID.randomUUID().toString();
    WorkflowClient workflowClient = testWorkflowRule.getWorkflowClient();
    WorkflowOptions options =
        SDKTestOptions.newWorkflowOptionsWithTimeouts(testWorkflowRule.getTaskQueue()).toBuilder()
            .setWorkflowId(workflowId)
            .setWorkflowTaskTimeout(Duration.ofSeconds(1))
            .build();
    TestUpdateWorkflow client = workflowClient.newWorkflowStub(TestUpdateWorkflow.class, options);

    WorkflowClient.start(client::execute, false);
    for (int i = 0; i < 3; i++) {
      workflowTaskProcessed.acquire();
      // Start update in a separate thread to avoid blocking since admitted is not supported.
      Thread asyncUpdate =
          new Thread(
              () -> {
                try {
                  client.update();
                } catch (Exception e) {
                }
              });
      asyncUpdate.start();
    }
    continueAsNew.complete(true);

    Assert.assertEquals("finished", client.execute(false));
  }

  @WorkflowInterface
  public interface TestUpdateWorkflow {

    @WorkflowMethod
    String execute(boolean finish);

    @UpdateMethod
    void update() throws ExecutionException, InterruptedException;
  }

  public static class TestUpdateWorkflowImpl implements TestUpdateWorkflow {

    @Override
    public String execute(boolean finish) {
      Workflow.await(() -> finish);
      return "finished";
    }

    @Override
    public void update() throws ExecutionException, InterruptedException {
      if (continueAsNew.getNow(false)) {
        Workflow.continueAsNew(true);
      }
      workflowTaskProcessed.release();
      throw new RuntimeException("Intentionally fail workflow task");
    }
  }
}
