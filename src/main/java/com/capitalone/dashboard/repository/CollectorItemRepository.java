package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.CollectorItem;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.querydsl.QueryDslPredicateExecutor;


public interface CollectorItemRepository extends BaseCollectorItemRepository<CollectorItem>, QueryDslPredicateExecutor<CollectorItem> {

    @Query("{'options.orgName' : ?0, 'options.projectName' : ?1, 'options.productName' : ?2}")
    CollectorItem findByOrgNameAndProjectNameAndProductName(String var1, String var2, String var3);


}
