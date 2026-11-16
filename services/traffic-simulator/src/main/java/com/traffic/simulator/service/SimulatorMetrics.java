package com.traffic.simulator.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimulatorMetrics {
    private final AtomicLong liveBatches = new AtomicLong();
    private final AtomicLong seededRows = new AtomicLong();

    public void recordLiveBatch() { liveBatches.incrementAndGet(); }
    public void recordSeededRows(long rows) { seededRows.addAndGet(rows); }
    public long liveBatches() { return liveBatches.get(); }
    public long seededRows() { return seededRows.get(); }
}
