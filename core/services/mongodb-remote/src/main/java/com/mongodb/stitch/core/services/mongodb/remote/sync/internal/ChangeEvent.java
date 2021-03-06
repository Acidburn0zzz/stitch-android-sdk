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


package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;
import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer.DOCUMENT_VERSION_FIELD;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

// TODO: Should there be a local and remote type for the pending part?
public final class ChangeEvent<DocumentT> {
  private final BsonDocument id; // Metadata related to the operation (the resumeToken).
  private final OperationType operationType;
  private final DocumentT fullDocument;
  private final MongoNamespace ns;
  private final BsonDocument documentKey;
  private final UpdateDescription updateDescription;
  private final boolean hasUncommittedWrites;

  ChangeEvent(
      final BsonDocument id,
      final OperationType operationType,
      final DocumentT fullDocument,
      final MongoNamespace ns,
      final BsonDocument documentKey,
      final UpdateDescription updateDescription,
      final boolean hasUncommittedWrites
  ) {
    this.id = id;
    this.operationType = operationType;
    this.fullDocument = fullDocument;
    this.ns = ns;
    this.documentKey = documentKey;
    this.updateDescription = updateDescription == null
        ? new UpdateDescription(null, null) : updateDescription;
    this.hasUncommittedWrites = hasUncommittedWrites;
  }

