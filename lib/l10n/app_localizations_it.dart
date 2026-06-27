// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Italian (`it`).
class AppLocalizationsIt extends AppLocalizations {
  AppLocalizationsIt([String locale = 'it']) : super(locale);

  @override
  String get searchHint => 'Dove vuoi andare?';

  @override
  String get calculatingRoute => 'Calcolo percorso…';

  @override
  String get routeNotFoundTitle => 'Percorso non trovato';

  @override
  String get noRouteFound =>
      'Nessun percorso trovato. Controlla la connessione.';

  @override
  String get emptyServerResponse =>
      'Risposta vuota dal server. Controlla la connessione.';

  @override
  String routeError(String error) {
    return 'Errore calcolo percorso: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS non disponibile — Impostazioni → App → Roadstr → Permessi → Posizione';

  @override
  String get acquiringGps => 'Acquisizione GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Server GraphHopper non configurato — uso OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API key GraphHopper non configurata — uso OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API key OpenRouteService non configurata — uso OSRM';

  @override
  String get chooseRoute => 'Scegli percorso';

  @override
  String routeOptionsCount(int count) {
    return '$count opzioni';
  }

  @override
  String get cancel => 'Annulla';

  @override
  String get startNavigation => 'Avvia navigazione';

  @override
  String get fastestRoute => 'Più rapido';

  @override
  String get now => 'Ora';

  @override
  String get history => 'Cronologia';

  @override
  String get clearHistory => 'Cancella';

  @override
  String get selectedPosition => 'Posizione selezionata';

  @override
  String get bottomBarProfile => 'Profilo';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Impostazioni';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Mappa';

  @override
  String get sectionPrivacy => 'Privacy';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Lingua';

  @override
  String get themeLightNostr => 'Chiaro · Viola Nostr';

  @override
  String get themeLightBitcoin => 'Chiaro · Arancione Bitcoin';

  @override
  String get langSystem => 'Predefinito di sistema';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Mantieni schermo acceso';

  @override
  String get keepScreenOnDescription =>
      'Impedisce lo spegnimento durante la navigazione';

  @override
  String get rotateMap => 'Mappa segue direzione';

  @override
  String get rotateMapDescription =>
      'La mappa ruota in base alla direzione di marcia';

  @override
  String get mapTileUrlLabel => 'URL Tile mappa';

  @override
  String get routingProviderLabel => 'Routing provider';

  @override
  String get osrmProvider => 'OSRM (pubblico, senza chiave)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (locale/privato)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API key)';

  @override
  String get openrouteProvider => 'OpenRouteService (API key)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API key (opzionale)';

  @override
  String get verify => 'Verifica';

  @override
  String get graphhopperServerUrlRequired =>
      'Inserisci l\'URL del server prima di verificare.';

  @override
  String get successTitle => 'Successo';

  @override
  String get graphhopperServerReachable => 'Server GraphHopper raggiungibile';

  @override
  String get errorTitle => 'Errore';

  @override
  String get close => 'Chiudi';

  @override
  String get privacyMode => 'Modalità privacy';

  @override
  String get privacyModeDescription => 'Non inviare dati anonimi di telemetria';

  @override
  String get infoVersion => 'Versione';

  @override
  String get infoProtocol => 'Protocollo';

  @override
  String get infoMaps => 'Mappe';

  @override
  String get infoRouting => 'Routing';

  @override
  String get infoSource => 'Sorgente';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (self-hosted)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profilo';

  @override
  String get notConnected => 'Non connesso';

  @override
  String get loginWithNostrTitle => 'ACCEDI CON NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'La chiave privata non lascia mai il dispositivo · Metodo consigliato';

  @override
  String get nsecLoginOption => 'Inserisci nsec';

  @override
  String get nsecLoginDescription =>
      'Chiave privata salvata localmente · Meno sicuro';

  @override
  String get connectedViaAmber => 'Connesso via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Connesso via nsec';

  @override
  String get publicKeyLabel => 'CHIAVE PUBBLICA';

  @override
  String get npubCopiedToClipboard => 'npub copiata negli appunti';

  @override
  String get logoutTitle => 'Disconnetti';

  @override
  String get logoutConfirmation =>
      'Vuoi rimuovere le credenziali Nostr da questo dispositivo?';

  @override
  String get logoutButton => 'Disconnetti';

  @override
  String get nostrIdentityInfo =>
      'Con un\'identità Nostr potrai pubblicare alert di traffico, segnalazioni e punti di interesse in modo decentralizzato sulla rete Nostr, senza server centrali.';

  @override
  String get warningTitle => 'Attenzione';

  @override
  String get nsecWarning =>
      'Stai per rawdoggare la tua chiave privata Nostr direttamente in un\'app. Chiunque abbia accesso fisico o remoto al tuo dispositivo potrebbe leggerla e prendere il controllo permanente della tua identità Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Il metodo sicuro è Amber (NIP-55): la nsec non lascia mai il vault dell\'app signer.';

  @override
  String get nsecRiskAcknowledgment =>
      'Ho capito il rischio e voglio continuare comunque';

  @override
  String get continueButton => 'Continua';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber è un app signer Android conforme a NIP-55. La tua chiave privata rimane isolata dentro Amber e non viene mai condivisa.';

  @override
  String get requestKeyFromAmber => 'Richiedi chiave ad Amber';

  @override
  String get amberNotFound =>
      'Amber non trovato. Installalo dal Play Store o inserisci la npub manualmente.';

  @override
  String get waitingForAmberResponse => 'In attesa della risposta di Amber…';

  @override
  String get pasteNpubManually => 'Incolla la npub manualmente:';

  @override
  String get confirmNpub => 'Conferma npub';

  @override
  String get enterNsecTitle => 'Inserisci nsec';

  @override
  String get loginButton => 'Accedi';

  @override
  String get invalidNpubTitle => 'npub non valida';

  @override
  String get invalidNsecTitle => 'nsec non valida';

  @override
  String get invalidNpubMessage =>
      'Verifica di aver incollato la chiave corretta.';

  @override
  String get invalidNsecMessage =>
      'Verifica di aver incollato la chiave corretta.';

  @override
  String get amberResponseError => 'Errore risposta Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'oppure';

  @override
  String get gpsNotActiveTitle => 'GPS non attivo';

  @override
  String get gpsDisabledMessage =>
      'Attiva il GPS nelle impostazioni del dispositivo per ottenere la tua posizione e usare la navigazione.';

  @override
  String get openSettings => 'Impostazioni';

  @override
  String get myLocation => 'La mia posizione';

  @override
  String get loginToReport =>
      'Accedi con Nostr (sezione Profilo) per segnalare eventi';

  @override
  String get navigateHere => 'Naviga qui';

  @override
  String get reportEventHere => 'Segnala evento qui';

  @override
  String get zapSendSats => 'Zap ⚡ (manda sats)';

  @override
  String get sendZap => 'Manda un Zap';

  @override
  String get chooseAmountSats => 'Scegli l\'importo in satoshi (sats):';

  @override
  String get customAmount => 'Importo personalizzato…';

  @override
  String get zapSending => 'Invio…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Recupero Lightning address…';

  @override
  String get noLightningAddress =>
      'Il segnalatore non ha una Lightning address';

  @override
  String get requestingInvoice => 'Richiedo invoice…';

  @override
  String get lnurlUnavailable => 'LNURL non disponibile';

  @override
  String get invoiceFailed => 'Impossibile generare invoice';

  @override
  String get openingWallet => 'Apertura wallet…';

  @override
  String get payingViaNwc => 'Pagamento via NWC…';

  @override
  String get noLightningWallet => 'Nessun wallet Lightning trovato';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sat inviati!';
  }

