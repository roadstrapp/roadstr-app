import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_nostr_strings.xml';

/// Android string name -> ARB key. Every language comes from the main app's
/// own translations, so the native screens say exactly what the Flutter ones do.
const _arbKeys = <String, String>{
  'native_nostr_sync_success': 'syncSuccessSnack',
  'native_nostr_sync_failed': 'syncFailedSnack',
  'native_nostr_export_success': 'exportSuccessSnack',
  'native_nostr_export_failed': 'exportFailedSnack',
  'native_nostr_import_success': 'importSuccessSnack',
  'native_nostr_import_failed': 'importFailedSnack',
  'native_nostr_report_published': 'reportPublished',
  'native_nostr_report_queued': 'reportQueuedOffline',
  'native_nostr_login_to_report': 'loginToReport',
  'native_nostr_login_to_vote': 'loginToConfirm',
  'native_nostr_speed_saved': 'speedLimitSaved',
  'native_nostr_edit_request_sent': 'editRequestSent',
  'native_nostr_visibility_error': 'profileVisibilityPublishError',
  'native_nostr_sync_passphrase_title': 'syncPassphraseTitle',
  'native_nostr_sync_passphrase_desc': 'syncPassphraseDesc',
  'native_nostr_relay_title': 'syncCustomRelayTitle',
  'native_nostr_relay_desc': 'syncCustomRelayDesc',
  'native_nostr_export_title': 'exportFavoritesTitle',
  'native_nostr_export_desc': 'exportFavoritesDesc',
  'native_nostr_export_encrypt': 'exportEncryptToggle',
  'native_nostr_export_button': 'exportButton',
  'native_nostr_password_hint': 'exportPasswordHint',
  'native_nostr_import_password': 'importPasswordPrompt',
  'native_nostr_edit_speed_title': 'editSpeedLimit',
  'native_nostr_request_speed_title': 'requestSpeedLimit',
  'native_nostr_speed_hint': 'speedLimitHint',
  'native_settings_gh_url_required': 'graphhopperServerUrlRequired',
  'native_settings_gh_reachable': 'graphhopperServerReachable',
  'native_zap_title': 'sendZap',
  'native_zap_choose_amount': 'chooseAmountSats',
  'native_zap_custom_amount': 'customAmount',
  'native_zap_sending': 'zapSending',
  'native_zap_send_button': 'zapAmountButton',
  'native_zap_sent': 'zapSent',
  'native_zap_status_fetching_address': 'fetchingLightningAddress',
  'native_zap_status_no_address': 'noLightningAddress',
  'native_zap_status_requesting_invoice': 'requestingInvoice',
  'native_zap_status_lnurl_unavailable': 'lnurlUnavailable',
  'native_zap_status_invoice_failed': 'invoiceFailed',
  'native_zap_status_opening_wallet': 'openingWallet',
  'native_zap_status_paying_nwc': 'payingViaNwc',
  'native_zap_status_no_wallet': 'noLightningWallet',
  'native_map_my_location': 'myLocation',
  'native_map_report_event': 'reportAnEvent',
  'native_map_add_stop': 'plannerAddStop',
  'native_parking_save_here': 'parkingSaveHere',
  'native_parking_saved': 'parkingSavedSnack',
  'native_parking_removed': 'parkingRemovedSnack',
  'native_nostr_cancel': 'cancel',
  'native_nostr_ok': 'ok',
  // Previously hand-written in the settings, navigation and saved-places
  // catalogues; they have an equivalent in the main app.
  'native_nav_exit_body': 'navExitBody',
  'native_nav_continue': 'navContinue',
  'native_nav_exit_confirm': 'navExit',
  'native_saved_label': 'favoriteLabelHint',
  'native_saved_geocoding_error': 'favoriteGeocodingError',
};

