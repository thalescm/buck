/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.distributed.build_slave;

import com.facebook.buck.distributed.BuildStatusUtil;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.ExitCode;
import com.facebook.buck.distributed.thrift.BuildJob;
import com.facebook.buck.distributed.thrift.BuildStatus;
import com.facebook.buck.distributed.thrift.CoordinatorService;
import com.facebook.buck.distributed.thrift.CoordinatorService.Iface;
import com.facebook.buck.distributed.thrift.GetWorkRequest;
import com.facebook.buck.distributed.thrift.GetWorkResponse;
import com.facebook.buck.distributed.thrift.ReportMinionAliveRequest;
import com.facebook.buck.distributed.thrift.ReportMinionAliveResponse;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.log.Logger;
import com.facebook.buck.log.TimedLogger;
import com.facebook.buck.slb.ThriftException;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.server.TThreadedSelectorServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TTransportException;

public class ThriftCoordinatorServer implements Closeable {

  /** Information about the exit state of the Coordinator. */
  public static class ExitState {

    private final int exitCode;
    private final String exitMessage;
    private final boolean wasExitCodeSetByServers;

    private ExitState(int exitCode, String exitMessage, boolean wasExitCodeSetByServers) {
      this.exitCode = exitCode;
      this.exitMessage = exitMessage;
      this.wasExitCodeSetByServers = wasExitCodeSetByServers;
    }

    public static ExitState setLocally(int exitCode, String exitMessage) {
      return new ExitState(exitCode, exitMessage, false);
    }

    public static ExitState setByServers(int exitCode, String exitMessage) {
      return new ExitState(exitCode, exitMessage, true);
    }

    public int getExitCode() {
      return exitCode;
    }

    public boolean wasExitCodeSetByServers() {
      return wasExitCodeSetByServers;
    }

    public String getExitMessage() {
      return exitMessage;
    }

    @Override
    public String toString() {
      return "ExitState{"
          + "exitCode="
          + exitCode
          + ", exitMessage='"
          + exitMessage
          + '\''
          + ", wasExitCodeSetByServers="
          + wasExitCodeSetByServers
          + '}';
    }
  }

  /** Listen to ThriftCoordinatorServer events. */
  public interface EventListener {

    void onThriftServerStarted(String address, int port) throws IOException;

    void onThriftServerClosing(ThriftCoordinatorServer.ExitState exitState) throws IOException;
  }

  private static final TimedLogger LOG = new TimedLogger(Logger.get(ThriftCoordinatorServer.class));

  private static final long MAX_TEAR_DOWN_MILLIS = TimeUnit.SECONDS.toMillis(2);
  private static final long MAX_DIST_BUILD_DURATION_MILLIS = TimeUnit.HOURS.toMillis(2);

  private final DistBuildTraceTracker chromeTraceTracker;
  private final CoordinatorService.Processor<CoordinatorService.Iface> processor;
  private final Object lock;
  private final CompletableFuture<ExitState> exitCodeFuture;
  private final StampedeId stampedeId;
  private final ThriftCoordinatorServer.EventListener eventListener;
  private final CoordinatorBuildRuleEventsPublisher coordinatorBuildRuleEventsPublisher;
  private final MinionHealthTracker minionHealthTracker;
  private final DistBuildService distBuildService;

  private volatile OptionalInt port;

  private volatile CoordinatorService.Iface handler;

  @Nullable private volatile TThreadedSelectorServer server;
  @Nullable private Thread serverThread;

