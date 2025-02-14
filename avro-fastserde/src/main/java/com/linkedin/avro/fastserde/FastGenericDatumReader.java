package com.linkedin.avro.fastserde;

import java.io.IOException;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generic {@link DatumReader} backed by generated deserialization code.
 */
public class FastGenericDatumReader<T> implements DatumReader<T> {
  private static final Logger LOGGER = LoggerFactory.getLogger(FastGenericDatumReader.class);

  private Schema writerSchema;
  private Schema readerSchema;
  private FastSerdeCache cache;

  private FastDeserializer<T> cachedFastDeserializer;

  public FastGenericDatumReader(Schema schema) {
    this(schema, schema);
  }

  public FastGenericDatumReader(Schema writerSchema, Schema readerSchema) {
    this(writerSchema, readerSchema, FastSerdeCache.getDefaultInstance());
  }

  public FastGenericDatumReader(Schema schema, FastSerdeCache cache) {
    this(schema, schema, cache);
  }

  public FastGenericDatumReader(Schema writerSchema, Schema readerSchema, FastSerdeCache cache) {
    this.writerSchema = writerSchema;
    this.readerSchema = readerSchema;
    this.cache = cache != null ? cache : FastSerdeCache.getDefaultInstance();

    if (!Utils.isSupportedAvroVersionsForDeserializer()) {
      this.cachedFastDeserializer = getRegularAvroImpl(writerSchema, readerSchema);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Current avro version: " + Utils.getRuntimeAvroVersion() + " is not supported, and only the following"
                + " versions are supported: " + Utils.getAvroVersionsSupportedForDeserializer()
                + ", so skip the FastDeserializer generation");
      }
    } else if (!FastSerdeCache.isSupportedForFastDeserializer(readerSchema.getType())) {
      // For unsupported schema type, we won't try to fetch it from FastSerdeCache since it is inefficient.
      this.cachedFastDeserializer = getRegularAvroImpl(writerSchema, readerSchema);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Skip the FastGenericDeserializer generation since read schema type: " + readerSchema.getType()
            + " is not supported");
      }
    }
  }

  @Override
  public void setSchema(Schema schema) {
    if (writerSchema == null) {
      writerSchema = schema;
    }

    if (readerSchema == null) {
      readerSchema = writerSchema;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(T reuse, Decoder in) throws IOException {
    FastDeserializer<T> fastDeserializer = null;

    if (cachedFastDeserializer != null) {
      fastDeserializer = cachedFastDeserializer;
    } else {
      fastDeserializer = getFastDeserializerFromCache(cache, writerSchema, readerSchema);
      if (!isFastDeserializer(fastDeserializer)) {
        // don't cache
      } else {
        cachedFastDeserializer = fastDeserializer;
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("FastGenericDeserializer was generated and cached for reader schema: ["
              + readerSchema + "], writer schema: [" + writerSchema + "]");
        }
      }
    }

    return fastDeserializer.deserialize(reuse, in);
  }

  protected FastDeserializer<T> getFastDeserializerFromCache(FastSerdeCache fastSerdeCache, Schema writerSchema,
      Schema readerSchema) {
    return (FastDeserializer<T>) fastSerdeCache.getFastGenericDeserializer(writerSchema, readerSchema);
  }

  protected FastDeserializer<T> getRegularAvroImpl(Schema writerSchema, Schema readerSchema) {
    return new FastSerdeCache.FastDeserializerWithAvroGenericImpl<>(writerSchema, readerSchema);
  }

  private static boolean isFastDeserializer(FastDeserializer deserializer) {
    return !(deserializer instanceof FastSerdeCache.FastDeserializerWithAvroSpecificImpl
        || deserializer instanceof FastSerdeCache.FastDeserializerWithAvroGenericImpl);
  }

  /**
   * Return a flag to indicate whether fast deserializer is being used or not.
   * @return
   */
  public boolean isFastDeserializerUsed() {
    if (cachedFastDeserializer == null) {
      return false;
    }
    return isFastDeserializer(cachedFastDeserializer);
  }
}
