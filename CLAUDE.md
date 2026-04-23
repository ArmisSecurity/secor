# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Secor is a Pinterest service that persists Kafka logs to cloud storage (Amazon S3, Google Cloud Storage, Microsoft Azure Blob Storage, OpenStack Swift). It provides strong consistency guarantees, fault tolerance, and horizontal scalability for log persistence.

## Build Commands

```bash
# Build (uses kafka-0.10.2.0 profile by default)
mvn package

# Build with specific Kafka version
mvn -Pkafka-2.0.0 package    # Kafka 2.0.0 (enables Kafka headers support)

# Run unit tests
mvn test
# Or via Makefile:
make unit

# Run integration tests (requires Docker)
make integration

# Full test suite (build + unit + integration)
make test

# Test with specific Kafka version
MVN_PROFILE=kafka-2.0.0 make test

# Clean
make clean
```

## Architecture

### Consumer Groups

Secor uses two primary consumer group patterns:
- **Backup Group**: Persists all messages verbatim without parsing. Simple, performant, provides raw data backup for reprocessing.
- **Partition Group**: Parses messages to extract timestamps/partitions, groups output by date for Hive consumption.

### Core Components (src/main/java/com/pinterest/secor/)

| Package | Purpose |
|---------|---------|
| `consumer/` | Main Consumer thread coordinating read/write/upload cycle |
| `reader/` | MessageReader implementations for Kafka message consumption |
| `parser/` | MessageParser implementations for extracting partitions from messages |
| `writer/` | MessageWriter implementations for local file writing |
| `uploader/` | Uploaders for cloud storage (S3, GCS, Azure, Swift) |
| `common/` | SecorConfig, FileRegistry, OffsetTracker |
| `io/impl/` | File format implementations (Sequence, Parquet, ORC, DelimitedText) |

### Entry Points (src/main/java/com/pinterest/secor/main/)

- `ConsumerMain` - Primary Secor consumer service
- `LogFilePrinterMain` - Display stored log file contents
- `LogFileVerifierMain` - Verify log file consistency
- `PartitionFinalizerMain` - Write _SUCCESS markers, optionally register with Hive
- `ProgressMonitorMain` - Export offset consumption lags to monitoring

### Data Flow

1. MessageReader reads from Kafka
2. MessageParser extracts partitions/timestamps
3. MessageWriter writes to local files
4. Uploader moves files to cloud storage when upload policy triggers (size or time threshold)
5. Offsets committed to ZooKeeper after successful upload

### Offset Management

Secor tracks two offset types per topic/partition:
- `last_seen_offset` - Greatest offset seen but not committed
- `last_committed_offset` - Greatest offset persisted to cloud storage

ZooKeeper locking prevents race conditions during uploads. On rebalance, local files are cleaned up to maintain exactly-once semantics.

## Configuration

Main config: `src/main/config/secor.common.properties`

Key properties:
- `secor.message.parser.class` - Parser implementation class
- `secor.file.reader.writer.factory` - Output format factory
- `cloud.service` - Cloud provider (S3, GS, Swift, Azure)
- `secor.max.file.size.bytes` - Size-based upload threshold
- `secor.max.file.age.seconds` - Time-based upload threshold

## Extending Secor

### Custom Message Parser

Extend `MessageParser` or `TimestampedMessageParser`:
```java
public class MyParser extends TimestampedMessageParser {
    public long extractTimestampMillis(Message message) { ... }
}
```
Set via `secor.message.parser.class=com.example.MyParser`

### Output Formats

Set `secor.file.reader.writer.factory` to:
- `SequenceFileReaderWriterFactory` - Binary key-value (default)
- `DelimitedTextFileReaderWriterFactory` - Line-delimited text
- `JsonORCFileReaderWriterFactory` - ORC columnar
- `ProtobufParquetFileReaderWriterFactory` - Parquet for protobuf
- `ThriftParquetFileReaderWriterFactory` - Parquet for Thrift
- `AvroParquetFileReaderWriterFactory` - Parquet for Avro

## Testing

Test schemas located in:
- `src/test/thrift/` - Thrift definitions
- `src/test/protobuf/` - Protocol buffer definitions
- `src/test/avro/` - Avro schemas

Integration tests use Docker with local S3 (fakes3) and Kafka brokers.