  public ThriftCoordinatorServer(
      OptionalInt port,
      ListenableFuture<BuildTargetsQueue> queue,
      StampedeId stampedeId,
      EventListener eventListener,
      CoordinatorBuildRuleEventsPublisher coordinatorBuildRuleEventsPublisher,
      MinionHealthTracker minionHealthTracker,
      DistBuildService distBuildService) {
    this.eventListener = eventListener;
    this.stampedeId = stampedeId;
    this.coordinatorBuildRuleEventsPublisher = coordinatorBuildRuleEventsPublisher;
    this.minionHealthTracker = minionHealthTracker;
    this.distBuildService = distBuildService;
    this.lock = new Object();
    this.exitCodeFuture = new CompletableFuture<>();
    this.chromeTraceTracker = new DistBuildTraceTracker(stampedeId);
    this.port = port;
    this.handler = new IdleCoordinatorService();
    CoordinatorServiceHandler handlerWrapper = new CoordinatorServiceHandler();
    this.processor = new CoordinatorService.Processor<>(handlerWrapper);
    queue.addListener(() -> switchToActiveModeOrFail(queue), MoreExecutors.directExecutor());
  }

  public ThriftCoordinatorServer start() throws IOException {
    synchronized (lock) {
      TNonblockingServerSocket transport;
      try {
        transport = new TNonblockingServerSocket(this.port.orElse(0));
        // If we initially specified port zero, we would now have the correct value.
        this.port = OptionalInt.of(transport.getPort());
      } catch (TTransportException e) {
        throw new ThriftException(e);
      }

      TThreadedSelectorServer.Args serverArgs = new TThreadedSelectorServer.Args(transport);
      serverArgs.processor(processor);
      server = new TThreadedSelectorServer(serverArgs);
      serverThread = new Thread(() -> Preconditions.checkNotNull(server).serve());
      serverThread.start();
    }

    eventListener.onThriftServerStarted(InetAddress.getLocalHost().getHostName(), port.getAsInt());
    return this;
  }

  /** Checks if all minions are alive. Fails the distributed build if they are not. */
  public void checkAllMinionsAreAlive() {
    List<String> deadMinions = minionHealthTracker.getDeadMinions();
    if (!deadMinions.isEmpty()) {
      String msg =
          String.format(
              "Failing the build due to dead minions: [%s].", Joiner.on(", ").join(deadMinions));
      LOG.error(msg);
      exitCodeFuture.complete(
          ExitState.setLocally(ExitCode.DEAD_MINION_FOUND_EXIT_CODE.getCode(), msg));
    }
  }

  /** Checks whether the BuildStatus has not been set to terminated remotely. */
  public void checkBuildStatusIsNotTerminated() throws IOException {
    BuildJob buildJob = distBuildService.getCurrentBuildJobState(stampedeId);
    BuildStatus status = buildJob.getStatus();
    if (!buildJob.isSetStatus() || !BuildStatusUtil.isTerminalBuildStatus(status)) {
      return;
    }

    if (status == BuildStatus.FINISHED_SUCCESSFULLY) {
      exitCodeFuture.complete(
          ExitState.setByServers(ExitCode.SUCCESSFUL.getCode(), "Build succeeded externally."));
    } else {
      exitCodeFuture.complete(
          ExitState.setByServers(
              ExitCode.BUILD_FAILED_EXTERNALLY_EXIT_CODE.getCode(), "Build failed externally."));
    }
  }

  private ThriftCoordinatorServer stop() throws IOException {
    ExitState exitState =
        exitCodeFuture.getNow(
            ExitState.setLocally(
                ExitCode.UNEXPECTED_STOP_EXIT_CODE.getCode(),
                "Forced unexpected Coordinator shutdown."));
    eventListener.onThriftServerClosing(exitState);
    synchronized (lock) {
      Preconditions.checkNotNull(server, "Server has already been stopped.").stop();
      server = null;
      try {
        Preconditions.checkNotNull(serverThread).join(MAX_TEAR_DOWN_MILLIS);
      } catch (InterruptedException e) {
        throw new IOException("Coordinator thrift server took too long to tear down.", e);
      } finally {
        serverThread = null;
      }
    }

    return this;
  }

  public int getPort() {
    if (port.isPresent()) {
      return port.getAsInt();
    }
    throw new RuntimeException("Port is unknown since the coordinator server is not up yet.");
  }

  @Override
  public void close() throws IOException {
    if (server != null) {
      stop();
    }
  }

