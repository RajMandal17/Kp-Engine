package com.gitbitex.marketdata.repository;

import com.gitbitex.marketdata.entity.Sequence;
import com.gitbitex.marketdata.entity.mm;
import com.gitbitex.marketdata.entity.mmPrice;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class MmPriceRepository {

    private final MongoCollection<mmPrice> collection;

    private final MongoCollection<Sequence> sequenceCollection;

    public MmPriceRepository(MongoDatabase database) {
        this.collection = database.getCollection(mmPrice.class.getSimpleName().toLowerCase(), mmPrice.class);
        this.sequenceCollection = database.getCollection("sequence", Sequence.class);
    }

    public mmPrice findById(String productId) {
        return this.collection.find(Filters.eq("productId", productId)).first();
    }




    public void updateRandomPrice(String productId, BigDecimal newRandomPrice) {
        Bson filter = Filters.eq("productId", productId);
        Bson update = Updates.combine(
                Updates.set("randomPrice", newRandomPrice),
                Updates.set("timeInForce", LocalDateTime.now())
                // Add other updates as needed
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true);
        this.collection.findOneAndUpdate(filter, update, options);
    }



}
