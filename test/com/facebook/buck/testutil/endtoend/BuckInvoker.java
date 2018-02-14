/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.testutil.endtoend;

import com.facebook.buck.testutil.ProcessResult;
import com.google.common.collect.ImmutableMap;
import org.junit.runners.model.Statement;

/**
 * A statement that takes a testDescriptor and target, and invokes Buck commands around that target,
 * including the construction and destruction of the necessary Workspace.
 */
public class BuckInvoker extends Statement {
  private final EndToEndTestDescriptor testDescriptor;
  private final Object target;

  public BuckInvoker(EndToEndTestDescriptor testDescriptor, Object target) {
    this.testDescriptor = testDescriptor;
    this.target = target;
  }

  @Override
  public void evaluate() throws Throwable {
    EndToEndWorkspace workspace = new EndToEndWorkspace();
    workspace.setup();
    try {
      ProcessResult result =
          workspace.runBuckCommand(
              testDescriptor.getBuckdEnabled(),
              ImmutableMap.copyOf(testDescriptor.getVariableMap()),
              testDescriptor.getTemplateSet(),
              testDescriptor.getCommand());
      testDescriptor.getMethod().invokeExplosively(target, testDescriptor, result);
    } finally {
      workspace.teardown();
    }
  }
}