  /** Create a snapshot of dist build trace. */
  public DistBuildTrace traceSnapshot() {
    synchronized (lock) {
      return chromeTraceTracker.snapshot();
    }
  }

  public Future<ExitState> getExitState() {
    return exitCodeFuture;
  }

  public int waitUntilBuildCompletesAndReturnExitCode() {
    try {
      LOG.verbose("Coordinator going into blocking wait mode...");
      return getExitState()
          .get(MAX_DIST_BUILD_DURATION_MILLIS, TimeUnit.MILLISECONDS)
          .getExitCode();
    } catch (ExecutionException | TimeoutException | InterruptedException e) {
      LOG.error(e);
      throw new RuntimeException("The distributed build Coordinator was interrupted.", e);
    }
  }

  private void switchToActiveModeOrFail(Future<BuildTargetsQueue> queue) {
    Preconditions.checkState(queue.isDone());
    try {
      MinionWorkloadAllocator allocator = new MinionWorkloadAllocator(queue.get());
      this.handler =
          new ActiveCoordinatorService(
              allocator,
              exitCodeFuture,
              chromeTraceTracker,
              coordinatorBuildRuleEventsPublisher,
              minionHealthTracker);
    } catch (InterruptedException | ExecutionException e) {
      String msg = "Failed to create the BuildTargetsQueue.";
      LOG.error(msg);
      this.handler =
          new Iface() {
            @Override
            public GetWorkResponse getWork(GetWorkRequest request) throws TException {
              throw new RuntimeException(msg, e);
            }

            @Override
            public ReportMinionAliveResponse reportMinionAlive(ReportMinionAliveRequest request)
                throws TException {
              return new ReportMinionAliveResponse();
            }
          };
      // Any exception we throw here is going to be swallowed by the async executor.
    }
  }

  private class CoordinatorServiceHandler implements CoordinatorService.Iface {

    @Override
    public ReportMinionAliveResponse reportMinionAlive(ReportMinionAliveRequest request)
        throws TException {
      try {
        checkBuildId(request.getStampedeId());
        Preconditions.checkArgument(request.isSetMinionId());
        return handler.reportMinionAlive(request);
      } catch (Throwable e) {
        LOG.error(
            e, "reportIAmAlive() failed: internal state may be corrupted, so exiting coordinator.");
        String msg =
            String.format(
                "Failed to handle ReportMinionAliveRequest for minion [%s].",
                request.getMinionId());
        exitCodeFuture.complete(
            ExitState.setLocally(ExitCode.I_AM_ALIVE_FAILED_EXIT_CODE.getCode(), msg));
        throw e;
      }
    }

    @Override
    public GetWorkResponse getWork(GetWorkRequest request) throws TException {
      try {
        return getWorkUnsafe(request);
      } catch (Throwable e) {
        LOG.error(e, "getWork() failed: internal state may be corrupted, so exiting coordinator.");
        String msg =
            String.format(
                "Failed to handle GetWorkRequest for minion [%s].", request.getMinionId());
        exitCodeFuture.complete(
            ExitState.setLocally(ExitCode.GET_WORK_FAILED_EXIT_CODE.getCode(), msg));
        throw e;
      }
    }

    private GetWorkResponse getWorkUnsafe(GetWorkRequest request) throws TException {
      LOG.info(
          String.format(
              "Got GetWorkRequest from minion [%s]. [%s] targets finished. [%s] units requested",
              request.getMinionId(),
              request.getFinishedTargets().size(),
              request.getMaxWorkUnitsToFetch()));

      checkBuildId(request.getStampedeId());
      Preconditions.checkArgument(request.isSetMinionId());
      Preconditions.checkArgument(request.isSetLastExitCode());

      synchronized (lock) {
        return handler.getWork(request);
      }
    }

    private void checkBuildId(StampedeId buildId) {
      Preconditions.checkArgument(
          stampedeId.equals(buildId),
          "Request stampede build id [%s] does not match the current build id [%s].",
          buildId.getId(),
          stampedeId.getId());
    }
  }
}
