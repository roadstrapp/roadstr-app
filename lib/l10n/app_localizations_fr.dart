// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get searchHint => 'Où voulez-vous aller ?';

  @override
  String get calculatingRoute => 'Calcul de l\'itinéraire…';

  @override
  String get routeNotFoundTitle => 'Itinéraire introuvable';

  @override
  String get noRouteFound =>
      'Aucun itinéraire trouvé. Vérifiez votre connexion.';

  @override
  String get emptyServerResponse =>
      'Réponse du serveur vide. Vérifiez votre connexion.';

  @override
  String routeError(String error) {
    return 'Erreur de calcul d\'itinéraire : $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS indisponible — Paramètres → App → Roadstr → Autorisations → Localisation';

  @override
  String get acquiringGps => 'Acquisition du GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Serveur GraphHopper non configuré — utilisation d\'OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Clé API GraphHopper non configurée — utilisation d\'OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Clé API OpenRouteService non configurée — utilisation d\'OSRM';

  @override
  String get chooseRoute => 'Choisir un itinéraire';

  @override
  String routeOptionsCount(int count) {
    return '$count options';
  }

  @override
  String get cancel => 'Annuler';

  @override
  String get startNavigation => 'Démarrer la navigation';

  @override
  String get fastestRoute => 'Plus rapide';

  @override
  String get now => 'Maintenant';

  @override
  String get history => 'Historique';

  @override
  String get clearHistory => 'Effacer';

  @override
  String get selectedPosition => 'Position sélectionnée';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Paramètres';

  @override
  String get sectionTheme => 'Thème';

  @override
  String get sectionMap => 'Carte';

  @override
  String get sectionPrivacy => 'Confidentialité';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Langue';

  @override
  String get themeLightNostr => 'Clair · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Clair · Bitcoin Orange';

  @override
  String get langSystem => 'Langue du système';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Garder l\'écran allumé';

  @override
  String get keepScreenOnDescription =>
      'Empêche la mise en veille pendant la navigation';

  @override
  String get rotateMap => 'La carte suit la direction';

  @override
  String get rotateMapDescription =>
      'La carte pivote selon la direction de conduite';

  @override
  String get mapTileUrlLabel => 'URL des tuiles de carte';

  @override
  String get routingProviderLabel => 'Fournisseur de routage';

  @override
  String get osrmProvider => 'OSRM (public, aucune clé requise)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (local/privé)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (clé API)';

  @override
  String get openrouteProvider => 'OpenRouteService (clé API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Clé API GraphHopper (facultatif)';

  @override
  String get verify => 'Vérifier';

  @override
  String get graphhopperServerUrlRequired =>
      'Entrez l\'URL du serveur avant de vérifier.';

  @override
  String get successTitle => 'Succès';

  @override
  String get graphhopperServerReachable => 'Serveur GraphHopper accessible';

  @override
  String get errorTitle => 'Erreur';

  @override
  String get close => 'Fermer';

  @override
  String get privacyMode => 'Mode confidentialité';

  @override
  String get privacyModeDescription =>
      'Ne pas envoyer de données de télémétrie anonymes';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protocole';

  @override
  String get infoMaps => 'Cartes';

  @override
  String get infoRouting => 'Routage';

  @override
  String get infoSource => 'Source';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (auto-hébergé)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Non connecté';

  @override
  String get loginWithNostrTitle => 'SE CONNECTER AVEC NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'La clé privée ne quitte jamais votre appareil · Recommandé';

  @override
  String get nsecLoginOption => 'Insérer nsec';

  @override
  String get nsecLoginDescription =>
      'Clé privée stockée localement · Moins sécurisé';

  @override
  String get connectedViaAmber => 'Connecté via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Connecté via nsec';

  @override
  String get publicKeyLabel => 'CLÉ PUBLIQUE';

  @override
  String get npubCopiedToClipboard => 'npub copié dans le presse-papiers';

  @override
  String get logoutTitle => 'Déconnecter';

  @override
  String get logoutConfirmation =>
      'Supprimer les identifiants Nostr de cet appareil ?';

  @override
  String get logoutButton => 'Déconnecter';

  @override
  String get nostrIdentityInfo =>
      'Avec une identité Nostr, vous pouvez publier des alertes de trafic, des rapports et des points d\'intérêt de manière décentralisée sur le réseau Nostr, sans serveurs centraux.';

  @override
  String get warningTitle => 'Avertissement';

  @override
  String get nsecWarning =>
      'Vous êtes sur le point d\'entrer votre clé privée Nostr directement dans une application. Toute personne ayant un accès physique ou distant à votre appareil pourrait la lire et prendre le contrôle permanent de votre identité Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  La méthode sécurisée est Amber (NIP-55) : le nsec ne quitte jamais le coffre-fort du signataire de l\'application.';

  @override
  String get nsecRiskAcknowledgment =>
      'Je comprends le risque et veux continuer quand même';

  @override
  String get continueButton => 'Continuer';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber est un signataire d\'application Android conforme NIP-55. Votre clé privée reste isolée dans Amber et n\'est jamais partagée.';

  @override
  String get requestKeyFromAmber => 'Demander la clé publique à Amber';

  @override
  String get amberNotFound =>
      'Amber introuvable. Installez-le depuis le Play Store ou entrez votre npub manuellement.';

  @override
  String get waitingForAmberResponse => 'En attente de la réponse d\'Amber…';

  @override
  String get pasteNpubManually => 'Collez votre npub manuellement :';

  @override
  String get confirmNpub => 'Confirmer le npub';

  @override
  String get enterNsecTitle => 'Insérer nsec';

  @override
  String get loginButton => 'Connexion';

  @override
  String get invalidNpubTitle => 'npub invalide';

  @override
  String get invalidNsecTitle => 'nsec invalide';

  @override
  String get invalidNpubMessage => 'Assurez-vous d\'avoir collé le bon npub.';

  @override
  String get invalidNsecMessage => 'Assurez-vous d\'avoir collé le bon nsec.';

  @override
  String get amberResponseError => 'Erreur de réponse Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'ou';

  @override
  String get gpsNotActiveTitle => 'GPS non actif';

  @override
  String get gpsDisabledMessage =>
      'Activez le GPS dans les paramètres de votre appareil pour obtenir votre position et utiliser la navigation.';

  @override
  String get openSettings => 'Paramètres';

  @override
  String get myLocation => 'Ma position';

  @override
  String get loginToReport =>
      'Connectez-vous avec Nostr (section Profil) pour signaler des événements';

  @override
  String get navigateHere => 'Naviguer ici';

  @override
  String get reportEventHere => 'Signaler un événement ici';

  @override
  String get zapSendSats => 'Zap ⚡ (envoyer des sats)';

  @override
  String get sendZap => 'Envoyer un Zap';

  @override
  String get chooseAmountSats => 'Choisir le montant en satoshi (sats) :';

  @override
  String get customAmount => 'Montant personnalisé…';

  @override
  String get zapSending => 'Envoi en cours…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress =>
      'Récupération de l\'adresse Lightning…';

  @override
  String get noLightningAddress =>
      'Ce rapporteur n\'a pas d\'adresse Lightning';

  @override
  String get requestingInvoice => 'Demande de facture…';

  @override
  String get lnurlUnavailable => 'LNURL non disponible';

  @override
  String get invoiceFailed => 'Impossible de générer la facture';

  @override
  String get openingWallet => 'Ouverture du portefeuille…';

  @override
  String get payingViaNwc => 'Paiement via NWC…';

  @override
  String get noLightningWallet => 'Aucun portefeuille Lightning trouvé';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats envoyés !';
  }

  @override
  String get stillThere => 'Toujours là';

  @override
  String get notThereAnymore => 'Plus là';

  @override
  String get loginToConfirm =>
      'Connectez-vous avec Nostr pour confirmer ou contester';

  @override
  String get reportAnEvent => 'Signaler un événement';

  @override
  String get optionalComment => 'Commentaire facultatif…';

  @override
  String get publish => 'Publier';

  @override
  String get publishing => 'Publication…';

  @override
  String get reportPublished => 'Rapport publié ✓';

  @override
  String get myReports => 'MES RAPPORTS';

  @override
  String get noReportsYet => 'Aucun rapport publié';

  @override
  String get zapBalance => 'Solde Zap';

  @override
  String get satoshiFromReports => 'Satoshi reçus de vos rapports';

  @override
  String get reputationHigh => 'Élevée';

  @override
  String get reputationMedium => 'Moyenne';

  @override
  String get reputationLow => 'Faible';

  @override
  String reputationLabel(String level) {
    return 'Réputation $level';
  }

  @override
  String reliability(int pct) {
    return 'Fiabilité : $pct%';
  }

  @override
  String get confirmedLabel => 'Confirmé';

  @override
  String get removedLabel => 'Supprimé';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Chargement…';

  @override
  String get sectionWebSearch => 'Recherche web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Collez votre URI NWC (Alby Hub, Mutiny, Cashu…) pour payer des Zaps directement depuis l\'application.';

  @override
  String get searchEngineQwantDesc =>
      'Européen, axé sur la confidentialité. Pas de traçage, pas de profils publicitaires. Recommandé.';

  @override
  String get searchEngineBraveDesc =>
      'Index indépendant, open-source. Aucune dépendance à Google ou Bing. Zéro profilage.';

  @override
  String get searchEngineDdgDesc =>
      'Axé sur la confidentialité et populaire. Résultats partiellement issus de Bing — gardez ça à l\'esprit.';

  @override
  String get searchEngineStartpageDesc =>
      'Résultats Google sans transmettre vos données à Google. Un compromis raisonnable.';

  @override
  String get searchEngineGoogleDesc =>
      'Très efficace. Mais rappelons-le : Google vous connaît mieux que votre mère et revend tout aux annonceurs. C\'est votre choix. 🍪';

  @override
  String get categoryPolice => 'Police';

  @override
  String get categorySpeedCamera => 'Radar';

  @override
  String get categoryTrafficJam => 'Embouteillage';

  @override
  String get categoryAccident => 'Accident';

  @override
  String get categoryRoadClosure => 'Route barrée';

  @override
  String get categoryConstruction => 'Travaux';

  @override
  String get categoryHazard => 'Danger';

  @override
  String get categoryRoadCondition => 'État de la route';

  @override
  String get categoryPothole => 'Nid-de-poule';

  @override
  String get categoryFog => 'Brouillard';

  @override
  String get categoryIce => 'Verglas';

  @override
  String get categoryAnimal => 'Animal';

  @override
  String get categoryOther => 'Autre';

  @override
  String get dateTimeLabel => 'Date / heure';

  @override
  String minutesAgo(int count) {
    return 'Il y a $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'Il y a ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'Il y a ${count}j';
  }
}