  @override
  String get stillThere => 'C\'è ancora';

  @override
  String get notThereAnymore => 'Non c\'è più';

  @override
  String get loginToConfirm => 'Accedi con Nostr per confermare o smentire';

  @override
  String get reportAnEvent => 'Segnala un evento';

  @override
  String get optionalComment => 'Commento opzionale…';

  @override
  String get publish => 'Pubblica';

  @override
  String get publishing => 'Pubblicazione…';

  @override
  String get reportPublished => 'Segnalazione pubblicata ✓';

  @override
  String get myReports => 'LE MIE SEGNALAZIONI';

  @override
  String get noReportsYet => 'Nessuna segnalazione pubblicata';

  @override
  String get zapBalance => 'Balance Zap';

  @override
  String get satoshiFromReports => 'Satoshi ricevuti dalle tue segnalazioni';

  @override
  String get reputationHigh => 'Alta';

  @override
  String get reputationMedium => 'Media';

  @override
  String get reputationLow => 'Bassa';

  @override
  String reputationLabel(String level) {
    return 'Reputazione $level';
  }

  @override
  String reliability(int pct) {
    return 'Affidabilità: $pct%';
  }

  @override
  String get confirmedLabel => 'Confermato';

  @override
  String get removedLabel => 'Rimosso';

  @override
  String get positionLabel => 'Posizione';

  @override
  String get loadingLabel => 'Caricamento…';

  @override
  String get sectionWebSearch => 'Ricerca web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Incolla l\'URI NWC del tuo wallet (Alby Hub, Mutiny, Cashu…) per pagare i Zap direttamente dall\'app.';

  @override
  String get searchEngineQwantDesc =>
      'Europeo, privacy-first. Nessun tracciamento, nessun profilo pubblicitario. Scelta consigliata.';

  @override
  String get searchEngineBraveDesc =>
      'Indice indipendente, open-source. Non dipende da Google o Bing. Zero profilazione.';

  @override
  String get searchEngineDdgDesc =>
      'Privacy-focused e popolare. I risultati arrivano in parte da Bing — tienilo a mente.';

  @override
  String get searchEngineStartpageDesc =>
      'Risultati Google senza cedere a Google i tuoi dati. Un compromesso ragionevole.';

  @override
  String get searchEngineGoogleDesc =>
      'Efficacissimo. Ricorda però che Google ti conosce meglio di tua madre e rivende tutto agli inserzionisti. Ma vabbè, scelta tua. 🍪';

  @override
  String get categoryPolice => 'Polizia';

  @override
  String get categorySpeedCamera => 'Autovelox';

  @override
  String get categoryTrafficJam => 'Traffico';

  @override
  String get categoryAccident => 'Incidente';

  @override
  String get categoryRoadClosure => 'Strada chiusa';

  @override
  String get categoryConstruction => 'Cantiere';

  @override
  String get categoryHazard => 'Pericolo';

  @override
  String get categoryRoadCondition => 'Condiz. strada';

  @override
  String get categoryPothole => 'Buca';

  @override
  String get categoryFog => 'Nebbia';

  @override
  String get categoryIce => 'Ghiaccio';

  @override
  String get categoryAnimal => 'Animale';

  @override
  String get categoryOther => 'Altro';

  @override
  String get dateTimeLabel => 'Data / ora';

  @override
  String minutesAgo(int count) {
    return '$count min fa';
  }

  @override
  String hoursAgo(int count) {
    return '${count}h fa';
  }

  @override
  String daysAgo(int count) {
    return '${count}gg fa';
  }
}
