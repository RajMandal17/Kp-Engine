package com.gitbitex.marketdata.repository;

import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.matchingengine.TradeEmit;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.diagnostics.logging.Logger;
import lombok.extern.flogger.Flogger;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


@Component
public class TradeEmitRepository {
    private final MongoCollection<TradeEmit> collection;

    public TradeEmitRepository(MongoDatabase database) {
        this.collection = database.getCollection(TradeEmit.class.getSimpleName().toLowerCase(), TradeEmit.class);
        this.collection.createIndex(Indexes.descending("productId", "sequence"));
    }

//

    public TradeEmit findByTradeId(String tradeId) {
        return this.collection
                .find(Filters.eq("_id", tradeId))
                .first();
    }
    //
    public List<TradeEmit> findByProductId(String productId, int limit) {
        return this.collection.find(Filters.eq("productId", productId))
                .sort(Sorts.descending("sequence"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public List<TradeEmit> findByProductId(String tradeEmitId) {
        return this.collection.find(Filters.eq("tradeEmitId", tradeEmitId))
                .sort(Sorts.descending("sequence"))
                .into(new ArrayList<>());
    }

    public List<TradeEmit> findAllTrade(String status, int limit) {
        Bson filter = Filters.eq("status", status);
        return this.collection.find(filter)
                .sort(Sorts.descending("sequence"))
          //      .limit(limit)
                .into(new ArrayList<>());
    }
    public List<TradeEmit> findTradeByOrderId(String orderId, int limit) {
        Bson filter = Filters.or(
                Filters.eq("takerOrderId", orderId),
                Filters.eq("makerOrderId", orderId)
        );

        return this.collection.find(filter)
                .sort(Sorts.descending("sequence"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public void saveAll(Collection<TradeEmit> trades) {
        List<WriteModel<TradeEmit>> writeModels = new ArrayList<>();
        for (TradeEmit item : trades) {
            item.setStatus("0");
            Bson filter = Filters.eq("_id", item.getProductId());
            WriteModel<TradeEmit> writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            writeModels.add(writeModel);
        }
        collection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
    }


//    public void save(TradeEmit trade) {
//
//        Bson filter = Filters.eq("_id", trade.getProductId());
//        ReplaceOneModel<TradeEmit> writeModel = new ReplaceOneModel<>(filter, trade, new ReplaceOptions().upsert(true));
//        collection.replaceOne(filter, trade, new ReplaceOptions().upsert(true));
//    }

    public void save(TradeEmit trade) {
        // Assuming 'collection' is an instance of MongoDB's MongoCollection<TradeEmit>
        InsertOneModel<TradeEmit> writeModel = new InsertOneModel<>(trade);
        collection.bulkWrite(Collections.singletonList(writeModel));
    }


    public void updateStatusByTradeEmitId(String tradeEmitId, String newStatus) {
        try {
            Bson filter = Filters.eq("tradeEmitId", tradeEmitId);
            Bson update = Updates.set("status", newStatus);
            UpdateOptions options = new UpdateOptions().upsert(false);

            UpdateResult result = collection.updateOne(filter, update, options);

            if (result.getModifiedCount() == 0) {


            }
        } catch (MongoException e) {

            e.printStackTrace();
        }
    }
}
