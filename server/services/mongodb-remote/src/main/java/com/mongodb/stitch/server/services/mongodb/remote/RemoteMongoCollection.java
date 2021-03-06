/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.server.services.mongodb.remote;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import java.util.List;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * The RemoteMongoCollection interface.
 *
 * @param <DocumentT> The type that this collection will encode documents from and decode documents
 *                   to.
 */
public interface RemoteMongoCollection<DocumentT> {

  /**
   * Gets the namespace of this collection.
   *
   * @return the namespace
   */
  MongoNamespace getNamespace();

  /**
   * Get the class of documents stored in this collection.
   *
   * @return the class
   */
  Class<DocumentT> getDocumentClass();

  /**
   * Get the codec registry for the RemoteMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  CodecRegistry getCodecRegistry();

  /**
   * Create a new RemoteMongoCollection instance with a different default class to cast any
   * documents returned from the database into.
   *
   * @param clazz the default class to cast any documents returned from the database into.
   * @param <NewDocumentT> The type that the new collection will encode documents from and decode
   *                      documents to.
   * @return a new RemoteMongoCollection instance with the different default class
   */
  <NewDocumentT> RemoteMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz);

  /**
   * Create a new RemoteMongoCollection instance with a different codec registry.
   *
   * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the
   *                      collection.
   * @return a new RemoteMongoCollection instance with the different codec registry
   */
  RemoteMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry);

  /**
   * Counts the number of documents in the collection.
   *
   * @return the number of documents in the collection
   */
  long count();

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  long count(final Bson filter);

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  long count(final Bson filter, final RemoteCountOptions options);

  /**
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  RemoteFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> RemoteFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  RemoteFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> RemoteFindIterable<ResultT> find(final Bson filter, final Class<ResultT> resultClass);


  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  RemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline);

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> RemoteAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass);

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  RemoteInsertOneResult insertOne(final DocumentT document);

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  RemoteInsertManyResult insertMany(final List<? extends DocumentT> documents);

  /**
   * Removes at most one document from the collection that matches the given filter.  If no
   * documents match, the collection is not
   * modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  RemoteDeleteResult deleteOne(final Bson filter);

  /**
   * Removes all documents from the collection that match the given query filter.  If no documents
   * match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  RemoteDeleteResult deleteMany(final Bson filter);

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update one operation
   */
  RemoteUpdateResult updateOne(final Bson filter, final Bson update);

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  RemoteUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions);

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update many operation
   */
  RemoteUpdateResult updateMany(final Bson filter, final Bson update);

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  RemoteUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions);

  /**
   * A set of synchronization related operations at the collection level.
   *
   * @return set of sync operations for this collection
   */
  Sync<DocumentT> sync();
}
