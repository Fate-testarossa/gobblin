/*
 * (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.instrumented.qualitychecker;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.base.Optional;
import com.google.common.io.Closer;

import gobblin.configuration.State;
import gobblin.instrumented.Instrumentable;
import gobblin.instrumented.Instrumented;
import gobblin.metrics.GobblinMetrics;
import gobblin.metrics.MetricContext;
import gobblin.metrics.MetricNames;
import gobblin.qualitychecker.row.RowLevelPolicy;


/**
 * package-private implementation of instrumentation for {@link gobblin.qualitychecker.row.RowLevelPolicy}.
 * See {@link gobblin.instrumented.qualitychecker.InstrumentedRowLevelPolicy} for extensible class.
 */
abstract class InstrumentedRowLevelPolicyBase extends RowLevelPolicy implements Instrumentable, Closeable {

  private final boolean instrumentationEnabled;

  protected final MetricContext metricContext;
  protected final Optional<Meter> recordsMeter;
  protected final Optional<Meter> passedRecordsMeter;
  protected final Optional<Meter> failedRecordsMeter;
  protected final Optional<Timer> policyTimer;
  protected final Closer closer;

  public InstrumentedRowLevelPolicyBase(State state, Type type) {
    super(state, type);
    this.instrumentationEnabled = GobblinMetrics.isEnabled(state);
    this.closer = Closer.create();
    this.metricContext =
        closer.register(Instrumented.getMetricContext(state, this.getClass()));

    if(isInstrumentationEnabled()) {
      this.recordsMeter = Optional.of(this.metricContext.meter(MetricNames.RowLevelPolicyMetrics.RECORDS_IN_METER));
      this.passedRecordsMeter = Optional.of(
          this.metricContext.meter(MetricNames.RowLevelPolicyMetrics.RECORDS_PASSED_METER));
      this.failedRecordsMeter = Optional.of(
          this.metricContext.meter(MetricNames.RowLevelPolicyMetrics.RECORDS_FAILED_METER));
      this.policyTimer = Optional.of(
          this.metricContext.timer(MetricNames.RowLevelPolicyMetrics.CHECK_TIMER));
    } else {
      this.recordsMeter = Optional.absent();
      this.passedRecordsMeter = Optional.absent();
      this.failedRecordsMeter = Optional.absent();
      this.policyTimer = Optional.absent();
    }
  }

  @Override
  public boolean isInstrumentationEnabled() {
    return this.instrumentationEnabled;
  }

  @Override
  public Result executePolicy(Object record) {
    if(!isInstrumentationEnabled()) {
      return executePolicyImpl(record);
    }

    long startTime = System.nanoTime();

    beforeCheck(record);
    Result result = executePolicyImpl(record);
    afterCheck(result, startTime);

    return result;
  }

  /**
   * Called before check is run.
   * @param record
   */
  public void beforeCheck(Object record) {
    Instrumented.markMeter(this.recordsMeter);
  }

  /**
   * Called after check is run.
   * @param result result from check.
   * @param startTimeNanos start time of check.
   */
  public void afterCheck(Result result, long startTimeNanos) {
    switch (result) {
      case FAILED:
        Instrumented.markMeter(this.failedRecordsMeter);
        break;
      case PASSED:
        Instrumented.markMeter(this.passedRecordsMeter);
        break;
      default:
    }

    Instrumented.updateTimer(this.policyTimer, System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Subclasses should implement this instead of {@link gobblin.qualitychecker.row.RowLevelPolicy#executePolicy}.
   */
  public abstract Result executePolicyImpl(Object record);

  @Override
  public void close()
      throws IOException {
    closer.close();
  }

  @Override
  public MetricContext getMetricContext() {
    return this.metricContext;
  }
}
