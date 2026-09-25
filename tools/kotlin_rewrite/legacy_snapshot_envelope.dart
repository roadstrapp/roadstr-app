import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

const int legacySnapshotSchemaVersion = 1;

const int _formatVersion = 1;
const int _digestBytes = 32;
final Uint8List _magic = Uint8List.fromList(ascii.encode('RSTRMIG1'));

abstract final class LegacyEnvelopeLimits {
  static const int maxEntriesPerStore = 512;
  static const int maxKeyBytes = 256;
  static const int maxOrdinaryValueBytes = 4 * 1024 * 1024;
  static const int maxSecureValueBytes = 64 * 1024;
  static const int maxAssets = 256;
  static const int maxAssetPathBytes = 1024;
  static const int maxAssetSizeBytes = 4 * 1024 * 1024 * 1024;
  static const int maxEnvelopeBytes = 64 * 1024 * 1024;
}

class LegacyEnvelopeIdentity {
  const LegacyEnvelopeIdentity({
    required this.publicKeyHex,
    required this.flavor,
    required this.privateKeyHex,
  });

  final String? publicKeyHex;
  final String? flavor;
  final String? privateKeyHex;
}

class LegacyEnvelopeAsset {
  const LegacyEnvelopeAsset({
    required this.relativePath,
    required this.sizeBytes,
    required this.sha256,
  });

  final String relativePath;
  final int sizeBytes;
  final String sha256;
}

class LegacyEnvelopeSnapshot {
  LegacyEnvelopeSnapshot({
    required this.schemaVersion,
    required Map<String, String> ordinaryValues,
    required Map<String, String> secureValues,
    required this.identity,
    required List<LegacyEnvelopeAsset> assets,
  })  : ordinaryValues = Map.unmodifiable(ordinaryValues),
        secureValues = Map.unmodifiable(secureValues),
        assets = List.unmodifiable(assets);

  final int schemaVersion;
  final Map<String, String> ordinaryValues;
  final Map<String, String> secureValues;
  final LegacyEnvelopeIdentity identity;
  final List<LegacyEnvelopeAsset> assets;
}

class LegacyEnvelopeException implements Exception {
  const LegacyEnvelopeException(this.message);

  final String message;

  @override
  String toString() => 'LegacyEnvelopeException: $message';
}

/// Deterministic bridge format shared with `LegacySnapshotEnvelope.kt`.
///
/// The final SHA-256 detects accidental corruption only. The production
/// bridge must still read private app storage directly and native migration
/// must validate the decoded identity before writing anything.
abstract final class LegacySnapshotEnvelopeCodec {
  static Uint8List encode(LegacyEnvelopeSnapshot snapshot) {
    _ensure(
        snapshot.ordinaryValues.length <=
            LegacyEnvelopeLimits.maxEntriesPerStore,
        'Too many ordinary entries');
    _ensure(
        snapshot.secureValues.length <= LegacyEnvelopeLimits.maxEntriesPerStore,
        'Too many secure entries');
    _ensure(snapshot.assets.length <= LegacyEnvelopeLimits.maxAssets,
        'Too many assets');
    _ensure(
      snapshot.assets.map((asset) => asset.relativePath).toSet().length ==
          snapshot.assets.length,
      'Duplicate asset path',
    );

    final writer = _EnvelopeWriter();
    writer.writeBytes(_magic);
    writer.writeInt32(_formatVersion);
    writer.writeInt32(snapshot.schemaVersion);
    writer.writeMap(
        snapshot.ordinaryValues, LegacyEnvelopeLimits.maxOrdinaryValueBytes);
    writer.writeMap(
        snapshot.secureValues, LegacyEnvelopeLimits.maxSecureValueBytes);
    writer.writeNullableString(snapshot.identity.publicKeyHex,
        LegacyEnvelopeLimits.maxSecureValueBytes);
    writer.writeNullableString(
        snapshot.identity.flavor, LegacyEnvelopeLimits.maxSecureValueBytes);
    writer.writeNullableString(snapshot.identity.privateKeyHex,
        LegacyEnvelopeLimits.maxSecureValueBytes);

    final assets = snapshot.assets.toList()
      ..sort((left, right) {
        final pathOrder = left.relativePath.compareTo(right.relativePath);
        if (pathOrder != 0) return pathOrder;
        final sizeOrder = left.sizeBytes.compareTo(right.sizeBytes);
        if (sizeOrder != 0) return sizeOrder;
        return left.sha256.compareTo(right.sha256);
      });
    writer.writeInt32(assets.length);
    for (final asset in assets) {
      writer.writeString(
          asset.relativePath, LegacyEnvelopeLimits.maxAssetPathBytes);
      _ensure(
          asset.sizeBytes >= 0 &&
              asset.sizeBytes <= LegacyEnvelopeLimits.maxAssetSizeBytes,
          'Invalid asset size');
      writer.writeInt64(asset.sizeBytes);
      writer.writeString(asset.sha256, 64);
    }

    final body = writer.takeBytes();
    final digest = sha256.convert(body).bytes;
    final envelope = Uint8List(body.length + _digestBytes)
      ..setRange(0, body.length, body)
      ..setRange(body.length, body.length + _digestBytes, digest);
    return envelope;
  }

