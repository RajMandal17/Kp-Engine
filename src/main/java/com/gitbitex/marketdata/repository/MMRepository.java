package com.gitbitex.marketdata.repository;

import com.gitbitex.marketdata.entity.mm;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
@Component
public class MMRepository {

    private final MongoCollection<mm> mongoCollection;

    public MMRepository(MongoDatabase database) {
        this.mongoCollection = database.getCollection(mm.class.getSimpleName().toLowerCase(), mm.class);
    }

    public mm findById(String id) {
        return this.mongoCollection.find(Filters.eq("_id", id)).first();
    }

    public List<mm> findAll() {
        return this.mongoCollection.find().into(new ArrayList<>());
    }

    public void save(mm product) {
        List<WriteModel<mm>> writeModels = new ArrayList<>();
        Bson filter = Filters.eq("_id", product.getId());
        WriteModel<mm> writeModel = new ReplaceOneModel<>(filter, product, new ReplaceOptions().upsert(true));
        writeModels.add(writeModel);
        this.mongoCollection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false));

    }
    public void update(mm product)  {
        List<WriteModel<mm>> writeModels = new ArrayList<>();

        // Set the filter to find the document by its ID
        Bson filter = Filters.eq("_id", product.getId());

        // Set the update operations for each field
        Bson update = Updates.combine(
                Updates.set("updatedAt", product.getUpdatedAt()),
                Updates.set("baseCurrency", product.getBaseCurrency()),
                Updates.set("quoteCurrency", product.getQuoteCurrency()),
                Updates.set("baseMinSize", product.getBaseMinSize()),
                Updates.set("baseMaxSize", product.getBaseMaxSize()),
                Updates.set("quoteMinSize", product.getQuoteMinSize()),
                Updates.set("quoteMaxSize", product.getQuoteMaxSize()),
                Updates.set("baseScale", product.getBaseScale()),
                Updates.set("quoteScale", product.getQuoteScale()),
                Updates.set("quoteIncrement", product.getQuoteIncrement()),

                Updates.set("orderSizeMin", product.getOrderSizeMin()),
                Updates.set("orderSizeMax", product.getOrderSizeMax()),
                Updates.set("spread", product.getSpread())
        );
        // Create an UpdateOneModel with the specified filter, update, and options
        UpdateOneModel<mm> updateModel = new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true));

        // Add the UpdateOneModel to the list
        writeModels.add(updateModel);

        // Execute the bulk write operation
        this.mongoCollection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
    }
}
