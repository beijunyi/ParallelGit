package com.beijunyi.parallelgit.utils.io;

import java.io.IOException;
import javax.annotation.Nonnull;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectInserter.Formatter;
import org.eclipse.jgit.lib.ObjectReader;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

public class BlobSnapshot extends ObjectSnapshot<byte[]> {

  private BlobSnapshot(ObjectId id, byte[] bytes) {
    super(id, bytes);
  }

  @Nonnull
  public static BlobSnapshot load(ObjectId id, ObjectReader reader) throws IOException {
    return new BlobSnapshot(id, reader.open(id).getBytes());
  }

  @Nonnull
  public static BlobSnapshot capture(byte[] bytes) {
    return new BlobSnapshot(computeBlobId(bytes), bytes);
  }

  @Nonnull
  @Override
  public ObjectId save(ObjectInserter inserter) throws IOException {
    return inserter.insert(OBJ_BLOB, getData());
  }

  @Nonnull
  private static ObjectId computeBlobId(byte[] data) {
    return new Formatter().idFor(OBJ_BLOB, data);
  }

}