/// Strings the main app has no equivalent for. Each language is written out
/// explicitly: a missing one would silently fall back to English on the phone.
const _explicit = <String, Map<String, String>>{
  'native_settings_dark_map': {
    'bg': 'Тъмна карта',
    'cs': 'Tmavá mapa',
    'da': 'Mørkt kort',
    'de': 'Dunkle Karte',
    'el': 'Σκούρος χάρτης',
    'en': 'Dark map',
    'es': 'Mapa oscuro',
    'et': 'Tume kaart',
    'fi': 'Tumma kartta',
    'fr': 'Carte sombre',
    'ga': 'Léarscáil dhorcha',
    'hr': 'Tamna karta',
    'hu': 'Sötét térkép',
    'it': 'Mappa scura',
    'ja': 'ダークマップ',
    'lt': 'Tamsus žemėlapis',
    'lv': 'Tumša karte',
    'mt': 'Mappa skura',
    'nl': 'Donkere kaart',
    'pl': 'Ciemna mapa',
    'pt': 'Mapa escuro',
    'ro': 'Hartă întunecată',
    'ru': 'Тёмная карта',
    'sk': 'Tmavá mapa',
    'sl': 'Temen zemljevid',
    'sv': 'Mörk karta',
    'zh': '深色地图',
  },
  'native_settings_dark_map_desc': {
    'bg': 'Използвай тъмни карти независимо от темата на менюто',
    'cs': 'Používat tmavé mapové dlaždice nezávisle na motivu nabídky',
    'da': 'Brug mørke korttiles uafhængigt af menuens tema',
    'de': 'Dunkle Kartenkacheln unabhängig vom Menü-Design verwenden',
    'el': 'Χρήση σκούρων πλακιδίων χάρτη ανεξάρτητα από το θέμα του μενού',
    'en': 'Use dark map tiles independently from the menu theme',
    'es': 'Usar teselas de mapa oscuras independientemente del tema del menú',
    'et': 'Kasuta tumedaid kaardiplaate sõltumatult menüü teemast',
    'fi': 'Käytä tummia karttaruutuja valikon teemasta riippumatta',
    'fr':
        'Utiliser des tuiles de carte sombres indépendamment du thème des menus',
    'ga':
        'Úsáid tíleanna léarscáile dorcha gan spleáchas ar théama an roghchláir',
    'hr': 'Koristi tamne pločice karte neovisno o temi izbornika',
    'hu': 'Sötét térképcsempék használata a menü témájától függetlenül',
    'it': 'Usa le tile scure indipendentemente dal tema dei menu',
    'ja': 'メニューのテーマとは別に、ダークなマップタイルを使用します',
    'lt': 'Naudoti tamsias žemėlapio plyteles nepriklausomai nuo meniu temos',
    'lv': 'Izmantot tumšas kartes flīzes neatkarīgi no izvēlnes motīva',
    'mt': 'Uża t-tiles skuri tal-mappa indipendentement mit-tema tal-menu',
    'nl': 'Gebruik donkere kaarttegels onafhankelijk van het menuthema',
    'pl': 'Używaj ciemnych kafelków mapy niezależnie od motywu menu',
    'pt': 'Usar mosaicos de mapa escuros independentemente do tema do menu',
    'ro': 'Folosește plăci de hartă întunecate independent de tema meniului',
    'ru': 'Использовать тёмные тайлы карты независимо от темы меню',
    'sk': 'Používať tmavé mapové dlaždice nezávisle od témy ponuky',
    'sl': 'Uporabi temne ploščice zemljevida neodvisno od teme menija',
    'sv': 'Använd mörka kartrutor oberoende av menyns tema',
    'zh': '无论菜单主题如何，都使用深色地图图块',
  },
  'native_settings_map_engine': {
    'bg': 'Картографичен енджин',
    'cs': 'Mapový engine',
    'da': 'Kortmotor',
    'de': 'Karten-Engine',
    'el': 'Μηχανή χάρτη',
    'en': 'Map engine',
    'es': 'Motor de mapa',
    'et': 'Kaardimootor',
    'fi': 'Karttamoottori',
    'fr': 'Moteur de carte',
    'ga': 'Inneall léarscáile',
    'hr': 'Mapni mehanizam',
    'hu': 'Térképmotor',
    'it': 'Motore mappa',
    'ja': 'マップエンジン',
    'lt': 'Žemėlapio variklis',
    'lv': 'Kartes dzinējs',
    'mt': 'Magna tal-mappa',
    'nl': 'Kaartengine',
    'pl': 'Silnik mapy',
    'pt': 'Motor de mapa',
    'ro': 'Motor hartă',
    'ru': 'Картографический движок',
    'sk': 'Mapový engine',
    'sl': 'Mapni pogon',
    'sv': 'Kartmotor',
    'zh': '地图引擎',
  },
  'native_settings_nwc_save': {
    'bg': 'Запази сигурно',
    'cs': 'Bezpečně uložit',
    'da': 'Gem sikkert',
    'de': 'Sicher speichern',
    'el': 'Ασφαλής αποθήκευση',
    'en': 'Save securely',
    'es': 'Guardar de forma segura',
    'et': 'Salvesta turvaliselt',
    'fi': 'Tallenna turvallisesti',
    'fr': 'Enregistrer en sécurité',
    'ga': 'Sábháil go slán',
    'hr': 'Spremi sigurno',
    'hu': 'Biztonságos mentés',
    'it': 'Salva in modo sicuro',
    'ja': '安全に保存',
    'lt': 'Saugiai išsaugoti',
    'lv': 'Droši saglabāt',
    'mt': "Salva b'mod sigur",
    'nl': 'Veilig opslaan',
    'pl': 'Zapisz bezpiecznie',
    'pt': 'Guardar com segurança',
    'ro': 'Salvează în siguranță',
    'ru': 'Сохранить безопасно',
    'sk': 'Bezpečne uložiť',
    'sl': 'Varno shrani',
    'sv': 'Spara säkert',
    'zh': '安全保存',
  },
  'native_settings_nwc_remove': {
    'bg': 'Премахни',
    'cs': 'Odebrat',
    'da': 'Fjern',
    'de': 'Entfernen',
    'el': 'Αφαίρεση',
    'en': 'Remove',
    'es': 'Quitar',
    'et': 'Eemalda',
    'fi': 'Poista',
    'fr': 'Retirer',
    'ga': 'Bain',
    'hr': 'Ukloni',
    'hu': 'Eltávolítás',
    'it': 'Rimuovi',
    'ja': '削除',
    'lt': 'Pašalinti',
    'lv': 'Noņemt',
    'mt': 'Neħħi',
    'nl': 'Verwijderen',
    'pl': 'Usuń',
    'pt': 'Remover',
    'ro': 'Elimină',
    'ru': 'Удалить',
    'sk': 'Odstrániť',
    'sl': 'Odstrani',
    'sv': 'Ta bort',
    'zh': '移除',
  },
  'native_settings_nwc_invalid': {
    'bg':
        'Невалиден NWC URI. Проверете публичния ключ на портфейла, релето и тайния ключ.',
    'cs':
        'Neplatné NWC URI. Zkontrolujte veřejný klíč peněženky, relay a tajný klíč.',
    'da':
        'Ugyldig NWC-URI. Kontrollér tegnebogens offentlige nøgle, relay og hemmelighed.',
    'de':
        'Ungültige NWC-URI. Prüfe den öffentlichen Schlüssel der Wallet, das Relay und das Geheimnis.',
    'el':
        'Μη έγκυρο NWC URI. Ελέγξτε το δημόσιο κλειδί του πορτοφολιού, το relay και το μυστικό.',
    'en': 'Invalid NWC URI. Check the wallet public key, relay and secret.',
    'es':
        'URI NWC no válida. Comprueba la clave pública de la cartera, el relay y el secreto.',
    'et':
        'Kehtetu NWC URI. Kontrolli rahakoti avalikku võtit, releed ja saladust.',
    'fi':
        'Virheellinen NWC-URI. Tarkista lompakon julkinen avain, relay ja salaisuus.',
    'fr':
        'URI NWC invalide. Vérifiez la clé publique du portefeuille, le relais et le secret.',
    'ga':
        'URI NWC neamhbhailí. Seiceáil eochair phoiblí an sparáin, an athsheachadán agus an rún.',
    'hr': 'Nevažeći NWC URI. Provjerite javni ključ novčanika, relay i tajnu.',
    'hu':
        'Érvénytelen NWC URI. Ellenőrizd a tárca nyilvános kulcsát, a relayt és a titkot.',
    'it':
        'URI NWC non valido. Controlla chiave pubblica del wallet, relay e segreto.',
    'ja': 'NWC URIが無効です。ウォレットの公開鍵、リレー、シークレットを確認してください。',
    'lt':
        'Netinkamas NWC URI. Patikrinkite piniginės viešąjį raktą, relay ir paslaptį.',
    'lv':
        'Nederīgs NWC URI. Pārbaudiet maciņa publisko atslēgu, relay un noslēpumu.',
    'mt':
        'NWC URI invalidu. Iċċekkja ċ-ċavetta pubblika tal-kartiera, ir-relay u s-sigriet.',
    'nl':
        'Ongeldige NWC-URI. Controleer de publieke sleutel van de wallet, de relay en het geheim.',
    'pl':
        'Nieprawidłowy URI NWC. Sprawdź klucz publiczny portfela, relay i sekret.',
    'pt':
        'URI NWC inválido. Verifique a chave pública da carteira, o relay e o segredo.',
    'ro':
        'URI NWC invalid. Verifică cheia publică a portofelului, relay-ul și secretul.',
    'ru':
        'Недопустимый NWC URI. Проверьте открытый ключ кошелька, релей и секрет.',
    'sk':
        'Neplatné NWC URI. Skontrolujte verejný kľúč peňaženky, relay a tajomstvo.',
    'sl':
        'Neveljaven NWC URI. Preverite javni ključ denarnice, relay in skrivnost.',
    'sv':
        'Ogiltig NWC-URI. Kontrollera plånbokens publika nyckel, relay och hemlighet.',
    'zh': 'NWC URI 无效。请检查钱包公钥、中继和密钥。',
  },
  'native_nostr_action_failed': {
    'bg':
        'Действието не можа да бъде завършено. Проверете връзката и опитайте отново.',
    'cs':
        'Akci se nepodařilo dokončit. Zkontrolujte připojení a zkuste to znovu.',
    'da':
        'Handlingen kunne ikke gennemføres. Tjek din forbindelse, og prøv igen.',
    'de':
        'Die Aktion konnte nicht abgeschlossen werden. Prüfe die Verbindung und versuche es erneut.',
    'el':
        'Η ενέργεια δεν ολοκληρώθηκε. Ελέγξτε τη σύνδεσή σας και δοκιμάστε ξανά.',
    'en': "Couldn't complete the action. Check your connection and try again.",
    'es':
        'No se pudo completar la acción. Comprueba tu conexión e inténtalo de nuevo.',
    'et':
        'Toimingut ei õnnestunud lõpetada. Kontrolli ühendust ja proovi uuesti.',
    'fi': 'Toimintoa ei voitu suorittaa. Tarkista yhteys ja yritä uudelleen.',
    'fr':
        "Impossible de terminer l'action. Vérifiez votre connexion et réessayez.",
    'ga':
        'Níorbh fhéidir an gníomh a chur i gcrích. Seiceáil do cheangal agus bain triail eile as.',
    'hr': 'Radnja nije dovršena. Provjerite vezu i pokušajte ponovno.',
    'hu':
        'A műveletet nem sikerült befejezni. Ellenőrizd a kapcsolatot, és próbáld újra.',
    'it':
        'Impossibile completare l\'azione. Controlla la connessione e riprova.',
    'ja': '操作を完了できませんでした。接続を確認して、もう一度お試しください。',
    'lt': 'Nepavyko atlikti veiksmo. Patikrinkite ryšį ir bandykite dar kartą.',
    'lv':
        'Darbību neizdevās pabeigt. Pārbaudiet savienojumu un mēģiniet vēlreiz.',
    'mt':
        "L-azzjoni ma setgħetx titlesta. Iċċekkja l-konnessjoni tiegħek u erġa' pprova.",
    'nl':
        'De actie kon niet worden voltooid. Controleer je verbinding en probeer het opnieuw.',
    'pl': 'Nie udało się wykonać akcji. Sprawdź połączenie i spróbuj ponownie.',
    'pt':
        'Não foi possível concluir a ação. Verifique a ligação e tente novamente.',
    'ro':
        'Acțiunea nu a putut fi finalizată. Verifică conexiunea și încearcă din nou.',
    'ru':
        'Не удалось выполнить действие. Проверьте соединение и повторите попытку.',
    'sk':
        'Akciu sa nepodarilo dokončiť. Skontrolujte pripojenie a skúste to znova.',
    'sl':
        'Dejanja ni bilo mogoče dokončati. Preverite povezavo in poskusite znova.',
    'sv':
        'Åtgärden kunde inte slutföras. Kontrollera anslutningen och försök igen.',
    'zh': '无法完成操作。请检查网络连接后重试。',
  },
  'native_roadtest_amber_failed': {
    'bg':
        'Връзката с Amber не бе успешна. Проверете дали е инсталиран, и опитайте отново.',
    'cs':
        'Nepodařilo se spojit s Amber. Zkontrolujte, že je nainstalovaný, a zkuste to znovu.',
    'da':
        'Kunne ikke forbinde til Amber. Tjek, at den er installeret, og prøv igen.',
    'de':
        'Verbindung zu Amber fehlgeschlagen. Prüfe, ob es installiert ist, und versuche es erneut.',
    'el':
        'Η σύνδεση με το Amber απέτυχε. Βεβαιωθείτε ότι είναι εγκατεστημένο και δοκιμάστε ξανά.',
    'en': "Couldn't connect to Amber. Check that it's installed and try again.",
    'es':
        'No se pudo conectar con Amber. Comprueba que está instalado e inténtalo de nuevo.',
    'et':
        'Amberiga ühendamine ebaõnnestus. Kontrolli, et see on paigaldatud, ja proovi uuesti.',
    'fi':
        'Yhteys Amberiin epäonnistui. Tarkista, että se on asennettu, ja yritä uudelleen.',
    'fr':
        "Impossible de se connecter à Amber. Vérifiez qu'il est installé et réessayez.",
    'ga':
        'Níorbh fhéidir ceangal le Amber. Seiceáil go bhfuil sé suiteáilte agus bain triail eile as.',
    'hr':
        'Povezivanje s Amberom nije uspjelo. Provjerite je li instaliran i pokušajte ponovno.',
    'hu':
        'Nem sikerült csatlakozni az Amberhez. Ellenőrizd, hogy telepítve van-e, és próbáld újra.',
    'it':
        'Impossibile collegarsi ad Amber. Controlla che sia installato e riprova.',
    'ja': 'Amberに接続できませんでした。インストールされているか確認して、もう一度お試しください。',
    'lt':
        'Nepavyko prisijungti prie Amber. Patikrinkite, ar jis įdiegtas, ir bandykite dar kartą.',
    'lv':
        'Neizdevās savienoties ar Amber. Pārbaudiet, vai tas ir instalēts, un mēģiniet vēlreiz.',
    'mt':
        "Ma setgħetx tinħoloq konnessjoni ma' Amber. Iċċekkja li hu installat u erġa' pprova.",
    'nl':
        'Kon geen verbinding maken met Amber. Controleer of het is geïnstalleerd en probeer het opnieuw.',
    'pl':
        'Nie udało się połączyć z Amber. Sprawdź, czy jest zainstalowany, i spróbuj ponownie.',
    'pt':
        'Não foi possível ligar ao Amber. Verifique se está instalado e tente novamente.',
    'ro':
        'Nu s-a putut face conexiunea cu Amber. Verifică dacă este instalat și încearcă din nou.',
    'ru':
        'Не удалось подключиться к Amber. Убедитесь, что он установлен, и повторите попытку.',
    'sk':
        'Nepodarilo sa pripojiť k Amber. Skontrolujte, či je nainštalovaný, a skúste to znova.',
    'sl':
        'Povezava z Amberjem ni uspela. Preverite, ali je nameščen, in poskusite znova.',
    'sv':
        'Kunde inte ansluta till Amber. Kontrollera att den är installerad och försök igen.',
    'zh': '无法连接 Amber。请确认已安装后重试。',
  },
  'native_roadtest_invoice_copied': {
    'bg': 'Заявката за плащане е копирана в клипборда',
    'cs': 'Platební požadavek zkopírován do schránky',
    'da': 'Betalingsanmodning kopieret til udklipsholderen',
    'de': 'Zahlungsanforderung in die Zwischenablage kopiert',
    'el': 'Το αίτημα πληρωμής αντιγράφηκε στο πρόχειρο',
    'en': 'Payment request copied to the clipboard',
    'es': 'Solicitud de pago copiada al portapapeles',
    'et': 'Maksenõue kopeeriti lõikelauale',
    'fi': 'Maksupyyntö kopioitu leikepöydälle',
    'fr': 'Demande de paiement copiée dans le presse-papiers',
    'ga': 'Cóipeáladh an iarratas ar íocaíocht chuig an ngearrthaisce',
    'hr': 'Zahtjev za plaćanje kopiran u međuspremnik',
    'hu': 'A fizetési kérelem a vágólapra másolva',
    'it': 'Richiesta di pagamento copiata negli appunti',
    'ja': '支払いリクエストをクリップボードにコピーしました',
    'lt': 'Mokėjimo prašymas nukopijuotas į iškarpinę',
    'lv': 'Maksājuma pieprasījums nokopēts starpliktuvē',
    'mt': 'It-talba għall-ħlas ġiet ikkupjata fil-clipboard',
    'nl': 'Betaalverzoek gekopieerd naar het klembord',
    'pl': 'Żądanie płatności skopiowane do schowka',
    'pt': 'Pedido de pagamento copiado para a área de transferência',
    'ro': 'Cererea de plată a fost copiată în clipboard',
    'ru': 'Запрос на оплату скопирован в буфер обмена',
    'sk': 'Žiadosť o platbu bola skopírovaná do schránky',
    'sl': 'Zahteva za plačilo je kopirana v odložišče',
    'sv': 'Betalningsförfrågan kopierad till urklipp',
    'zh': '付款请求已复制到剪贴板',
  },
  'native_roadtest_no_app': {
    'bg': 'Няма приложение, което да отвори тази връзка',
    'cs': 'Žádná aplikace nemůže tento odkaz otevřít',
    'da': 'Ingen app kan åbne dette link',
    'de': 'Keine App kann diesen Link öffnen',
    'el': 'Καμία εφαρμογή δεν μπορεί να ανοίξει αυτόν τον σύνδεσμο',
    'en': 'No app available to open this link',
    'es': 'Ninguna aplicación puede abrir este enlace',
    'et': 'Ükski rakendus ei saa seda linki avada',
    'fi': 'Mikään sovellus ei voi avata tätä linkkiä',
    'fr': 'Aucune application ne peut ouvrir ce lien',
    'ga': 'Níl aon aip ar fáil chun an nasc seo a oscailt',
    'hr': 'Nijedna aplikacija ne može otvoriti ovu poveznicu',
    'hu': 'Nincs alkalmazás a hivatkozás megnyitásához',
    'it': 'Nessuna app disponibile per aprire questo collegamento',
    'ja': 'このリンクを開けるアプリがありません',
    'lt': 'Nėra programos šiai nuorodai atidaryti',
    'lv': 'Nav lietotnes, kas varētu atvērt šo saiti',
    'mt': 'Ma hemm l-ebda app biex tiftaħ din il-link',
    'nl': 'Geen app beschikbaar om deze link te openen',
    'pl': 'Brak aplikacji, która mogłaby otworzyć ten link',
    'pt': 'Nenhuma aplicação disponível para abrir esta ligação',
    'ro': 'Nicio aplicație nu poate deschide acest link',
    'ru': 'Нет приложения, способного открыть эту ссылку',
    'sk': 'Žiadna aplikácia nedokáže otvoriť tento odkaz',
    'sl': 'Nobena aplikacija ne more odpreti te povezave',
    'sv': 'Ingen app kan öppna den här länken',
    'zh': '没有可打开此链接的应用',
  },
  'native_nostr_sync_not_found': {
    'bg': 'Все още няма любими места в релетата ти',
    'cs': 'Ve tvých relay zatím nejsou žádná oblíbená místa',
    'da': 'Ingen favoritter fundet på dine relays endnu',
    'de': 'Auf deinen Relays wurden noch keine Favoriten gefunden',
    'el': 'Δεν βρέθηκαν ακόμη αγαπημένα στα relay σου',
    'en': 'No favorites found on your relays yet',
    'es': 'Todavía no hay favoritos en tus relays',
    'et': 'Sinu releedest ei leitud veel lemmikuid',
    'fi': 'Releistäsi ei löytynyt vielä suosikkeja',
    'fr': "Aucun favori trouvé sur tes relais pour l'instant",
    'ga': 'Níor aimsíodh aon ceann is fearr leat ar do athsheachadáin go fóill',
    'hr': 'Na tvojim relayima još nema favorita',
    'hu': 'A relayeken még nincsenek kedvencek',
    'it': 'Nessun preferito trovato sui tuoi relay',
    'ja': 'リレーにお気に入りはまだありません',
    'lt': 'Tavo relay dar nerasta mėgstamų vietų',
    'lv': 'Tavos relay vēl nav atrasta neviena izlase',
    'mt': 'Għadu ma nstab l-ebda favorit fir-relays tiegħek',
    'nl': 'Nog geen favorieten gevonden op je relays',
    'pl': 'Na twoich relayach nie ma jeszcze ulubionych',
    'pt': 'Ainda não há favoritos nos teus relays',
    'ro': 'Încă nu s-au găsit favorite pe relay-urile tale',
    'ru': 'На твоих релеях пока нет избранного',
    'sk': 'Na tvojich relay zatiaľ nie sú žiadne obľúbené',
    'sl': 'Na tvojih relayih še ni priljubljenih',
    'sv': 'Inga favoriter hittades på dina relays ännu',
    'zh': '你的中继上还没有找到收藏',
  },
  'native_nostr_sync_empty': {
    'bg': 'Няма какво да се публикува. Добави място или първо изтегли от Nostr',
    'cs': 'Zatím není co publikovat. Přidej místo nebo nejdřív stáhni z Nostru',
    'da': 'Intet at udgive endnu. Tilføj et sted, eller hent først fra Nostr',
    'de':
        'Noch nichts zu veröffentlichen. Füge einen Ort hinzu oder hole zuerst von Nostr',
    'el':
        'Δεν υπάρχει ακόμη τίποτα για δημοσίευση. Πρόσθεσε μια τοποθεσία ή ανάκτησε πρώτα από το Nostr',
    'en': 'Nothing to publish yet. Add a place, or pull from Nostr first',
    'es':
        'Aún no hay nada que publicar. Añade un lugar o recupera primero desde Nostr',
    'et': 'Pole veel midagi avaldada. Lisa koht või too esmalt Nostrist',
    'fi': 'Ei vielä mitään julkaistavaa. Lisää paikka tai hae ensin Nostrista',
    'fr':
        "Rien à publier pour l'instant. Ajoute un lieu ou récupère d'abord depuis Nostr",
    'ga':
        'Níl aon rud le foilsiú go fóill. Cuir áit leis nó tarraing ó Nostr ar dtús',
    'hr': 'Još nema ničega za objavu. Dodaj mjesto ili prvo preuzmi s Nostra',
    'hu':
        'Még nincs mit közzétenni. Adj hozzá egy helyet, vagy előbb töltsd le a Nostrról',
    'it':
        'Niente da pubblicare per ora. Aggiungi un luogo o recupera prima da Nostr',
    'ja': '公開するものがありません。場所を追加するか、先にNostrから取得してください',
    'lt':
        'Kol kas nėra ką skelbti. Pridėk vietą arba pirmiausia atsisiųsk iš Nostr',
    'lv':
        'Pagaidām nav ko publicēt. Pievieno vietu vai vispirms ielādē no Nostr',
    'mt': "Għad m'hemm xejn x'tippubblika. Żid post jew ġib minn Nostr l-ewwel",
    'nl':
        'Nog niets om te publiceren. Voeg een plek toe of haal eerst op van Nostr',
    'pl':
        'Na razie nie ma czego opublikować. Dodaj miejsce lub najpierw pobierz z Nostr',
    'pt':
        'Ainda não há nada para publicar. Adiciona um local ou recupera primeiro do Nostr',
    'ro':
        'Încă nu e nimic de publicat. Adaugă un loc sau recuperează mai întâi de pe Nostr',
    'ru': 'Пока нечего публиковать. Добавь место или сначала загрузи из Nostr',
    'sk':
        'Zatiaľ nie je čo publikovať. Pridaj miesto alebo najprv stiahni z Nostru',
    'sl': 'Še ni ničesar za objavo. Dodaj mesto ali najprej prenesi iz Nostra',
    'sv':
        'Inget att publicera ännu. Lägg till en plats eller hämta först från Nostr',
    'zh': '暂无可发布的内容。请先添加地点，或先从 Nostr 拉取',
  },
  'native_settings_gh_unreachable': {
    'bg': 'Не може да се стигне до GraphHopper сървър на този адрес',
    'cs': 'Na této adrese se nepodařilo najít server GraphHopper',
    'da': 'Kunne ikke nå en GraphHopper-server på den adresse',
    'de': 'Unter dieser Adresse ist kein GraphHopper-Server erreichbar',
    'el':
        'Δεν ήταν δυνατή η σύνδεση με διακομιστή GraphHopper σε αυτή τη διεύθυνση',
    'en': "Couldn't reach a GraphHopper server at that address",
    'es': 'No se pudo conectar con un servidor GraphHopper en esa dirección',
    'et':
        'Sellel aadressil ei õnnestunud GraphHopperi serveriga ühendust saada',
    'fi': 'GraphHopper-palvelinta ei tavoitettu tuosta osoitteesta',
    'fr': 'Impossible de joindre un serveur GraphHopper à cette adresse',
    'ga':
        'Níorbh fhéidir freastalaí GraphHopper a bhaint amach ag an seoladh sin',
    'hr': 'Na toj adresi nije moguće doći do GraphHopper poslužitelja',
    'hu': 'Ezen a címen nem érhető el GraphHopper-szerver',
    'it': 'Impossibile raggiungere un server GraphHopper a questo indirizzo',
    'ja': 'そのアドレスのGraphHopperサーバーに接続できませんでした',
    'lt': 'Nepavyko pasiekti GraphHopper serverio šiuo adresu',
    'lv': 'Šajā adresē neizdevās sasniegt GraphHopper serveri',
    'mt': "Ma setax jintlaħaq server GraphHopper f'dak l-indirizz",
    'nl': 'Geen GraphHopper-server bereikbaar op dat adres',
    'pl': 'Nie udało się połączyć z serwerem GraphHopper pod tym adresem',
    'pt': 'Não foi possível alcançar um servidor GraphHopper nesse endereço',
    'ro': 'Nu s-a putut ajunge la un server GraphHopper la această adresă',
    'ru': 'Не удалось подключиться к серверу GraphHopper по этому адресу',
    'sk': 'Na tejto adrese sa nepodarilo nájsť server GraphHopper',
    'sl': 'Na tem naslovu ni mogoče doseči strežnika GraphHopper',
    'sv': 'Det gick inte att nå en GraphHopper-server på den adressen',
    'zh': '无法连接到该地址的 GraphHopper 服务器',
  },
  'native_map_compass': {
    'bg': 'Компас',
    'cs': 'Kompas',
    'da': 'Kompas',
    'de': 'Kompass',
    'el': 'Πυξίδα',
    'en': 'Compass',
    'es': 'Brújula',
    'et': 'Kompass',
    'fi': 'Kompassi',
    'fr': 'Boussole',
    'ga': 'Compás',
    'hr': 'Kompas',
    'hu': 'Iránytű',
    'it': 'Bussola',
    'ja': 'コンパス',
    'lt': 'Kompasas',
    'lv': 'Kompass',
    'mt': 'Kumpass',
    'nl': 'Kompas',
    'pl': 'Kompas',
    'pt': 'Bússola',
    'ro': 'Busolă',
    'ru': 'Компас',
    'sk': 'Kompas',
    'sl': 'Kompas',
    'sv': 'Kompass',
    'zh': '指南针',
  },
  'native_nostr_relay_invalid': {
    'bg':
        'Невалиден адрес на реле. Използвайте wss://relay.example.com или ws://192.168.x.x:порт за реле в домашната мрежа',
    'cs':
        'Neplatná adresa relay. Použijte wss://relay.example.com nebo ws://192.168.x.x:port pro relay v domácí síti',
    'da':
        'Ugyldig relay-adresse. Brug wss://relay.example.com eller ws://192.168.x.x:port til et relay på dit hjemmenetværk',
    'de':
        'Keine gültige Relay-Adresse. Nutze wss://relay.example.com oder ws://192.168.x.x:Port für ein Relay im Heimnetz',
    'el':
        'Μη έγκυρη διεύθυνση relay. Χρησιμοποιήστε wss://relay.example.com ή ws://192.168.x.x:θύρα για relay στο οικιακό δίκτυο',
    'en':
        'Not a valid relay address. Use wss://relay.example.com, or ws://192.168.x.x:port for a relay on your home network',
    'es':
        'Dirección de relay no válida. Usa wss://relay.example.com o ws://192.168.x.x:puerto para un relay de tu red doméstica',
    'et':
        'Kehtetu releeaadress. Kasuta wss://relay.example.com või ws://192.168.x.x:port koduvõrgu releele',
    'fi':
        'Virheellinen relay-osoite. Käytä wss://relay.example.com tai ws://192.168.x.x:portti kotiverkon relaylle',
    'fr':
        'Adresse de relais invalide. Utilise wss://relay.example.com, ou ws://192.168.x.x:port pour un relais de ton réseau local',
    'ga':
        "Seoladh athsheachadáin neamhbhailí. Úsáid wss://relay.example.com, nó ws://192.168.x.x:port d'athsheachadán ar do líonra baile",
    'hr':
        'Nevažeća adresa releja. Koristi wss://relay.example.com ili ws://192.168.x.x:port za relej u kućnoj mreži',
    'hu':
        'Érvénytelen relay-cím. Használd a wss://relay.example.com címet, vagy a ws://192.168.x.x:port címet az otthoni hálózat relayéhez',
    'it':
        'Indirizzo relay non valido. Usa wss://relay.example.com, oppure ws://192.168.x.x:porta per un relay nella tua rete di casa',
    'ja':
        'リレーのアドレスが正しくありません。wss://relay.example.com か、自宅ネットワークのリレーには ws://192.168.x.x:ポート を使用してください',
    'lt':
        'Netinkamas relay adresas. Naudokite wss://relay.example.com arba ws://192.168.x.x:prievadas namų tinklo relay',
    'lv':
        'Nederīga relay adrese. Izmantojiet wss://relay.example.com vai ws://192.168.x.x:ports mājas tīkla relay',
    'mt':
        'Indirizz tar-relay mhux validu. Uża wss://relay.example.com, jew ws://192.168.x.x:port għal relay fin-netwerk tad-dar',
    'nl':
        'Geen geldig relay-adres. Gebruik wss://relay.example.com, of ws://192.168.x.x:poort voor een relay op je thuisnetwerk',
    'pl':
        'Nieprawidłowy adres relaya. Użyj wss://relay.example.com lub ws://192.168.x.x:port dla relaya w sieci domowej',
    'pt':
        'Endereço de relay inválido. Usa wss://relay.example.com ou ws://192.168.x.x:porta para um relay na tua rede doméstica',
    'ro':
        'Adresă de relay invalidă. Folosește wss://relay.example.com sau ws://192.168.x.x:port pentru un relay din rețeaua de acasă',
    'ru':
        'Недопустимый адрес релея. Используйте wss://relay.example.com или ws://192.168.x.x:порт для релея в домашней сети',
    'sk':
        'Neplatná adresa relay. Použite wss://relay.example.com alebo ws://192.168.x.x:port pre relay v domácej sieti',
    'sl':
        'Neveljaven naslov releja. Uporabi wss://relay.example.com ali ws://192.168.x.x:vrata za rele v domačem omrežju',
    'sv':
        'Ogiltig relay-adress. Använd wss://relay.example.com, eller ws://192.168.x.x:port för ett relay i hemnätverket',
    'zh': '中继地址无效。请使用 wss://relay.example.com，家庭网络中的中继请使用 ws://192.168.x.x:端口',
  },
  'native_map_whats_here': {
    'bg': 'Какво има тук?',
    'cs': 'Co je tady?',
    'da': 'Hvad er her?',
    'de': 'Was ist hier?',
    'el': 'Τι υπάρχει εδώ;',
    'en': "What's here?",
    'es': '¿Qué hay aquí?',
    'et': 'Mis siin on?',
    'fi': 'Mitä täällä on?',
    'fr': "Qu'y a-t-il ici ?",
    'ga': 'Cad atá anseo?',
    'hr': 'Što je ovdje?',
    'hu': 'Mi van itt?',
    'it': "Cosa c'è qui?",
    'ja': 'ここには何がある？',
    'lt': 'Kas čia yra?',
    'lv': 'Kas šeit ir?',
    'mt': "X'hemm hawn?",
    'nl': 'Wat is hier?',
    'pl': 'Co tu jest?',
    'pt': 'O que há aqui?',
    'ro': 'Ce este aici?',
    'ru': 'Что здесь?',
    'sk': 'Čo je tu?',
    'sl': 'Kaj je tukaj?',
    'sv': 'Vad finns här?',
    'zh': '这里有什么？',
  },
  'native_history_clear': {
    'bg': 'Изчисти историята',
    'cs': 'Vymazat historii',
    'da': 'Ryd historik',
    'de': 'Verlauf löschen',
    'el': 'Εκκαθάριση ιστορικού',
    'en': 'Clear history',
    'es': 'Borrar historial',
    'et': 'Tühjenda ajalugu',
    'fi': 'Tyhjennä historia',
    'fr': "Effacer l'historique",
    'ga': 'Glan an stair',
    'hr': 'Izbriši povijest',
    'hu': 'Előzmények törlése',
    'it': 'Svuota cronologia',
    'ja': '履歴を消去',
    'lt': 'Išvalyti istoriją',
    'lv': 'Notīrīt vēsturi',
    'mt': 'Neħħi l-istorja',
    'nl': 'Geschiedenis wissen',
    'pl': 'Wyczyść historię',
    'pt': 'Limpar histórico',
    'ro': 'Șterge istoricul',
    'ru': 'Очистить историю',
    'sk': 'Vymazať históriu',
    'sl': 'Počisti zgodovino',
    'sv': 'Rensa historik',
    'zh': '清除历史记录',
  },
  'native_history_empty': {
    'bg': 'Няма скорошни маршрути',
    'cs': 'Žádné nedávné trasy',
    'da': 'Ingen seneste ruter',
    'de': 'Keine letzten Routen',
    'el': 'Καμία πρόσφατη διαδρομή',
    'en': 'No recent routes',
    'es': 'Sin rutas recientes',
    'et': 'Hiljutisi marsruute pole',
    'fi': 'Ei viimeaikaisia reittejä',
    'fr': 'Aucun itinéraire récent',
    'ga': 'Níl aon bhealaí le déanaí ann',
    'hr': 'Nema nedavnih ruta',
    'hu': 'Nincsenek legutóbbi útvonalak',
    'it': 'Nessun percorso recente',
    'ja': '最近のルートはありません',
    'lt': 'Neseniai nebuvo maršrutų',
    'lv': 'Nav nesenu maršrutu',
    'mt': 'Ebda rotta riċenti',
    'nl': 'Geen recente routes',
    'pl': 'Brak ostatnich tras',
    'pt': 'Sem rotas recentes',
    'ro': 'Niciun traseu recent',
    'ru': 'Нет недавних маршрутов',
    'sk': 'Žiadne nedávne trasy',
    'sl': 'Ni nedavnih poti',
    'sv': 'Inga senaste rutter',
    'zh': '没有最近的路线',
  },
  'native_history_empty_body': {
    'bg': 'Тук се появяват маршрутите, които стартирате. Съхраняват се шифровани само на това устройство.',
    'cs': 'Zde se zobrazí trasy, které spustíte. Jsou uloženy šifrovaně pouze v tomto zařízení.',
    'da': 'Ruter, du starter, vises her. De gemmes krypteret kun på denne enhed.',
    'de': 'Routen, die du startest, erscheinen hier. Sie werden verschlüsselt nur auf diesem Gerät gespeichert.',
    'el': 'Οι διαδρομές που ξεκινάτε εμφανίζονται εδώ. Αποθηκεύονται κρυπτογραφημένες μόνο σε αυτή τη συσκευή.',
    'en': 'Routes you start appear here. They are stored encrypted on this device only.',
    'es': 'Las rutas que inicies aparecen aquí. Se guardan cifradas solo en este dispositivo.',
    'et': 'Siin kuvatakse alustatud marsruudid. Need salvestatakse krüpteeritult ainult sellesse seadmesse.',
    'fi': 'Aloittamasi reitit näkyvät täällä. Ne tallennetaan salattuina vain tälle laitteelle.',
    'fr': 'Les itinéraires que vous démarrez apparaissent ici. Ils sont stockés chiffrés sur cet appareil uniquement.',
    'ga': 'Taispeánfar anseo na bealaí a thosaíonn tú. Stóráiltear iad criptithe ar an ngléas seo amháin.',
    'hr': 'Rute koje pokrenete pojavljuju se ovdje. Pohranjene su šifrirane samo na ovom uređaju.',
    'hu': 'Az elindított útvonalak itt jelennek meg. Titkosítva, csak ezen az eszközön tároljuk őket.',
    'it': 'I percorsi che avvii compaiono qui. Sono salvati cifrati solo su questo dispositivo.',
    'ja': '開始したルートはここに表示されます。暗号化され、この端末にのみ保存されます。',
    'lt': 'Čia rodomi jūsų pradėti maršrutai. Jie saugomi užšifruoti tik šiame įrenginyje.',
    'lv': 'Šeit parādās jūsu sāktie maršruti. Tie tiek glabāti šifrēti tikai šajā ierīcē.',
    'mt': 'Ir-rotot li tibda jidhru hawn. Jinħażnu kriptati fuq dan l-apparat biss.',
    'nl': 'Routes die je start verschijnen hier. Ze worden alleen versleuteld op dit apparaat opgeslagen.',
    'pl': 'Rozpoczęte trasy pojawiają się tutaj. Są przechowywane w postaci zaszyfrowanej tylko na tym urządzeniu.',
    'pt': 'As rotas que inicia aparecem aqui. São guardadas cifradas apenas neste dispositivo.',
    'ro': 'Traseele pe care le începi apar aici. Sunt stocate criptat doar pe acest dispozitiv.',
    'ru': 'Здесь появляются начатые вами маршруты. Они хранятся в зашифрованном виде только на этом устройстве.',
    'sk': 'Tu sa zobrazia trasy, ktoré spustíte. Ukladajú sa šifrované len v tomto zariadení.',
    'sl': 'Tukaj se prikažejo poti, ki jih začnete. Shranjene so šifrirane samo v tej napravi.',
    'sv': 'Rutter du startar visas här. De lagras krypterade endast på den här enheten.',
    'zh': '你开始的路线会显示在这里。它们仅以加密形式存储在此设备上。',
  },
  'native_history_remove': {
    'bg': 'Премахни от историята',
    'cs': 'Odebrat z historie',
    'da': 'Fjern fra historik',
    'de': 'Aus Verlauf entfernen',
    'el': 'Αφαίρεση από το ιστορικό',
    'en': 'Remove from history',
    'es': 'Quitar del historial',
    'et': 'Eemalda ajaloost',
    'fi': 'Poista historiasta',
    'fr': "Retirer de l'historique",
    'ga': 'Bain den stair',
    'hr': 'Ukloni iz povijesti',
    'hu': 'Eltávolítás az előzményekből',
    'it': 'Rimuovi dalla cronologia',
    'ja': '履歴から削除',
    'lt': 'Pašalinti iš istorijos',
    'lv': 'Noņemt no vēstures',
    'mt': 'Neħħi mill-istorja',
    'nl': 'Uit geschiedenis verwijderen',
    'pl': 'Usuń z historii',
    'pt': 'Remover do histórico',
    'ro': 'Elimină din istoric',
    'ru': 'Удалить из истории',
    'sk': 'Odstrániť z histórie',
    'sl': 'Odstrani iz zgodovine',
    'sv': 'Ta bort från historiken',
    'zh': '从历史记录中删除',
  },
};

