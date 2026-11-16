package com.traffic.trafficservice.repository;

import com.traffic.trafficservice.domain.TrafficAggregate;
import com.traffic.trafficservice.domain.TrafficReading;
import java.time.Instant;
import java.util.List;

public interface ReadingRepository {
    InsertResult insertIgnoringDuplicates(List<TrafficReading> readings);
    List<TrafficReading> findVehicleTrajectory(String vehicleId, Instant from, Instant to);
    List<TrafficAggregate> aggregate(Instant from, Instant to);

    record InsertResult(List<TrafficReading> inserted, int duplicates) { }
}
