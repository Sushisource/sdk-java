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

package io.temporal.workflow;

import static org.junit.Assert.assertEquals;

import io.temporal.client.WorkflowStub;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.workflow.shared.TestActivities;
import io.temporal.workflow.shared.TestWorkflows;
import org.junit.Rule;
import org.junit.Test;

public class GetRootWorkflowExecutionTest {
  private static final TestActivities.VariousTestActivities activitiesImpl =
      new TestActivities.TestActivitiesImpl();

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestWorkflowImpl.class)
          .setActivityImplementations(activitiesImpl)
          .build();

  @Test
  public void getRootWorkflowExecution() {
    TestWorkflows.TestWorkflowReturnString workflowStub =
        testWorkflowRule.newWorkflowStubTimeoutOptions(
            TestWorkflows.TestWorkflowReturnString.class);
    String workflowId = workflowStub.execute();
    assertEquals(
        "empty " + WorkflowStub.fromTyped(workflowStub).getExecution().getWorkflowId(), workflowId);
  }

  public static class TestWorkflowImpl implements TestWorkflows.TestWorkflowReturnString {

    @Override
    public String execute() {
      String result = Workflow.getInfo().getRootWorkflowId().orElse("empty");
      if (!Workflow.getInfo().getParentWorkflowId().isPresent()) {
        result += " ";
        result +=
            Workflow.newChildWorkflowStub(TestWorkflows.TestWorkflowReturnString.class).execute();
      }
      return result;
    }
  }
}
