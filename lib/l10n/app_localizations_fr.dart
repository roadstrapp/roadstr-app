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
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Langue';

  @override
  String get themeLightNostr => 'Clair · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Clair · Bitcoin Orange';

  @override
  String get themeDarkNostr => 'Sombre · Nostr Violet';

  @override
  String get themeDarkBitcoin => 'Sombre · Bitcoin Orange';

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

  @override
  String get sectionFavorites => 'Lieux enregistrés';

  @override
  String get addFavorite => 'Ajouter un lieu';

  @override
  String get favoriteLabelHint => 'Nom (ex. Maison, Bureau)';

  @override
  String get favoriteAddressHint => 'Adresse';

  @override
  String get favoriteGeocodingError =>
      'Adresse introuvable. Essaie une adresse plus précise.';

  @override
  String get trafficAlertTitle => 'Nouveau trafic sur l\'itinéraire';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category signalé $age sur votre itinéraire.\n\nVoulez-vous trouver un autre itinéraire?';
  }

  @override
  String get trafficContinue => 'Continuer';

  @override
  String get trafficRecalculate => 'Recalculer l\'itinéraire';

  @override
  String get navExitTitle => 'Quitter la navigation?';

  @override
  String get navExitBody =>
      'Voulez-vous arrêter la navigation et revenir à la carte?';

  @override
  String get navContinue => 'Continuer la navigation';

  @override
  String get navExit => 'Oui, quitter';

  @override
  String get loadingInfo => 'Chargement des informations…';

  @override
  String get conditionsOnRoute => 'Conditions sur l\'itinéraire';

  @override
  String get calculateRoute => 'Calculer l\'itinéraire';

  @override
  String get sectionNavigationVoice => 'Voix de navigation';

  @override
  String get voiceGuidance => 'Guidage vocal';

  @override
  String get voiceGuidanceDesc =>
      'Lire les instructions de direction à voix haute pendant la navigation';

  @override
  String get testVoiceEngine => 'Tester le moteur vocal';

  @override
  String get testVoiceEngineDesc =>
      'Touchez pour vérifier le moteur TTS et obtenir les instructions d\'installation';

  @override
  String get ttsDialogTitle => 'Moteur vocal manquant';

  @override
  String get ttsDialogBody =>
      'Aucun moteur Text-to-Speech fonctionnel n\'a été trouvé.\n\nRoadstr ne s\'appuie que sur des logiciels open source — installez l\'un de ces moteurs gratuits depuis F-Droid :';

  @override
  String get ttsRhvoiceDesc => 'Voix naturelle, liste de langues limitée';

  @override
  String get ttsEspeakDesc => 'Couvre plus de 100 langues, voix robotique';

  @override
  String get ttsInstallNote =>
      '⚠️ Après l\'installation :\n1. Paramètres Android → Accessibilité → Synthèse vocale\n2. Sélectionnez le moteur que vous venez d\'installer\n3. Téléchargez les données vocales de votre langue\n4. Redémarrez complètement Roadstr';

  @override
  String get ttsTestNow => 'Tester maintenant';

  @override
  String get voiceUnsupportedTitle => 'Guidage vocal non disponible';

  @override
  String get voiceUnsupportedBody =>
      'Votre langue n\'est pas encore prise en charge pour les indications vocales. Les instructions de navigation continueront à s\'afficher sous forme de texte à l\'écran.';

  @override
  String get kokoroModelTitle => 'Modèle vocal (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Non téléchargé · ~82 Mo';

  @override
  String get kokoroModelStatusDownloading => 'Téléchargement...';

  @override
  String get kokoroModelStatusReady => 'Modèle vocal prêt';

  @override
  String get kokoroModelDownloadBtn => 'Télécharger';

  @override
  String get kokoroModelSupportedLangs =>
      'Prend en charge : italien, anglais, espagnol, français, japonais, chinois, portugais';

  @override
  String get autoDarkMode => 'Thème sombre automatique';

  @override
  String get autoDarkModeDesc => 'Active le thème sombre au coucher du soleil';

  @override
  String get settingsImperialUnits => 'Unités impériales';

  @override
  String get settingsImperialUnitsDesc =>
      'Miles et pieds au lieu de kilomètres et mètres';

  @override
  String get arrivedTitle => '🎉 Vous êtes arrivé !';

  @override
  String get arrivedBody => 'Vous avez atteint votre destination.';

  @override
  String get arrivedFeedbackPrompt => 'Comment s\'est passé le trajet ?';

  @override
  String get feedbackBad => 'Mal';

  @override
  String get feedbackGood => 'Bien !';

  @override
  String get feedbackDialogTitle => 'Dites-nous ce qui s\'est mal passé';

  @override
  String get feedbackHint => 'Décrivez le problème…';

  @override
  String get feedbackSent => 'Avis envoyé — merci ! 🙏';

  @override
  String get feedbackSubmit => 'Envoyer';

  @override
  String get transportModeCar => 'Voiture';

  @override
  String get transportModeWalk => 'À pied';

  @override
  String etaArrivalLabel(String time) {
    return 'Arr. $time';
  }

  @override
  String get supportRoadstr => 'Soutenir Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copié dans le presse-papiers';
  }

  @override
  String get disclaimerTitle => 'Avis important';

  @override
  String get disclaimerAccept => 'J\'ai lu et j\'accepte';

  @override
  String get disclaimerBody =>
      'Roadstr est une application de navigation expérimentale, open source et maintenue par la communauté, basée sur les données OpenStreetMap et le protocole Nostr, mise à disposition pour une utilisation dans n\'importe quel pays. En téléchargeant, installant ou utilisant cette application, l\'utilisateur accepte sans réserve l\'ensemble des conditions suivantes, sans limitation territoriale :\n\n🚗  SÉCURITÉ ROUTIÈRE AVANT TOUT\nLe conducteur doit toujours garder les yeux sur la route et respecter l\'ensemble du code de la route et de la signalisation en vigueur, qui priment toujours sur toute indication de l\'application. Ne jamais manipuler l\'appareil en conduisant ; le fixer sur un support homologué et visible avant de démarrer, sans jamais détourner son attention de la route pour interagir avec lui lorsque le véhicule est en mouvement.\n\n⚠️  ACCEPTATION DES RISQUES — VALABLE DANS LE MONDE ENTIER\nEn utilisant Roadstr, dans quelque pays et sous quelque système juridique que ce soit, l\'utilisateur assume sciemment et volontairement TOUS les risques liés à son utilisation, y compris, sans s\'y limiter : accidents de la route, blessures corporelles, décès, dommages matériels, dommages au véhicule, amendes, sanctions administratives, mise en fourrière, immobilisation du véhicule, responsabilité pénale, ou toute autre conséquence découlant directement ou indirectement du fait de se fier à l\'application. L\'utilisateur assume seul l\'entière responsabilité de chaque décision de conduite et de navigation.\n\n🚫  AUCUNE GARANTIE\nRoadstr est fournie strictement « EN L\'ÉTAT » et « SELON DISPONIBILITÉ », sans garantie d\'aucune sorte, expresse, implicite ou légale — y compris, sans limitation, les garanties d\'exactitude, d\'exhaustivité, de fiabilité, de disponibilité, de qualité marchande, d\'adéquation à un usage particulier et de non-contrefaçon. Les données cartographiques, l\'itinéraire, les limitations de vitesse, les radars et les informations sur les zones à circulation limitée (ZTL/ZAC/LTZ) proviennent de sources ouvertes maintenues par la communauté (OpenStreetMap, API Overpass) qui peuvent être incomplètes, obsolètes ou inexactes pour tout pays, région ou commune, à tout moment et sans préavis. Il incombe exclusivement à l\'utilisateur de vérifier de façon autonome, avant et pendant le trajet, la légalité et l\'accessibilité de tout itinéraire suggéré, en la comparant à la signalisation et à la réglementation locale officielle.\n\n📍  PRÉCISION ET GPS\nLe positionnement GPS peut être imprécis ou indisponible. Toutes les indications, distances et alertes sont fournies à titre purement indicatif et ne doivent jamais constituer le seul fondement d\'une décision de conduite.\n\n🛡️  LIMITATION DE RESPONSABILITÉ\nDans toute la mesure permise par la loi applicable dans une juridiction donnée, les développeurs, les contributeurs et toute partie impliquée dans la création ou la distribution de Roadstr ne pourront être tenus responsables d\'aucun dommage direct, indirect, accessoire, consécutif, spécial, exemplaire ou punitif, quelle que soit sa nature — y compris les blessures corporelles, le décès ou les pertes financières — découlant de l\'utilisation ou de l\'impossibilité d\'utiliser l\'application, même s\'ils avaient été informés de la possibilité de tels dommages. Lorsqu\'une juridiction n\'autorise pas, en tout ou en partie, cette limitation, la responsabilité sera limitée dans la mesure minimale permise par la loi de cette juridiction.\n\n🤝  INDEMNISATION\nL\'utilisateur s\'engage à indemniser et à dégager de toute responsabilité les développeurs et les contributeurs pour toute réclamation, dommage, perte ou dépense (y compris les frais juridiques) découlant de l\'utilisation de l\'application par l\'utilisateur ou d\'une violation des présentes conditions.\n\n🔒  CONFIDENTIALITÉ\nAucune donnée de localisation n\'est transmise aux serveurs de Roadstr. Le calcul d\'itinéraire est effectué via des services tiers (OSRM, GraphHopper, OpenRouteService) auxquels seules les coordonnées de départ et de destination sont envoyées.\n\n⚖️  DIVISIBILITÉ\nSi une disposition des présentes conditions est jugée inapplicable dans une juridiction donnée, cette disposition sera limitée ou supprimée dans la mesure minimale nécessaire, et toutes les autres dispositions resteront pleinement en vigueur.\n\nEn utilisant Roadstr, où que ce soit dans le monde, l\'utilisateur confirme avoir lu, compris et accepté sans réserve chacune des conditions ci-dessus, et assume l\'entière responsabilité — et tous les risques — liés à l\'utilisation de l\'application et à toute conséquence en découlant.';

  @override
  String get readOnWikipedia => 'Lire sur Wikipédia';

  @override
  String searchOnEngine(String engine) {
    return 'Rechercher sur $engine';
  }

  @override
  String get plannerFromHint => 'De…';

  @override
  String get plannerToHint => 'Destination…';

  @override
  String departEta(String dep, String arr) {
    return 'Départ $dep  →  Arrivée $arr';
  }

  @override
  String get modeCar => 'Voiture';

  @override
  String get modeBike => 'Vélo';

  @override
  String get modeWalk => 'À pied';

  @override
  String windSpeed(String speed) {
    return 'vent $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m min';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}h${m}min';
  }

  @override
  String get weatherClear => 'Clair';

  @override
  String get weatherPartlyCloudy => 'Prt. nuageux';

  @override
  String get weatherCloudy => 'Nuageux';

  @override
  String get weatherFog => 'Brouillard';

  @override
  String get weatherLightRain => 'Pluie légère';

  @override
  String get weatherRain => 'Pluie';

  @override
  String get weatherSnow => 'Neige';

  @override
  String get weatherShowers => 'Averses';

  @override
  String get weatherThunderstorm => 'Orage';

  @override
  String get ztlAheadWarning =>
      'Zone à trafic limité en approche — l\'itinéraire y entre';

  @override
  String get ztlInsideWarning => 'Zone à trafic limité';

  @override
  String get onboardingAppSubtitle => 'Navigation Nostr open source';

  @override
  String get onboardingWelcomeTitle => 'Bienvenue';

  @override
  String get onboardingWelcomeBody =>
      'Tout ce dont votre application de navigation a besoin — sans sacrifier votre vie privée.';

  @override
  String get onboardingFeatureNav => 'Navigation GPS virage par virage';

  @override
  String get onboardingFeatureNostr =>
      'Événements routiers Nostr (radars, dangers, trafic)';

  @override
  String get onboardingFeatureLightning =>
      'Pourboires Lightning Network pour les signaleurs';

  @override
  String get onboardingFeatureVoice =>
      'Guidage vocal IA sur l\'appareil (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Aucun compte requis — aucun suivi, jamais';

  @override
  String get onboardingGetStarted => 'Commencer';

  @override
  String get onboardingNostrTitle => 'Identité Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Optionnel — connectez-vous pour signaler des événements routiers, confirmer des alertes et gagner des pourboires Lightning.';

  @override
  String get onboardingNostrConnected => 'Connecté';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (recommandé)';

  @override
  String get onboardingAmberSubtitle =>
      'Connectez-vous avec l\'app signataire Amber. Votre clé privée ne quitte jamais Amber.';

  @override
  String get onboardingNsecTitle => 'Clé nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Collez votre clé privée. Stockée dans l\'Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Clé nsec invalide — vérifiez et réessayez.';

  @override
  String get onboardingSkip => 'Passer pour l\'instant';

  @override
  String get onboardingContinue => 'Continuer';

  @override
  String get onboardingEnterNsec => 'Entrez la clé nsec';

  @override
  String get onboardingSetupTitle => 'Configurer Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configurez l\'accès à la localisation et le guidage vocal optionnel.';

  @override
  String get onboardingLocationTitle => 'Localisation';

  @override
  String get onboardingLocationGranted => 'Accès à la localisation accordé';

  @override
  String get onboardingLocationRequired => 'Requis pour la navigation GPS';

  @override
  String get onboardingGrantButton => 'Autoriser';

  @override
  String get onboardingGrapheneTitle => 'Utilisateurs GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Accordez la localisation Précise (pas Approximative) et autorisez l\'accès Toujours (pas seulement en cours d\'utilisation) dans:\nParamètres → Applications → Roadstr → Autorisations → Localisation\n\nAvec une localisation approximative ou \'seulement en cours d\'utilisation\', la navigation GPS perdra la position en arrière-plan.';

  @override
  String get onboardingVoiceTitle => 'Guidage vocal IA (optionnel)';

  @override
  String get onboardingVoiceReady => 'Modèle vocal Kokoro prêt';

  @override
  String get onboardingVoiceDownloading => 'Téléchargement du modèle vocal…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Téléchargez le modèle IA Kokoro (82 Mo) pour la voix sur l\'appareil';

  @override
  String get onboardingVoiceChecking => 'Vérification du statut du modèle…';

  @override
  String get onboardingDownloadButton => 'Télécharger';

  @override
  String get onboardingVoiceLaterHint =>
      'Vous pouvez aussi télécharger le modèle vocal plus tard dans\nParamètres → Voix de navigation.';

  @override
  String get onboardingReadyTitle => 'Vous êtes prêt à naviguer !';

  @override
  String get onboardingReadyBody =>
      'Roadstr va maintenant ouvrir la carte.\nVous pouvez tout configurer dans Paramètres.';

  @override
  String get onboardingLetsGo => 'C\'est parti !';

  @override
  String get onboardingProfileLoading => 'Chargement du profil…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Voix';

  @override
  String get kokoroVoiceFemale => 'Féminine';

  @override
  String get kokoroVoiceMale => 'Masculine';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Voix masculine non disponible pour cette langue';

  @override
  String get kokoroSpeedTitle => 'Vitesse de parole';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Facultatif : vos favoris enregistrés peuvent se synchroniser entre vos appareils via les relais Nostr, chiffrés de bout en bout (NIP-44) avec votre propre clé — les relais ne voient que du texte chiffré et personne d’autre que vous ne peut lire le contenu. Activez-le à tout moment dans les Réglages.';

  @override
  String get parkingSaveHere => 'Enregistrer le stationnement ici';

  @override
  String get parkingMarkerTitle => 'Place de stationnement';

  @override
  String get parkingNavigateHere => 'Naviguer vers le stationnement';

  @override
  String get parkingRemove => 'Supprimer le stationnement';

  @override
  String get parkingSavedSnack => 'Stationnement enregistré';

  @override
  String get parkingRemovedSnack => 'Stationnement supprimé';

  @override
  String get exportFavoritesTitle => 'Exporter les favoris';

  @override
  String get exportFavoritesDesc =>
      'Enregistrez vos lieux favoris dans un fichier JSON que vous pouvez sauvegarder ou transférer sur un autre appareil.';

  @override
  String get exportEncryptToggle => 'Chiffrer avec un mot de passe';

  @override
  String get exportPasswordHint => 'Mot de passe';

  @override
  String get exportButton => 'Exporter';

  @override
  String get exportSuccessSnack => 'Favoris exportés';

  @override
  String get exportFailedSnack => 'Échec de l’exportation';

  @override
  String get importFavoritesTitle => 'Importer les favoris';

  @override
  String get importPasswordPrompt =>
      'Ce fichier est chiffré — saisissez le mot de passe';

  @override
  String importSuccessSnack(int n) {
    return '$n favoris importés';
  }

  @override
  String get importFailedSnack =>
      'Échec de l’importation — vérifiez le fichier et le mot de passe';

  @override
  String get syncFavoritesTitle => 'Synchroniser les favoris';

  @override
  String get syncFavoritesDesc =>
      'Publiez vos favoris, chiffrés de bout en bout, sur vos relais Nostr pour qu’ils vous suivent sur tous vos appareils. Les relais ne voient que du texte chiffré — personne d’autre que vous ne peut lire le contenu.';

  @override
  String get syncNowButton => 'Envoyer vers Nostr';

  @override
  String get syncPullButton => 'Récupérer depuis Nostr';

  @override
  String get syncSuccessSnack => 'Favoris synchronisés';

  @override
  String get syncFailedSnack => 'Échec de la synchronisation';

  @override
  String syncLastSyncLabel(String when) {
    return 'Dernière synchronisation : $when';
  }

  @override
  String get syncNoIdentity =>
      'Connectez-vous avec Nostr (Réglages → Profil) pour activer la synchronisation';

  @override
  String get onboardingVpnNotice =>
      'Pour une confidentialité maximale — les signalements sont propagés sur le réseau Nostr — l\'utilisation d\'un VPN est recommandée. Mullvad, payable en Bitcoin, est le choix recommandé.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Pour un fonctionnement fiable, réglez l\'autorisation de localisation de l\'application sur « Toujours autoriser », pas seulement pendant l\'utilisation.';

  @override
  String get trafficNormal => 'Trafic normal';

  @override
  String get trafficModerate => 'Trafic modéré';

  @override
  String get trafficHeavy => 'Trafic dense';

  @override
  String get avoidHighwaysChip => 'Éviter les autoroutes';

  @override
  String get avoidTollsChip => 'Éviter les péages';

  @override
  String get preferShorterChip => 'Itinéraire le plus court';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Afficher l\'aperçu de l\'itinéraire';
}
