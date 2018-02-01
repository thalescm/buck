/*
 * Copyright 2017-present Facebook, Inc.
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
package com.facebook.buck.rules;

/**
 * Used to notify the client of a remote build that a particular rule (or the entire build) has
 * finished.
 */
public interface RemoteBuildRuleCompletionNotifier {
  /**
   * Signals that an individual build rule has started building remotely
   *
   * @param buildTarget
   */
  void signalStartedRemoteBuildingOfBuildRule(String buildTarget);

  /**
   * Signals that an individual build rule has completed remotely
   *
   * @param buildTarget
   */
  void signalCompletionOfBuildRule(String buildTarget);

  /** Signals that the entire remote build has finished (and in turn all rules within it) */
  void signalCompletionOfRemoteBuild();

  /** Configured threshold percentage of build rules has finished building remotely * */
  void signalMostBuildRulesFinished();
}
