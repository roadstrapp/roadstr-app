import 'package:flutter/foundation.dart';

/// Debug diagnostics that are compiled out of release builds.
///
/// Network exceptions, relay notices and spoken navigation phrases may carry
/// locations or user-provided text, so production builds must not forward
/// them to logcat.
void debugLog(String? message, {int? wrapWidth}) {
  if (kDebugMode) {
    debugPrint(message, wrapWidth: wrapWidth);
  }
}
