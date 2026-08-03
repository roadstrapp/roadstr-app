import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/services/zap_service.dart';

/// Guards against the two ways a hostile remote endpoint reaches this app
/// without ever having to break a signature: an address that looks public but
/// resolves inward, and a text field with no length at all.
void main() {
  group('SSRF address filter', () {
    test('accepts ordinary public addresses', () {
      expect(ZapService.isPublicAddress(InternetAddress('1.1.1.1')), isTrue);
      expect(ZapService.isPublicAddress(InternetAddress('2606:4700::1111')),
          isTrue);
    });

    test('rejects private, loopback and reserved IPv4', () {
      for (final ip in [
        '0.0.0.0',
        '10.0.0.1',
        '127.0.0.1',
        '100.64.0.1',
        '169.254.169.254',
        '172.16.0.1',
        '192.168.1.1',
        '198.18.0.1',
        '224.0.0.1',
      ]) {
        expect(ZapService.isPublicAddress(InternetAddress(ip)), isFalse,
            reason: ip);
      }
    });

    test('rejects IPv6 that carries a private IPv4 inside it', () {
      for (final ip in [
        '::1',
        '::',
        'fe80::1',
        'fd00::1',
        'ff02::1',
        '::ffff:127.0.0.1',
        '::ffff:10.0.0.1',
        '::ffff:169.254.169.254',
        '::127.0.0.1',
        '64:ff9b::127.0.0.1',
      ]) {
        expect(ZapService.isPublicAddress(InternetAddress(ip)), isFalse,
            reason: ip);
      }
    });

    test('still accepts a mapped public IPv4', () {
      expect(ZapService.isPublicAddress(InternetAddress('::ffff:1.1.1.1')),
          isTrue);
    });
  });

  group('remote text clamp', () {
    test('caps oversized names and trims', () {
      final huge = 'A' * 5000;
      expect(NominatimResult.clampRemoteText(huge)!.length, 300);
      expect(NominatimResult.clampRemoteText('  Main Street  '), 'Main Street');
    });

    test('rejects empty and non-string values', () {
      expect(NominatimResult.clampRemoteText('   '), isNull);
      expect(NominatimResult.clampRemoteText(42), isNull);
      expect(NominatimResult.clampRemoteText(null), isNull);
    });

    test('a Nominatim result carries no unbounded field', () {
      final result = NominatimResult.fromJson({
        'lat': '45.0',
        'lon': '9.0',
        'display_name': 'X' * 4000,
        'class': 'H' * 4000,
        'type': 'T' * 4000,
        'address': {
          'road': 'R' * 4000,
          'house_number': '1' * 400,
          'city': 'C' * 4000,
        },
      });
      expect(result.displayName.length, 300);
      expect(result.city!.length, 120);
      // class/type reach switch statements and emoji lookups, not just tiles,
      // but "not rendered" is not a reason to accept an unbounded string.
      expect(result.cls!.length, 80);
      expect(result.type!.length, 80);
      // "road houseNo, city" built from the already-capped components.
      expect(
          result.shortName.length, lessThanOrEqualTo(120 + 1 + 24 + 2 + 120));
    });
  });
}
