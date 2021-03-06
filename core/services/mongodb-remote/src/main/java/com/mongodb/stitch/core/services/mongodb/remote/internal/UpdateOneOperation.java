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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;

class UpdateOneOperation implements Operation<RemoteUpdateResult> {

  private final MongoNamespace namespace;
  private final BsonDocument filter;
  private final BsonDocument update;
  private boolean upsert;

  UpdateOneOperation(
      final MongoNamespace namespace,
      final BsonDocument filter,
      final BsonDocument update
  ) {
    this.namespace = namespace;
    this.filter = filter;
    this.update = update;
  }

  public UpdateOneOperation upsert(final boolean upsert) {
    this.upsert = upsert;
    return this;
  }

  public RemoteUpdateResult execute(final CoreStitchServiceClient service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("query", filter);
    args.put("update", update);
    args.put("upsert", upsert);

    return service.callFunction(
        "updateOne",
        Collections.singletonList(args),
        ResultDecoders.updateResultDecoder);
  }
}
