/* (rank 239) copied from https://github.com/apache/zeppelin/blob/438f0b224a6321c5e309b950798069585ee02251/zeppelin-plugins/notebookrepo/mongo/src/main/java/org/apache/zeppelin/notebook/repo/OldMongoNotebookRepo.java
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.notebook.repo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.type;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.UpdateOptions;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.OldNoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Backend for storing Notebook on MongoDB
 */
public class OldMongoNotebookRepo implements OldNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(MongoNotebookRepo.class);

  private ZeppelinConfiguration conf;
  private MongoClient mongo;
  private MongoDatabase db;
  private MongoCollection<Document> coll;

  @Override
  public void init(ZeppelinConfiguration zConf) throws IOException {
    this.conf = zConf;

    mongo = new MongoClient(new MongoClientURI(conf.getMongoUri()));
    db = mongo.getDatabase(conf.getMongoDatabase());
    coll = db.getCollection(conf.getMongoCollection());

    if (conf.getMongoAutoimport()) {
      // import local notes into MongoDB
      insertFileSystemNotes();
    }
  }

  /**
   * If environment variable ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT is true,
   * this method will insert local notes into MongoDB on startup.
   * If a note already exists in MongoDB, skip it.
   */
  private void insertFileSystemNotes() throws IOException {
    LinkedList<Document> docs = new LinkedList<>(); // docs to be imported
    OldNotebookRepo vfsRepo = new OldVFSNotebookRepo();
    vfsRepo.init(this.conf);
    List<OldNoteInfo> infos =  vfsRepo.list(null);
    // collect notes to be imported
    for (OldNoteInfo info : infos) {
      Note note = vfsRepo.get(info.getId(), null);
      Document doc = noteToDocument(note);
      docs.add(doc);
    }

    /*
     * 'ordered(false)' option allows to proceed bulk inserting even though
     * there are duplicated documents. The duplicated documents will be skipped
     * and print a WARN log.
     */
    try {
      coll.insertMany(docs, new InsertManyOptions().ordered(false));
    } catch (MongoBulkWriteException e) {
      printDuplicatedException(e);  //print duplicated document warning log
    }

    vfsRepo.close();  // it does nothing for now but maybe in the future...
  }

  /**
   * MongoBulkWriteException contains error messages that inform
   * which documents were duplicated. This method catches those ID and print them.
   * @param e
   */
  private void printDuplicatedException(MongoBulkWriteException e) {
    List<BulkWriteError> errors = e.getWriteErrors();
    for (BulkWriteError error : errors) {
      String msg = error.getMessage();
      Pattern pattern = Pattern.compile("[A-Z0-9]{9}"); // regex for note ID
      Matcher matcher = pattern.matcher(msg);
      if (matcher.find()) { // if there were a note ID
        String noteId = matcher.group();
        LOG.warn("Note " + noteId + " not inserted since already exists in MongoDB");
      }
    }
  }

  @Override
  public List<OldNoteInfo> list(AuthenticationInfo subject) throws IOException {
    syncId();

    List<OldNoteInfo> infos = new LinkedList<>();
    MongoCursor<Document> cursor = coll.find().iterator();

    while (cursor.hasNext()) {
      Document doc = cursor.next();
      Note note = documentToNote(doc);
      OldNoteInfo info = new OldNoteInfo(note);
      infos.add(info);
    }

    cursor.close();

    return infos;
  }

  /**
   * Find documents of which type of _id is object ID, and change it to note ID.
   * Since updating _id field is not allowed, remove original documents and insert
   * new ones with string _id(note ID)
   */
  private void syncId() {
    // find documents whose id type is object id
    MongoCursor<Document> cursor =  coll.find(type("_id", BsonType.OBJECT_ID)).iterator();
    // if there is no such document, exit
    if (!cursor.hasNext())
      return;

    List<ObjectId> oldDocIds = new LinkedList<>();    // document ids need to update
    List<Document> updatedDocs = new LinkedList<>();  // new documents to be inserted

    while (cursor.hasNext()) {
      Document doc = cursor.next();
      // store original _id
      ObjectId oldId = doc.getObjectId("_id");
      oldDocIds.add(oldId);
      // store the document with string _id (note id)
      String noteId = doc.getString("id");
      doc.put("_id", noteId);
      updatedDocs.add(doc);
    }

    coll.insertMany(updatedDocs);
    coll.deleteMany(in("_id", oldDocIds));

    cursor.close();
  }

  /**
   * Convert document to note
   */
  private Note documentToNote(Document doc) throws IOException {
    // document to JSON
    String json = doc.toJson();
    // JSON to note
    return Note.fromJson(json);
  }

  /**
   * Convert note to document
   */
  private Document noteToDocument(Note note) {
    // note to JSON
    String json = note.toJson();
    // JSON to document
    Document doc = Document.parse(json);
    // set object id as note id
    doc.put("_id", note.getId());
    return doc;
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    Document doc = coll.find(eq("_id", noteId)).first();

    if (doc == null) {
      throw new IOException("Note " + noteId + "not found");
    }

    return documentToNote(doc);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    Document doc = noteToDocument(note);
    coll.replaceOne(eq("_id", note.getId()), doc, new UpdateOptions().upsert(true));
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    coll.deleteOne(eq("_id", noteId));
  }

  @Override
  public void close() {
    mongo.close();
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
  }

}
