package com.capitalone.dashboard.repository;


import com.capitalone.dashboard.model.WhiteSourceComponent;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WhiteSourceCustomComponentRepository {

    private final MongoTemplate template;

    @Autowired
    public WhiteSourceCustomComponentRepository(MongoTemplate template) {
        this.template = template;
    }

    public List<WhiteSourceComponent> findCollectorItemsByUniqueOptions(ObjectId id, Map<String, Object> allOptions, Map<String, Object> uniqueOptions, Map<String, Object> uniqueOptionsFromCollector) {
        Criteria c = Criteria.where("collectorId").is(id);
        uniqueOptions.values().removeIf(d -> d.equals(null) || ((d instanceof String) && StringUtils.isEmpty((String) d)));
        for (Map.Entry<String, Object> e : allOptions.entrySet()) {
            if (uniqueOptionsFromCollector.containsKey(e.getKey())) {
                c = getCriteria(uniqueOptions, c, e);
            }
        }
        List<WhiteSourceComponent> items = template.find(new Query(c), WhiteSourceComponent.class);
        return items;
    }

    private Criteria getCriteria(Map<String, Object> options, Criteria c, Map.Entry<String, Object> e) {
        Criteria criteria = c;
        criteria = criteria.and("options." + e.getKey()).is(options.get(e.getKey()));
        return criteria;
    }
}