  static LegacyEnvelopeSnapshot decode(Uint8List envelope) {
    _ensure(envelope.length >= _magic.length + 12 + _digestBytes,
        'Envelope is truncated');
    _ensure(envelope.length <= LegacyEnvelopeLimits.maxEnvelopeBytes,
        'Envelope is too large');

    final bodyLength = envelope.length - _digestBytes;
    final actualDigest =
        sha256.convert(Uint8List.sublistView(envelope, 0, bodyLength)).bytes;
    final expectedDigest =
        Uint8List.sublistView(envelope, bodyLength, envelope.length);
    _ensure(_constantTimeEquals(actualDigest, expectedDigest),
        'Envelope integrity check failed');

    final cursor = _EnvelopeCursor(envelope, bodyLength);
    _ensure(_bytesEqual(cursor.readBytes(_magic.length), _magic),
        'Invalid envelope magic');
    _ensure(
        cursor.readInt32() == _formatVersion, 'Unsupported envelope format');
    final schemaVersion = cursor.readInt32();
    final ordinaryValues =
        cursor.readMap(LegacyEnvelopeLimits.maxOrdinaryValueBytes);
    final secureValues =
        cursor.readMap(LegacyEnvelopeLimits.maxSecureValueBytes);
    final identity = LegacyEnvelopeIdentity(
      publicKeyHex:
          cursor.readNullableString(LegacyEnvelopeLimits.maxSecureValueBytes),
      flavor:
          cursor.readNullableString(LegacyEnvelopeLimits.maxSecureValueBytes),
      privateKeyHex:
          cursor.readNullableString(LegacyEnvelopeLimits.maxSecureValueBytes),
    );
    final assetCount =
        cursor.readCount(LegacyEnvelopeLimits.maxAssets, 'asset');
    final assetPaths = <String>{};
    final assets = <LegacyEnvelopeAsset>[];
    for (var index = 0; index < assetCount; index++) {
      final relativePath =
          cursor.readString(LegacyEnvelopeLimits.maxAssetPathBytes);
      _ensure(assetPaths.add(relativePath), 'Duplicate asset path');
      final sizeBytes = cursor.readInt64();
      _ensure(
          sizeBytes >= 0 && sizeBytes <= LegacyEnvelopeLimits.maxAssetSizeBytes,
          'Invalid asset size');
      assets.add(LegacyEnvelopeAsset(
        relativePath: relativePath,
        sizeBytes: sizeBytes,
        sha256: cursor.readString(64),
      ));
    }
    _ensure(cursor.isAtEnd, 'Envelope contains trailing data');

    return LegacyEnvelopeSnapshot(
      schemaVersion: schemaVersion,
      ordinaryValues: ordinaryValues,
      secureValues: secureValues,
      identity: identity,
      assets: assets,
    );
  }
}

