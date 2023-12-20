package com.gitbitex.marketdata.repository;

import com.gitbitex.marketdata.entity.Trade;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class TradeRepository {
    private final MongoCollection<Trade> collection;

    public TradeRepository(MongoDatabase database) {
        this.collection = database.getCollection(Trade.class.getSimpleName().toLowerCase(), Trade.class);
        this.collection.createIndex(Indexes.descending("productId", "sequence"));
    }

//

    public Trade findByTradeId(String tradeId) {
        return this.collection
                .find(Filters.eq("_id", tradeId))
                .first();
    }
    //
    public List<Trade> findByProductId(String productId, int limit) {
        return this.collection.find(Filters.eq("productId", productId))
                .sort(Sorts.descending("sequence"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public List<Trade> findByProductId(String id) {
        return this.collection.find(Filters.eq("_id", id))
                .sort(Sorts.descending("sequence"))
                .into(new ArrayList<>());
    }

    public List<Trade> findAllTrade(String status, int limit) {
        Bson filter = Filters.eq("status", status);
        return this.collection.find(filter)
                .sort(Sorts.descending("sequence"))
                .limit(limit)
                .into(new ArrayList<>());
    }
    public long countTradesLast24Hours(String status) {
        Date twentyFourHoursAgo = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000));

        Bson filter = Filters.and(
                Filters.eq("status", status),
                Filters.gte("createdAt", twentyFourHoursAgo)
        );

        try {
            return this.collection.countDocuments(filter);
        } catch (MongoException e) {
            // Handle MongoDB exception (log, throw a custom exception, etc.)
            logger.error("MongoDB error in countTradesLast24Hours", e);
            throw new RuntimeException("Error counting trades in the last 24 hours", e);
        }
    }

    public List<Trade> findTradeByOrderId(String orderId, int limit) {
        Bson filter = Filters.or(
                Filters.eq("takerOrderId", orderId),
                Filters.eq("makerOrderId", orderId)
        );

        return this.collection.find(filter)
                .sort(Sorts.descending("sequence"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public void saveAll(Collection<Trade> trades) {
        List<WriteModel<Trade>> writeModels = new ArrayList<>();
        for (Trade item : trades) {
            item.setStatus("0");
            Bson filter = Filters.eq("_id", item.getId());
            WriteModel<Trade> writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            writeModels.add(writeModel);
        }
        collection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
    }


    public void save(Trade trade) {

        Bson filter = Filters.eq("_id", trade.getId());
        ReplaceOneModel<Trade> writeModel = new ReplaceOneModel<>(filter, trade, new ReplaceOptions().upsert(true));
        collection.replaceOne(filter, trade, new ReplaceOptions().upsert(true));
    }

}
