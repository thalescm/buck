/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.slb;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.log.Logger;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.util.types.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ServerHealthManager {
  private static final Logger LOG = Logger.get(ServerHealthManager.class);
  public static final int CACHE_TIME_MS = 1000;

  private static final Comparator<Pair<URI, Long>> LATENCY_COMPARATOR =
      (o1, o2) -> (int) (o1.getSecond() - o2.getSecond());

  // TODO(ruibm): It could be useful to preserve this state across runs in the local fs.
  private final ConcurrentHashMap<URI, ServerHealthState> servers;
  private final int maxAcceptableLatencyMillis;
  private final int latencyCheckTimeRangeMillis;
  private final float maxErrorPercentage;
  private final int errorCheckTimeRangeMillis;
  private final BuckEventBus eventBus;
  private final LoadingCache<Object, Optional<URI>> getBestServerCache;

  private final Clock clock;

  public ServerHealthManager(
      ImmutableList<URI> servers,
      int errorCheckTimeRangeMillis,
      float maxErrorPercentage,
      int latencyCheckTimeRangeMillis,
      int maxAcceptableLatencyMillis,
      BuckEventBus eventBus,
      Clock clock) {
    LOG.getClass();
    this.errorCheckTimeRangeMillis = errorCheckTimeRangeMillis;
    this.maxErrorPercentage = maxErrorPercentage;
    this.latencyCheckTimeRangeMillis = latencyCheckTimeRangeMillis;
    this.maxAcceptableLatencyMillis = maxAcceptableLatencyMillis;
    this.clock = clock;
    this.servers = new ConcurrentHashMap<>();
    for (URI server : servers) {
      this.servers.put(server, new ServerHealthState(server));
    }
    this.eventBus = eventBus;
    this.getBestServerCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_TIME_MS, TimeUnit.MILLISECONDS)
            .build(
                new CacheLoader<Object, Optional<URI>>() {
                  @Override
                  public Optional<URI> load(Object key) throws Exception {
                    return calculateBestServer();
                  }
                });
  }

  public void reportPingLatency(URI server, long latencyMillis) {
    Preconditions.checkState(servers.containsKey(server), "Unknown server [%s]", server);
    servers.get(server).reportPingLatency(clock.currentTimeMillis(), latencyMillis);
    if (latencyMillis > maxAcceptableLatencyMillis) {
      getBestServerCache.refresh(this);
    }
  }

  public void reportRequestError(URI server) {
    Preconditions.checkState(servers.containsKey(server), "Unknown server [%s]", server);
    // Invalidate the best server on any error.
    servers.get(server).reportRequestError(clock.currentTimeMillis());
    getBestServerCache.refresh(this);
  }

  public void reportRequestSuccess(URI server) {
    Preconditions.checkState(servers.containsKey(server), "Unknown server [%s]", server);
    servers.get(server).reportRequestSuccess(clock.currentTimeMillis());
  }

  public URI getBestServer() throws NoHealthyServersException {
    try {
      Optional<URI> server = getBestServerCache.get(this);
      if (server.isPresent()) {
        return server.get();
      }
      throw new NoHealthyServersException(
          String.format(
              "No servers available. Too many errors reported by all servers in the pool: [%s]",
              Joiner.on(", ")
                  .join(FluentIterable.from(servers.keySet()).transform(Object::toString))));
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<URI> calculateBestServer() {
    ServerHealthManagerEventData.Builder data = ServerHealthManagerEventData.builder();
    Map<URI, PerServerData.Builder> allPerServerData = new HashMap<>();
    try {
      long epochMillis = clock.currentTimeMillis();
      List<Pair<URI, Long>> serverLatencies = new ArrayList<>();
      for (ServerHealthState state : servers.values()) {
        URI server = state.getServer();
        PerServerData.Builder perServerData = PerServerData.builder().setServer(server);
        allPerServerData.put(server, perServerData);

        float errorPercentage = state.getErrorPercentage(epochMillis, errorCheckTimeRangeMillis);
        long latencyMillis = state.getPingLatencyMillis(epochMillis, latencyCheckTimeRangeMillis);
        if (errorPercentage <= maxErrorPercentage && latencyMillis <= maxAcceptableLatencyMillis) {
          serverLatencies.add(new Pair<>(state.getServer(), latencyMillis));
        } else {
          perServerData.setServerUnhealthy(true);
        }
      }

      if (serverLatencies.size() == 0) {
        data.setNoHealthyServersAvailable(true);
        return Optional.empty();
      }

      Collections.sort(serverLatencies, LATENCY_COMPARATOR);
      URI bestServer = serverLatencies.get(0).getFirst();
      Preconditions.checkNotNull(allPerServerData.get(bestServer)).setBestServer(true);
      return Optional.of(bestServer);
    } finally {
      for (PerServerData.Builder builder : allPerServerData.values()) {
        data.addPerServerData(builder.build());
      }
      eventBus.post(new ServerHealthManagerEvent(data.build()));
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("ServerHealthManager{\n");
    for (ServerHealthState server : servers.values()) {
      builder.append(
          String.format(
              "  %s\n", server.toString(clock.currentTimeMillis(), latencyCheckTimeRangeMillis)));
    }

    builder.append("}");
    return builder.toString();
  }
}