class _EnvelopeWriter {
  final BytesBuilder _builder = BytesBuilder(copy: false);

  void writeMap(Map<String, String> values, int maxValueBytes) {
    writeInt32(values.length);
    final keys = values.keys.toList()..sort();
    for (final key in keys) {
      writeString(key, LegacyEnvelopeLimits.maxKeyBytes);
      writeString(values[key]!, maxValueBytes);
    }
  }

  void writeNullableString(String? value, int maxBytes) {
    if (value == null) {
      writeInt32(-1);
    } else {
      writeString(value, maxBytes);
    }
  }

  void writeString(String value, int maxBytes) {
    final encoded = utf8.encode(value);
    _ensure(encoded.length <= maxBytes, 'Envelope field is too large');
    writeInt32(encoded.length);
    writeBytes(encoded);
  }

  void writeInt32(int value) {
    final data = ByteData(4)..setInt32(0, value, Endian.big);
    writeBytes(data.buffer.asUint8List());
  }

  void writeInt64(int value) {
    final data = ByteData(8)..setInt64(0, value, Endian.big);
    writeBytes(data.buffer.asUint8List());
  }

  void writeBytes(List<int> bytes) {
    _ensure(
      _builder.length + bytes.length <=
          LegacyEnvelopeLimits.maxEnvelopeBytes - _digestBytes,
      'Envelope is too large',
    );
    _builder.add(bytes);
  }

  Uint8List takeBytes() => _builder.takeBytes();
}

class _EnvelopeCursor {
  _EnvelopeCursor(this._bytes, this._limit);

  final Uint8List _bytes;
  final int _limit;
  int _offset = 0;

  Map<String, String> readMap(int maxValueBytes) {
    final count = readCount(LegacyEnvelopeLimits.maxEntriesPerStore, 'entry');
    final values = <String, String>{};
    for (var index = 0; index < count; index++) {
      final key = readString(LegacyEnvelopeLimits.maxKeyBytes);
      final value = readString(maxValueBytes);
      _ensure(!values.containsKey(key), 'Duplicate map key');
      values[key] = value;
    }
    return values;
  }

  int readCount(int max, String kind) {
    final count = readInt32();
    _ensure(count >= 0 && count <= max, 'Invalid $kind count');
    return count;
  }

  String? readNullableString(int maxBytes) {
    final length = readInt32();
    if (length == -1) return null;
    return _readStringBody(length, maxBytes);
  }

  String readString(int maxBytes) => _readStringBody(readInt32(), maxBytes);

  String _readStringBody(int length, int maxBytes) {
    _ensure(length >= 0 && length <= maxBytes, 'Invalid envelope field length');
    try {
      return utf8.decode(readBytes(length), allowMalformed: false);
    } on FormatException {
      throw const LegacyEnvelopeException('Envelope contains invalid UTF-8');
    }
  }

  int readInt32() => ByteData.sublistView(readBytes(4)).getInt32(0, Endian.big);

  int readInt64() => ByteData.sublistView(readBytes(8)).getInt64(0, Endian.big);

  Uint8List readBytes(int count) {
    _ensure(count >= 0 && count <= _limit - _offset, 'Envelope is truncated');
    final result = Uint8List.sublistView(_bytes, _offset, _offset + count);
    _offset += count;
    return result;
  }

  bool get isAtEnd => _offset == _limit;
}

bool _constantTimeEquals(List<int> left, List<int> right) {
  if (left.length != right.length) return false;
  var difference = 0;
  for (var index = 0; index < left.length; index++) {
    difference |= left[index] ^ right[index];
  }
  return difference == 0;
}

bool _bytesEqual(List<int> left, List<int> right) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}

void _ensure(bool condition, String message) {
  if (!condition) throw LegacyEnvelopeException(message);
}
