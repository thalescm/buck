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
package com.facebook.buck.event.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.util.Console;
import com.facebook.buck.util.environment.DefaultExecutionEnvironment;
import com.facebook.buck.util.timing.FakeClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.Test;

/** Test static helper functions in {@link AbstractConsoleEventBusListener} */
public class AbstractConsoleEventBusListenerTest {

  private static AbstractConsoleEventBusListener createAbstractConsoleInstance() {
    return new AbstractConsoleEventBusListener(
        Console.createNullConsole(),
        FakeClock.doNotCare(),
        Locale.US,
        new DefaultExecutionEnvironment(
            ImmutableMap.copyOf(System.getenv()), System.getProperties()),
        false,
        1,
        false) {
      @Override
      public void printSevereWarningDirectly(String line) {}
    };
  }

  @Test
  public void testApproximateDistBuildProgressDoesNotLosePrecision() {
    AbstractConsoleEventBusListener listener = createAbstractConsoleInstance();

    listener.distBuildTotalRulesCount = 0;
    listener.distBuildFinishedRulesCount = 0;
    assertEquals(Optional.of(0.0), listener.getApproximateDistBuildProgress());

    listener.distBuildTotalRulesCount = 100;
    listener.distBuildFinishedRulesCount = 50;
    assertEquals(Optional.of(0.5), listener.getApproximateDistBuildProgress());

    listener.distBuildTotalRulesCount = 17;
    listener.distBuildFinishedRulesCount = 4;
    assertEquals(Optional.of(0.23), listener.getApproximateDistBuildProgress());
  }

  @Test
  public void testGetEventsBetween() throws Exception {
    final EventPair zeroToOneHundred = EventPair.proxy(0, 100);
    final EventPair oneToTwoHundred = EventPair.proxy(100, 200);
    final EventPair twoToThreeHundred = EventPair.proxy(200, 300);
    final EventPair threeToFourHundred = EventPair.proxy(300, 400);
    final EventPair fourToFiveHundred = EventPair.proxy(400, 500);
    List<EventPair> events =
        ImmutableList.<EventPair>builder()
            .add(zeroToOneHundred)
            .add(oneToTwoHundred)
            .add(twoToThreeHundred)
            .add(threeToFourHundred)
            .add(fourToFiveHundred)
            .build();

    Collection<EventPair> fiftyToThreeHundred =
        AbstractConsoleEventBusListener.getEventsBetween(50, 300, events);

    // First event should have been replaced by a proxy
    assertFalse(
        "0-100 event straddled a boundary, should have been replaced by a proxy of 50-100",
        fiftyToThreeHundred.contains(zeroToOneHundred));
    assertTrue(
        "0-100 event straddled a boundary, should have been replaced by a proxy of 50-100",
        fiftyToThreeHundred.contains(EventPair.proxy(50, 100)));

    // Second and third events should be present in their entirety
    assertTrue(
        "Second event (100-200) is totally contained, so it should pass the filter",
        fiftyToThreeHundred.contains(oneToTwoHundred));
    assertTrue(
        "Third event (200-300) matches the boundary which should be inclusive",
        fiftyToThreeHundred.contains(twoToThreeHundred));

    // Fourth event should have been trimmed to a proxy
    assertFalse(
        "Fourth event (300-400) starts on a boundary, so it should have been proxied",
        fiftyToThreeHundred.contains(threeToFourHundred));
    assertTrue(
        "Fourth event (300-400) starts on a boundary, so it should have been proxied",
        fiftyToThreeHundred.contains(EventPair.proxy(300, 300)));

    // Fifth event should be left out
    assertFalse(
        "Fifth event (400-500) is totally out of range, should be absent",
        fiftyToThreeHundred.contains(fourToFiveHundred));
  }

  @Test
  public void testGetWorkingTimeFromLastStartUntilNowIsNegOneForClosedPairs() throws Exception {
    final EventPair closed = EventPair.proxy(100, 500);
    List<EventPair> events = ImmutableList.of(closed);
    long timeUntilNow =
        AbstractConsoleEventBusListener.getWorkingTimeFromLastStartUntilNow(events, 600);
    assertEquals("Time should be -1 since there's no ongoing events", -1L, timeUntilNow);
  }

  @Test
  public void testGetWorkingTimeFromLastStartUntilNowIsUntilNowForOpenPairs() throws Exception {
    // Test overlapping ongoing events do not get measured twice
    final EventPair ongoing1 = EventPair.of(Optional.of(ProxyBuckEvent.of(100)), Optional.empty());
    final EventPair ongoing2 = EventPair.of(Optional.of(ProxyBuckEvent.of(200)), Optional.empty());
    long timeUntilNow =
        AbstractConsoleEventBusListener.getWorkingTimeFromLastStartUntilNow(
            ImmutableList.of(ongoing1, ongoing2), 300);
    assertEquals("Time should be counted since the latest ongoing event", 100L, timeUntilNow);

    // Test completed events are correctly accounted when getting ongoing time
    // If there are completed events, we don't want to overcount the time spent,
    // so ongoing time is always calculated from the last timestamp in the set.
    final EventPair closed = EventPair.proxy(300, 400);
    timeUntilNow =
        AbstractConsoleEventBusListener.getWorkingTimeFromLastStartUntilNow(
            ImmutableList.of(ongoing1, closed), 600);
    assertEquals("Time should only be counted from the last closed event", 200L, timeUntilNow);

    // Test that finished-only events do not count as ongoing
    final EventPair finishOnly =
        EventPair.of(Optional.empty(), Optional.of(ProxyBuckEvent.of(100)));
    timeUntilNow =
        AbstractConsoleEventBusListener.getWorkingTimeFromLastStartUntilNow(
            ImmutableList.of(finishOnly), 600);
    assertEquals("Finished events without start are not ongoing", -1L, timeUntilNow);
  }

  @Test
  public void testGetTotalCompletedTimeFromEventPairs() throws Exception {
    // Test events with a gap in between do not count the gap
    final EventPair zeroToOneHundred = EventPair.proxy(0, 100);
    final EventPair oneToThreeHundred = EventPair.proxy(100, 300);
    final EventPair twoToThreeHundred = EventPair.proxy(200, 300);

    long timeElapsed =
        AbstractConsoleEventBusListener.getTotalCompletedTimeFromEventPairs(
            ImmutableList.of(zeroToOneHundred, twoToThreeHundred));
    assertEquals("We should not add up time when there are no spanning events", 200L, timeElapsed);

    // Test overlapping events do not double-count
    timeElapsed =
        AbstractConsoleEventBusListener.getTotalCompletedTimeFromEventPairs(
            ImmutableList.of(oneToThreeHundred, twoToThreeHundred));
    assertEquals("We should not double count when two event pairs overlap", 200L, timeElapsed);
  }
}