  public BsonDocument getId() {
    return id;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public DocumentT getFullDocument() {
    return fullDocument;
  }

  public MongoNamespace getNamespace() {
    return ns;
  }

  public BsonDocument getDocumentKey() {
    return documentKey;
  }

  public UpdateDescription getUpdateDescription() {
    return updateDescription;
  }

  public boolean hasUncommittedWrites() {
    return hasUncommittedWrites;
  }

  public enum OperationType {
    INSERT, DELETE, REPLACE, UPDATE, UNKNOWN;

    static OperationType fromRemote(final String type) {
      switch (type) {
        case "insert":
          return INSERT;
        case "delete":
          return DELETE;
        case "replace":
          return REPLACE;
        case "update":
          return UPDATE;
        default:
          return UNKNOWN;
      }
    }

    String toRemote() {
      switch (this) {
        case INSERT:
          return "insert";
        case DELETE:
          return "delete";
        case REPLACE:
          return "replace";
        case UPDATE:
          return "update";
        default:
          return "unknown";
      }
    }
  }

  public static final class UpdateDescription {
    private final BsonDocument updatedFields;
    private final Collection<String> removedFields;

    UpdateDescription(
        final BsonDocument updatedFields,
        final Collection<String> removedFields
    ) {
      this.updatedFields = updatedFields == null ? new BsonDocument() : updatedFields;
      this.removedFields = removedFields == null ? Collections.<String>emptyList() : removedFields;
    }

    public BsonDocument getUpdatedFields() {
      return updatedFields;
    }

    public Collection<String> getRemovedFields() {
      return removedFields;
    }

    /**
     * Convert this update description to an update document.
     * @return an update document with the appropriate $set and $unset
     *         documents
     */
    BsonDocument toUpdateDocument() {
      final List<BsonElement> unsets = new ArrayList<>();
      for (final String removedField : this.removedFields) {
        unsets.add(new BsonElement(removedField, new BsonBoolean(true)));
      }
      final BsonDocument updateDocument = new BsonDocument();

      if (this.updatedFields.size() > 0) {
        updateDocument.append("$set", this.updatedFields);
      }

      if (unsets.size() > 0) {
        updateDocument.append("$unset", new BsonDocument(unsets));
      }

      return updateDocument;
    }

    /**
     * Find the diff between two documents.
     *
     * NOTE: This does not do a full diff on {@link BsonArray}. If there is
     * an inequality between the old and new array, the old array will
     * simply be replaced by the new one.
     *
     * @param beforeDocument original document
     * @param afterDocument document to diff on
     * @param onKey the key for our depth level
     * @param updatedFields contiguous document of updated fields,
     *                      nested or otherwise
     * @param removedFields contiguous list of removedFields,
     *                      nested or otherwise
     * @return a description of the updated fields and removed keys between
     *         the documents
     */
    private static UpdateDescription diff(final BsonDocument beforeDocument,
                                          final BsonDocument afterDocument,
                                          final @Nullable String onKey,
                                          final BsonDocument updatedFields,
                                          final List<String> removedFields) {
      // for each key in this document...
      for (final Map.Entry<String, BsonValue> entry: beforeDocument.entrySet()) {
        final String key = entry.getKey();
        // don't worry about the _id or version field for now
        if (key.equals("_id") || key.equals(DOCUMENT_VERSION_FIELD)) {
          continue;
        }
        final BsonValue oldValue = entry.getValue();

        final String actualKey = onKey == null ? key : String.format("%s.%s", onKey, key);
        // if the key exists in the other document AND both are BsonDocuments
        // diff the documents recursively, carrying over the keys to keep
        // updatedFields and removedFields flat.
        // this will allow us to reference whole objects as well as nested
        // properties.
        // else if the key does not exist, the key has been removed.
        if (afterDocument.containsKey(key)) {
          final BsonValue newValue = afterDocument.get(key);
          if (oldValue instanceof BsonDocument && newValue instanceof BsonDocument) {
            diff((BsonDocument) oldValue,
                (BsonDocument) newValue,
                actualKey,
                updatedFields,
                removedFields);
          } else if (!oldValue.equals(newValue)) {
            updatedFields.put(actualKey, newValue);
          }
        } else {
          removedFields.add(actualKey);
        }
      }

      // for each key in the other document...
      for (final Map.Entry<String, BsonValue> entry: afterDocument.entrySet()) {
        final String key = entry.getKey();
        // don't worry about the _id or version field for now
        if (key.equals("_id") || key.equals(DOCUMENT_VERSION_FIELD)) {
          continue;
        }

        final BsonValue newValue = entry.getValue();
        // if the key is not in the this document,
        // it is a new key with a new value.
        // updatedFields will included keys that must
        // be newly created.
        final String actualKey = onKey == null ? key : String.format("%s.%s", onKey, key);;
        if (!beforeDocument.containsKey(key)) {
          updatedFields.put(actualKey, newValue);
        }
      }

      return new UpdateDescription(updatedFields, removedFields);
    }

    /**
     * Find the diff between two documents.
     *
     * NOTE: This does not do a full diff on [BsonArray]. If there is
     * an inequality between the old and new array, the old array will
     * simply be replaced by the new one.
     *
     * @param beforeDocument original document
     * @param afterDocument document to diff on
     * @return a description of the updated fields and removed keys between
     *         the documents
     */
    static UpdateDescription diff(final BsonDocument beforeDocument,
                                  final BsonDocument afterDocument) {
      return UpdateDescription.diff(
          beforeDocument,
          afterDocument,
          null,
          new BsonDocument(),
          new ArrayList<>()
      );
    }
  }

  static BsonDocument toBsonDocument(final ChangeEvent<BsonDocument> value) {
    final BsonDocument asDoc = new BsonDocument();
    asDoc.put(ChangeEventCoder.Fields.ID_FIELD, value.getId());
    asDoc.put(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD,
        new BsonString(value.getOperationType().toRemote()));
    final BsonDocument nsDoc = new BsonDocument();
    nsDoc.put(ChangeEventCoder.Fields.NS_DB_FIELD,
        new BsonString(value.getNamespace().getDatabaseName()));
    nsDoc.put(ChangeEventCoder.Fields.NS_COLL_FIELD,
        new BsonString(value.getNamespace().getCollectionName()));
    asDoc.put(ChangeEventCoder.Fields.NS_FIELD, nsDoc);
    asDoc.put(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD, value.getDocumentKey());
    if (value.getFullDocument() != null) {
      asDoc.put(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD, value.getFullDocument());
    }
    if (value.getUpdateDescription() != null) {
      final BsonDocument updateDescDoc = new BsonDocument();
      updateDescDoc.put(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD,
          value.getUpdateDescription().getUpdatedFields());

      final BsonArray removedFields = new BsonArray();
      for (final String field : value.getUpdateDescription().getRemovedFields()) {
        removedFields.add(new BsonString(field));
      }
      updateDescDoc.put(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD,
          removedFields);
      asDoc.put(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD, updateDescDoc);
    }
    asDoc.put(ChangeEventCoder.Fields.WRITE_PENDING_FIELD,
        new BsonBoolean(value.hasUncommittedWrites));
    return asDoc;
  }

  static ChangeEvent<BsonDocument> fromBsonDocument(final BsonDocument document) {
    keyPresent(ChangeEventCoder.Fields.ID_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.NS_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD, document);

    final BsonDocument nsDoc = document.getDocument(ChangeEventCoder.Fields.NS_FIELD);
    final ChangeEvent.UpdateDescription updateDescription;
    if (document.containsKey(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD)) {
      final BsonDocument updateDescDoc =
          document.getDocument(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD);
      keyPresent(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD, updateDescDoc);
      keyPresent(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD, updateDescDoc);

      final BsonArray removedFieldsArr =
          updateDescDoc.getArray(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD);
      final Collection<String> removedFields = new ArrayList<>(removedFieldsArr.size());
      for (final BsonValue field : removedFieldsArr) {
        removedFields.add(field.asString().getValue());
      }
      updateDescription = new ChangeEvent.UpdateDescription(updateDescDoc.getDocument(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD),
          removedFields);
    } else {
      updateDescription = null;
    }

    final BsonDocument fullDocument;
    if (document.containsKey(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD)) {
      final BsonValue fdVal = document.get(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD);
      if (fdVal.isDocument()) {
        fullDocument = fdVal.asDocument();
      } else {
        fullDocument = null;
      }
    } else {
      fullDocument = null;
    }

    return new ChangeEvent<>(
        document.getDocument(ChangeEventCoder.Fields.ID_FIELD),
        ChangeEvent.OperationType.fromRemote(
            document.getString(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD).getValue()),
        fullDocument,
        new MongoNamespace(
            nsDoc.getString(ChangeEventCoder.Fields.NS_DB_FIELD).getValue(),
            nsDoc.getString(ChangeEventCoder.Fields.NS_COLL_FIELD).getValue()),
        document.getDocument(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD),
        updateDescription,
        document.getBoolean(
            ChangeEventCoder.Fields.WRITE_PENDING_FIELD,
            BsonBoolean.FALSE).getValue());
  }

  static final ChangeEventsDecoder changeEventsDecoder = new ChangeEventsDecoder();

  private static class ChangeEventsDecoder
      implements Decoder<List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>>> {
    public List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final LinkedHashMap<BsonValue, ChangeEvent<BsonDocument>> latestEvents =
          new LinkedHashMap<>();
      reader.readStartArray();
      while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        final ChangeEvent<BsonDocument> event = changeEventCoder.decode(reader, decoderContext);
        final BsonValue docId = event.getDocumentKey().get("_id");
        if (latestEvents.containsKey(docId)) {
          latestEvents.remove(docId);
        }
        latestEvents.put(docId, event);
      }
      reader.readEndArray();
      return new ArrayList<>(latestEvents.entrySet());
    }
  }

  static final ChangeEventCoder changeEventCoder = new ChangeEventCoder();

  static class ChangeEventCoder implements Codec<ChangeEvent<BsonDocument>> {
    public ChangeEvent<BsonDocument> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }

    @Override
    public void encode(
        final BsonWriter writer,
        final ChangeEvent<BsonDocument> value,
        final EncoderContext encoderContext
    ) {
      new BsonDocumentCodec().encode(writer, toBsonDocument(value), encoderContext);
    }

    @Override
    public Class<ChangeEvent<BsonDocument>> getEncoderClass() {
      return null;
    }

    private static final class Fields {
      static final String ID_FIELD = "_id";
      static final String OPERATION_TYPE_FIELD = "operationType";
      static final String FULL_DOCUMENT_FIELD = "fullDocument";
      static final String DOCUMENT_KEY_FIELD = "documentKey";

      static final String NS_FIELD = "ns";
      static final String NS_DB_FIELD = "db";
      static final String NS_COLL_FIELD = "coll";

      static final String UPDATE_DESCRIPTION_FIELD = "updateDescription";
      static final String UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD = "updatedFields";
      static final String UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD = "removedFields";

      static final String WRITE_PENDING_FIELD = "writePending";
    }
  }

  /**
   * Generates a change event for a local insert of the given document in the given namespace.
   *
   * @param namespace the namespace where the document was inserted.
   * @param document the document that was inserted.
   * @return a change event for a local insert of the given document in the given namespace.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalInsert(
      final MongoNamespace namespace,
      final BsonDocument document,
      final boolean writePending
  ) {
    final BsonValue docId = BsonUtils.getDocumentId(document);
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.INSERT,
        document,
        namespace,
        new BsonDocument("_id", docId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local update of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param update the update specifier.
   * @return a change event for a local update of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalUpdate(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final UpdateDescription update,
      final BsonDocument fullDocumentAfterUpdate,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.UPDATE,
        fullDocumentAfterUpdate,
        namespace,
        new BsonDocument("_id", documentId),
        update,
        writePending);
  }

  /**
   * Generates a change event for a local replacement of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param document the replacement document.
   * @return a change event for a local replacement of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalReplace(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument document,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.REPLACE,
        document,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local deletion of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @return a change event for a local deletion of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalDelete(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.DELETE,
        null,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Transforms a {@link ChangeEvent} into one that can be used by a user defined conflict resolver.
   * @param event the event to transform.
   * @param codec the codec to use to transform any documents specific to the collection.
   * @return the transformed {@link ChangeEvent}
   */
  static ChangeEvent transformChangeEventForUser(
      final ChangeEvent<BsonDocument> event,
      final Codec codec
  ) {
    return new ChangeEvent<>(
        event.getId(),
        event.getOperationType(),
        event.getFullDocument() == null ? null : codec
            .decode(event.getFullDocument().asBsonReader(), DecoderContext.builder().build()),
        event.getNamespace(),
        event.getDocumentKey(),
        event.getUpdateDescription(),
        event.hasUncommittedWrites());
  }
}
