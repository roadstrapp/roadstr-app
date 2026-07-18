import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/screens/wikipedia_webview_screen.dart';

void main() {
  bool allowed(String value) =>
      WikipediaWebViewScreen.isAllowedArticleUri(Uri.parse(value));

  test('allows only standard and mobile Wikipedia article URLs', () {
    expect(allowed('https://it.wikipedia.org/wiki/Roma'), isTrue);
    expect(allowed('https://en.m.wikipedia.org/wiki/Rome'), isTrue);
    expect(allowed('https://zh.wikipedia.org/wiki/Test?oldid=1'), isTrue);
  });

  test('rejects deceptive origins and non-article URLs', () {
    expect(allowed('https://wikipedia.org.evil.test/wiki/Roma'), isFalse);
    expect(allowed('https://it.wikipedia.org.evil.test/wiki/Roma'), isFalse);
    expect(allowed('http://it.wikipedia.org/wiki/Roma'), isFalse);
    expect(allowed('https://user@it.wikipedia.org/wiki/Roma'), isFalse);
    expect(allowed('https://it.wikipedia.org:8443/wiki/Roma'), isFalse);
    expect(allowed('https://it.wikipedia.org/w/index.php?title=Roma'), isFalse);
    expect(allowed('https://it.wikipedia.org/wiki/'), isFalse);
  });
}
