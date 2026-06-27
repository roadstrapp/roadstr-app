import 'package:flutter/material.dart';
import 'package:hive/hive.dart';
import 'app_theme.dart';

class ThemeProvider extends ChangeNotifier {
  static const _key = 'themeId';
  Box? _box;
  AppThemeId _current = AppThemeId.lightNostr;

  AppThemeId get current => _current;
  ThemeData get themeData => AppTheme.build(_current);

  Future<void> init() async {
    try {
      if (Hive.isBoxOpen('settings')) {
        _box = Hive.box('settings');
      } else {
        _box = await Hive.openBox('settings');
      }
      final saved = _box?.get(_key, defaultValue: 0) as int;
      _current = AppThemeIdExt.fromIndex(saved);
    } catch (_) {
      // Keep default theme if Hive isn't available
      _box = null;
      _current = AppThemeId.lightNostr;
    }
    notifyListeners();
  }

  void setTheme(AppThemeId id) {
    _current = id;
    try {
      _box?.put(_key, id.index2);
    } catch (_) {}
    notifyListeners();
  }
}
