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
package com.facebook.buck.command;

import com.facebook.buck.rules.RemoteBuildRuleCompletionWaiter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * * Triggers a local build, using the given parameters (with the rest coming from BuildCommand).
 */
public interface LocalBuildExecutorInvoker {
  int executeLocalBuild(
      boolean isDownloadHeavyBuild,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter,
      CountDownLatch initializeBuildLatch,
      AtomicReference<Build> buildReference)
      throws IOException, InterruptedException;
}
