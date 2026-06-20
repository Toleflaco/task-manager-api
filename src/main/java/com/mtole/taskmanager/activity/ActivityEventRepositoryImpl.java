package com.mtole.taskmanager.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

public class ActivityEventRepositoryImpl implements ActivityEventRepositoryCustom {


    private final MongoTemplate mongoTemplate;

    public ActivityEventRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<ActivityEvent> search(Long userId, ActivityEventFilter filter, Pageable pageable) {

        // 1. Criterio base obligatorio: siempre filtramos por userId
        Criteria criteria = Criteria.where("userId").is(userId);

        // 2. Criterio opcional: solo añadir si está presente
        if (filter.resourceType() != null && !filter.resourceType().isBlank()) {
            criteria.and("resourceType").is(filter.resourceType());
        }
        if (filter.from() != null && filter.to() != null) {
            criteria.and("timestamp").gte(filter.from()).lte(filter.to());
        } else if (filter.from() != null) {
            criteria.and("timestamp").gte(filter.from());
        } else if (filter.to() != null) {
            criteria.and("timestamp").lte(filter.to());
        }
        if (filter.resourceId() != null) {
            criteria.and("resourceId").is(filter.resourceId());
        }
        // 3. Construir Query con criteria + pageable (paginación + sort)
        Query query = new Query(criteria).with(pageable);

        // 4. Ejecutar
        List<ActivityEvent> content = mongoTemplate.find(query, ActivityEvent.class);

        // 5. Para Page necesitas el total: count con la misma criteria pero SIN pageable
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ActivityEvent.class);

        return new PageImpl<>(content, pageable, total);
    }
}