/// Identical in every language: an example address, not prose.
const _verbatim = <String, String>{
  'native_nostr_relay_hint': 'wss://relay.example.com',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  // The only placeholders in this catalogue are counts: places imported, sats.
  final withPlaceholder = source
      .replaceAll('{n}', '\u0000N\u0000')
      .replaceAll('{sats}', '\u0000N\u0000');
  if (RegExp(r'\{[^}]+\}').hasMatch(withPlaceholder)) {
    throw FormatException('Unsupported placeholder in $sourceKey: $source');
  }
  return withPlaceholder
      .replaceAll(r'\', r'\\')
      .replaceAll('%', '%%')
      .replaceAll("'", r"\'")
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll('\n', r'\n')
      .replaceAll('\r', r'\r')
      .replaceAll('\t', r'\t')
      .replaceAll('\u0000N\u0000', r'%1$d');
}

String _buildResource(String language, Map<String, dynamic> arb) {
  final entries = <String, String>{};
  for (final entry in _arbKeys.entries) {
    final source = arb[entry.value];
    if (source is! String || source.isEmpty) {
      throw FormatException('Missing ${entry.value} in app_$language.arb');
    }
    entries[entry.key] = _androidText(source, entry.value);
  }
  for (final entry in _explicit.entries) {
    final source = entry.value[language];
    if (source == null || source.isEmpty) {
      throw FormatException('Missing $language translation of ${entry.key}');
    }
    entries[entry.key] = _androidText(source, entry.key);
  }
  _verbatim.forEach((key, value) => entries[key] = _androidText(value, key));

  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated by tools/kotlin_rewrite/generate_android_nostr_strings.dart. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final key in entries.keys.toList()..sort()) {
    output.writeln('    <string name="$key">${entries[key]}</string>');
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidNostrResources() {
  final sources = Directory(_sourceDirectory)
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .toList()
    ..sort((left, right) => left.path.compareTo(right.path));
  if (sources.length != 27) {
    throw StateError('Expected 27 ARB locale files, found ${sources.length}');
  }
  final outputs = <String, String>{};
  for (final source in sources) {
    final language =
        RegExp(r'app_([a-z]{2})\.arb$').firstMatch(source.path)!.group(1)!;
    final decoded = jsonDecode(source.readAsStringSync());
    if (decoded is! Map<String, dynamic>) {
      throw FormatException('${source.path} is not a JSON object');
    }
    outputs['$_resourceDirectory/${_resourceFolder(language)}/$_outputName'] =
        _buildResource(language, decoded);
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidNostrResources().entries) {
    final output = File(entry.key);
    if (check) {
      if (!output.existsSync() || output.readAsStringSync() != entry.value) {
        stderr.writeln('${entry.key} is stale; regenerate it.');
        stale = true;
      }
      continue;
    }
    output.parent.createSync(recursive: true);
    output.writeAsStringSync(entry.value);
  }
  if (stale) exitCode = 1;
}
