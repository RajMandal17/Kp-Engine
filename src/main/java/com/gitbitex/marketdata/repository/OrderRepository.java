package com.gitbitex.marketdata.repository;

import com.gitbitex.enums.OrderSide;
import com.gitbitex.enums.OrderStatus;
import com.gitbitex.marketdata.entity.Order;
import com.gitbitex.openapi.model.PagedList;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class OrderRepository {
    private final MongoCollection<Order> collection;

    @Autowired
    private StringRedisTemplate redisTemplate;
    public OrderRepository(MongoDatabase database) {
        this.collection = database.getCollection(Order.class.getSimpleName().toLowerCase(), Order.class);
        this.collection.createIndex(Indexes.descending("userId", "productId", "sequence"));
    }

    public Order findByOrderId(String orderId) {
        return this.collection
                .find(Filters.eq("_id", orderId))
                .first();
    }

    public PagedList<Order> findAll( OrderStatus status,  int pageIndex,
                                    int pageSize) {
        Bson filter = Filters.empty();

        if (status != null) {
            filter = Filters.and(Filters.eq("status", status.name()), filter);
        }


        long count = this.collection.countDocuments(filter);
        List<Order> orders = this.collection
                .find(filter)
                .sort(Sorts.descending("sequence"))
                .skip(pageIndex - 1)
                .limit(pageSize)
                .into(new ArrayList<>());
        return new PagedList<>(orders, count);
    }

    public void saveAll(Collection<Order> orders) {
        List<WriteModel<Order>> writeModels = new ArrayList<>();
        for (Order item : orders) {
            Bson filter = Filters.eq("_id", item.getId());
            WriteModel<Order> writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            writeModels.add(writeModel);
        }
        collection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
    }



    public void save(Order order) {
        // Find the maximum sequence value in the collection
        Bson maxSequenceFilter = Aggregates.group(null, Accumulators.max("maxSequence", "$sequence"));
        Document maxSequenceResult = collection.aggregate(Collections.singletonList(maxSequenceFilter), Document.class).first();

        long nextSequence = 1L; // Default value if no orders exist yet

        if (maxSequenceResult != null) {
            // If there are orders, get the max sequence and increment it for the new order
            nextSequence = maxSequenceResult.getLong("maxSequence") + 1;
        }

        // Set the sequence for the new order
        order.setSequence(nextSequence);

        // Save the order to the collection
        collection.replaceOne(Filters.eq("_id", order.getId()), order, new ReplaceOptions().upsert(true));
    }

    public UpdateResult updateOrderStatus(String orderId, OrderStatus newStatus) {
        Bson filter = Filters.eq("_id", orderId);
        Bson update = Updates.set("status", newStatus.name());
        return this.collection.updateOne(filter, update);
    }

    public PagedList<Order> findAll(String userId, String productId, OrderStatus status, OrderSide side, int pageIndex,
                                    int pageSize) {
        Bson filter = Filters.empty();
        if (userId != null) {
            filter = Filters.and(Filters.eq("userId", userId), filter);
        }
        if (productId != null) {
            filter = Filters.and(Filters.eq("productId", productId), filter);
        }
        if (status != null) {
            filter = Filters.and(Filters.eq("status", status.name()), filter);
        }
        if (side != null) {
            filter = Filters.and(Filters.eq("side", side.name()), filter);
        }

        long count = this.collection.countDocuments(filter);
        List<Order> orders = this.collection
                .find(filter)
                .sort(Sorts.descending("sequence"))
                .skip(pageIndex - 1)
                .limit(pageSize)
                .into(new ArrayList<>());
        return new PagedList<>(orders, count);
    }



    public String getOrderBookSnapshot(String productId) {
        String redisKey = productId + ".l2_batch_order_book";
        return redisTemplate.opsForValue().get(redisKey);
    }
}