package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.WhiteSourceComponent;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;


public interface WhiteSourceComponentRepository extends BaseCollectorItemRepository<WhiteSourceComponent> {
    @Query(value="{ 'collectorId' : ?0, enabled: true}")
    List<WhiteSourceComponent> findEnabledComponents(ObjectId collectorId);

}
