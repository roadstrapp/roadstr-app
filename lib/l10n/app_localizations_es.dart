// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get searchHint => '¿A dónde quieres ir?';

  @override
  String get calculatingRoute => 'Calculando ruta…';

  @override
  String get routeNotFoundTitle => 'Ruta no encontrada';

  @override
  String get noRouteFound =>
      'No se encontró ninguna ruta. Comprueba tu conexión.';

  @override
  String get emptyServerResponse =>
      'Respuesta del servidor vacía. Comprueba tu conexión.';

  @override
  String routeError(String error) {
    return 'Error al calcular la ruta: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS no disponible — Ajustes → App → Roadstr → Permisos → Ubicación';

  @override
  String get acquiringGps => 'Obteniendo GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Servidor GraphHopper no configurado — usando OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Clave API de GraphHopper no configurada — usando OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Clave API de OpenRouteService no configurada — usando OSRM';

  @override
  String get chooseRoute => 'Elegir ruta';

  @override
  String routeOptionsCount(int count) {
    return '$count opciones';
  }

  @override
  String get cancel => 'Cancelar';

  @override
  String get startNavigation => 'Iniciar navegación';

  @override
  String get fastestRoute => 'Más rápida';

  @override
  String get now => 'Ahora';

  @override
  String get history => 'Historial';

  @override
  String get clearHistory => 'Borrar';

  @override
  String get selectedPosition => 'Posición seleccionada';

  @override
  String get bottomBarProfile => 'Perfil';

  @override
  String get bottomBarMenu => 'Menú';

  @override
  String get settingsTitle => 'Ajustes';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Mapa';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Idioma';

  @override
  String get themeLightNostr => 'Claro · Nostr Violeta';

  @override
  String get themeLightBitcoin => 'Claro · Bitcoin Naranja';

  @override
  String get themeDarkNostr => 'Oscuro · Nostr Violeta';

  @override
  String get themeDarkBitcoin => 'Oscuro · Bitcoin Naranja';

  @override
  String get langSystem => 'Predeterminado del sistema';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Mantener pantalla encendida';

  @override
  String get keepScreenOnDescription =>
      'Evita el modo de reposo durante la navegación';

  @override
  String get rotateMap => 'El mapa sigue la dirección';

  @override
  String get rotateMapDescription =>
      'El mapa rota según la dirección de conducción';

  @override
  String get mapTileUrlLabel => 'URL de mosaicos del mapa';

  @override
  String get routingProviderLabel => 'Proveedor de enrutamiento';

  @override
  String get osrmProvider => 'OSRM (público, sin clave requerida)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (local/privado)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (clave API)';

  @override
  String get openrouteProvider => 'OpenRouteService (clave API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Clave API de GraphHopper (opcional)';

  @override
  String get verify => 'Verificar';

  @override
  String get graphhopperServerUrlRequired =>
      'Introduce la URL del servidor antes de verificar.';

  @override
  String get successTitle => 'Éxito';

  @override
  String get graphhopperServerReachable => 'Servidor GraphHopper accesible';

  @override
  String get errorTitle => 'Error';

  @override
  String get close => 'Cerrar';

  @override
  String get infoVersion => 'Versión';

  @override
  String get infoProtocol => 'Protocolo';

  @override
  String get infoMaps => 'Mapas';

  @override
  String get infoRouting => 'Enrutamiento';

  @override
  String get infoSource => 'Fuente';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (auto-alojado)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (nube)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Perfil';

  @override
  String get notConnected => 'No conectado';

  @override
  String get loginWithNostrTitle => 'INICIAR SESIÓN CON NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'La clave privada nunca sale de tu dispositivo · Recomendado';

  @override
  String get nsecLoginOption => 'Insertar nsec';

  @override
  String get nsecLoginDescription =>
      'Clave privada almacenada localmente · Menos seguro';

  @override
  String get connectedViaAmber => 'Conectado a través de Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Conectado a través de nsec';

  @override
  String get publicKeyLabel => 'CLAVE PÚBLICA';

  @override
  String get npubCopiedToClipboard => 'npub copiado al portapapeles';

  @override
  String get logoutTitle => 'Desconectar';

  @override
  String get logoutConfirmation =>
      '¿Eliminar las credenciales de Nostr de este dispositivo?';

  @override
  String get logoutButton => 'Desconectar';

  @override
  String get nostrIdentityInfo =>
      'Con una identidad Nostr puedes publicar alertas de tráfico, informes y puntos de interés de forma descentralizada en la red Nostr, sin servidores centrales.';

  @override
  String get warningTitle => 'Advertencia';

  @override
  String get nsecWarning =>
      'Estás a punto de introducir tu clave privada de Nostr directamente en una app. Cualquier persona con acceso físico o remoto a tu dispositivo podría leerla y tomar el control permanente de tu identidad Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  El método seguro es Amber (NIP-55): el nsec nunca sale del almacén del firmante de la app.';

  @override
  String get nsecRiskAcknowledgment =>
      'Entiendo el riesgo y quiero continuar de todas formas';

  @override
  String get continueButton => 'Continuar';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber es un firmante de aplicación Android compatible con NIP-55. Tu clave privada permanece aislada dentro de Amber y nunca se comparte.';

  @override
  String get requestKeyFromAmber => 'Solicitar clave pública a Amber';

  @override
  String get amberNotFound =>
      'Amber no encontrado. Instálalo desde la Play Store o introduce tu npub manualmente.';

  @override
  String get waitingForAmberResponse => 'Esperando respuesta de Amber…';

  @override
  String get pasteNpubManually => 'Pega tu npub manualmente:';

  @override
  String get confirmNpub => 'Confirmar npub';

  @override
  String get enterNsecTitle => 'Insertar nsec';

  @override
  String get loginButton => 'Iniciar sesión';

  @override
  String get invalidNpubTitle => 'npub inválido';

  @override
  String get invalidNsecTitle => 'nsec inválido';

  @override
  String get invalidNpubMessage =>
      'Asegúrate de haber pegado el npub correcto.';

  @override
  String get invalidNsecMessage =>
      'Asegúrate de haber pegado el nsec correcto.';

  @override
  String get amberResponseError => 'Error de respuesta de Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'o';

  @override
  String get gpsNotActiveTitle => 'GPS no activo';

  @override
  String get gpsDisabledMessage =>
      'Activa el GPS en los ajustes de tu dispositivo para obtener tu ubicación y usar la navegación.';

  @override
  String get openSettings => 'Ajustes';

  @override
  String get myLocation => 'Mi ubicación';

  @override
  String get loginToReport =>
      'Inicia sesión con Nostr (sección Perfil) para reportar eventos';

  @override
  String get navigateHere => 'Navegar aquí';

  @override
  String get reportEventHere => 'Reportar evento aquí';

  @override
  String get zapSendSats => 'Zap ⚡ (enviar sats)';

  @override
  String get sendZap => 'Enviar un Zap';

  @override
  String get chooseAmountSats => 'Elige el monto en satoshi (sats):';

  @override
  String get customAmount => 'Monto personalizado…';

  @override
  String get zapSending => 'Enviando…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Obteniendo dirección Lightning…';

  @override
  String get noLightningAddress =>
      'Este reportero no tiene dirección Lightning';

  @override
  String get requestingInvoice => 'Solicitando factura…';

  @override
  String get lnurlUnavailable => 'LNURL no disponible';

  @override
  String get invoiceFailed => 'No se pudo generar la factura';

  @override
  String get openingWallet => 'Abriendo billetera…';

  @override
  String get payingViaNwc => 'Pagando vía NWC…';

  @override
  String get noLightningWallet => 'No se encontró billetera Lightning';

  @override
  String zapSent(int sats) {
    return '⚡ ¡$sats sats enviados!';
  }

  @override
  String get stillThere => 'Sigue ahí';

  @override
  String get notThereAnymore => 'Ya no está';

  @override
  String get loginToConfirm =>
      'Inicia sesión con Nostr para confirmar o disputar';

  @override
  String get reportAnEvent => 'Reportar un evento';

  @override
  String get optionalComment => 'Comentario opcional…';

  @override
  String get publish => 'Publicar';

  @override
  String get publishing => 'Publicando…';

  @override
  String get reportPublished => 'Reporte publicado ✓';

  @override
  String get myReports => 'MIS REPORTES';

  @override
  String get noReportsYet => 'No hay reportes publicados';

  @override
  String get zapBalance => 'Saldo Zap';

  @override
  String get satoshiFromReports => 'Satoshi recibidos de tus reportes';

  @override
  String get reputationHigh => 'Alta';

  @override
  String get reputationMedium => 'Media';

  @override
  String get reputationLow => 'Baja';

  @override
  String reputationLabel(String level) {
    return 'Reputación $level';
  }

  @override
  String reliability(int pct) {
    return 'Fiabilidad: $pct%';
  }

  @override
  String get confirmedLabel => 'Confirmado';

  @override
  String get removedLabel => 'Eliminado';

  @override
  String get positionLabel => 'Posición';

  @override
  String get loadingLabel => 'Cargando…';

  @override
  String get sectionWebSearch => 'Búsqueda web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Pega tu URI NWC (Alby Hub, Mutiny, Cashu…) para pagar Zaps directamente desde la app.';

  @override
  String get searchEngineQwantDesc =>
      'Europeo, con privacidad ante todo. Sin rastreo, sin perfiles publicitarios. Recomendado.';

  @override
  String get searchEngineBraveDesc =>
      'Índice independiente, código abierto. Sin dependencia de Google ni Bing. Cero perfilado.';

  @override
  String get searchEngineDdgDesc =>
      'Centrado en la privacidad y popular. Resultados parcialmente de Bing — tenlo en cuenta.';

  @override
  String get searchEngineStartpageDesc =>
      'Resultados de Google sin entregar tus datos a Google. Un compromiso razonable.';

  @override
  String get searchEngineGoogleDesc =>
      'Muy efectivo. Pero recuerda: Google te conoce mejor que tu madre y vende todo a los anunciantes. Tú decides. 🍪';

  @override
  String get categoryPolice => 'Policía';

  @override
  String get categorySpeedCamera => 'Radar de velocidad';

  @override
  String get categoryTrafficJam => 'Atasco';

  @override
  String get categoryAccident => 'Accidente';

  @override
  String get categoryRoadClosure => 'Corte de vía';

  @override
  String get categoryConstruction => 'Obras';

  @override
  String get categoryHazard => 'Peligro';

  @override
  String get categoryRoadCondition => 'Estado de la vía';

  @override
  String get categoryPothole => 'Bache';

  @override
  String get categoryFog => 'Niebla';

  @override
  String get categoryIce => 'Hielo';

  @override
  String get categoryAnimal => 'Animal';

  @override
  String get categoryOther => 'Otro';

  @override
  String get dateTimeLabel => 'Fecha / hora';

  @override
  String minutesAgo(int count) {
    return 'hace $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'hace ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'hace ${count}d';
  }

  @override
  String get sectionFavorites => 'Lugares guardados';

  @override
  String get addFavorite => 'Añadir lugar';

  @override
  String get favoriteLabelHint => 'Nombre (p. ej. Casa, Oficina)';

  @override
  String get favoriteAddressHint => 'Dirección';

  @override
  String get favoriteGeocodingError =>
      'Dirección no encontrada. Intenta con una más específica.';

  @override
  String get trafficAlertTitle => 'Nuevo tráfico en la ruta';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category reportado $age en tu ruta.\n\n¿Quieres encontrar una ruta alternativa?';
  }

  @override
  String get trafficContinue => 'Continuar';

  @override
  String get trafficRecalculate => 'Recalcular ruta';

  @override
  String get navExitTitle => '¿Salir de la navegación?';

  @override
  String get navExitBody => '¿Quieres detener la navegación y volver al mapa?';

  @override
  String get navContinue => 'Continuar navegación';

  @override
  String get navExit => 'Sí, salir';

  @override
  String get loadingInfo => 'Cargando información…';

  @override
  String get conditionsOnRoute => 'Condiciones en la ruta';

  @override
  String get calculateRoute => 'Calcular ruta';

  @override
  String get sectionNavigationVoice => 'Voz de navegación';

  @override
  String get voiceGuidance => 'Guía por voz';

  @override
  String get voiceGuidanceDesc =>
      'Leer las instrucciones de giro en voz alta durante la navegación';

  @override
  String get testVoiceEngine => 'Probar motor de voz';

  @override
  String get testVoiceEngineDesc =>
      'Toca para comprobar el motor TTS y obtener instrucciones de configuración';

  @override
  String get ttsDialogTitle => 'Falta el motor de voz';

  @override
  String get ttsDialogBody =>
      'No se encontró ningún motor Text-to-Speech funcional.\n\nRoadstr solo depende de software de código abierto — instala uno de estos motores gratuitos desde F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Voz natural, lista de idiomas limitada';

  @override
  String get ttsEspeakDesc => 'Cubre más de 100 idiomas, voz robótica';

  @override
  String get ttsInstallNote =>
      '⚠️ Después de instalar:\n1. Ajustes de Android → Accesibilidad → Síntesis de voz\n2. Selecciona el motor que acabas de instalar\n3. Descarga los datos de voz de tu idioma\n4. Reinicia Roadstr por completo';

  @override
  String get ttsTestNow => 'Probar ahora';

  @override
  String get voiceUnsupportedTitle => 'Guía por voz no disponible';

  @override
  String get voiceUnsupportedBody =>
      'Tu idioma aún no es compatible con las indicaciones de voz. Las instrucciones de navegación seguirán apareciendo como texto en pantalla.';

  @override
  String get kokoroModelTitle => 'Modelo de voz (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'No descargado · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Descargando...';

  @override
  String get kokoroModelStatusReady => 'Modelo de voz listo';

  @override
  String get kokoroModelDownloadBtn => 'Descargar';

  @override
  String get kokoroModelSupportedLangs =>
      'Admite: italiano, inglés, español, francés, japonés, chino, portugués';

  @override
  String get autoDarkMode => 'Tema oscuro automático';

  @override
  String get autoDarkModeDesc => 'Activa el tema oscuro al atardecer';

  @override
  String get settingsImperialUnits => 'Unidades imperiales';

  @override
  String get settingsImperialUnitsDesc =>
      'Millas y pies en lugar de kilómetros y metros';

  @override
  String get arrivedTitle => '🎉 ¡Has llegado!';

  @override
  String get arrivedBody => 'Has llegado a tu destino.';

  @override
  String get arrivedFeedbackPrompt => '¿Qué tal?';

  @override
  String get feedbackBad => 'Mal';

  @override
  String get feedbackGood => '¡Bien!';

  @override
  String get feedbackDialogTitle => 'Cuéntanos qué salió mal';

  @override
  String get feedbackHint => 'Describe el problema…';

  @override
  String get feedbackSent => 'Comentario enviado — ¡gracias! 🙏';

  @override
  String get feedbackSubmit => 'Enviar';

  @override
  String get transportModeCar => 'Coche';

  @override
  String get transportModeWalk => 'A pie';

  @override
  String etaArrivalLabel(String time) {
    return 'Lleg. $time';
  }

  @override
  String get supportRoadstr => 'Apoya Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copiado al portapapeles';
  }

  @override
  String get disclaimerTitle => 'Aviso importante';

  @override
  String get disclaimerAccept => 'He leído y acepto';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Leer en Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Buscar en $engine';
  }

  @override
  String get plannerFromHint => 'Desde…';

  @override
  String get plannerToHint => 'Destino…';

  @override
  String departEta(String dep, String arr) {
    return 'Salida $dep  →  Llegada $arr';
  }

  @override
  String get modeCar => 'Coche';

  @override
  String get modeBike => 'Bici';

  @override
  String get modeWalk => 'A pie';

  @override
  String windSpeed(String speed) {
    return 'viento $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m min';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}h ${m}min';
  }

  @override
  String get weatherClear => 'Despejado';

  @override
  String get weatherPartlyCloudy => 'Prt. nublado';

  @override
  String get weatherCloudy => 'Nublado';

  @override
  String get weatherFog => 'Niebla';

  @override
  String get weatherLightRain => 'Lluvia ligera';

  @override
  String get weatherRain => 'Lluvia';

  @override
  String get weatherSnow => 'Nieve';

  @override
  String get weatherShowers => 'Chubascos';

  @override
  String get weatherThunderstorm => 'Tormenta';

  @override
  String get ztlAheadWarning =>
      'Zona ZTL adelante — la ruta entra en área restringida';

  @override
  String get ztlInsideWarning => 'Zona de tráfico limitado';

  @override
  String get onboardingAppSubtitle => 'Navegación Nostr de código abierto';

  @override
  String get onboardingWelcomeTitle => 'Bienvenido';

  @override
  String get onboardingWelcomeBody =>
      'Todo lo que necesita una app de navegación — sin renunciar a tu privacidad.';

  @override
  String get onboardingFeatureNav => 'Navegación GPS giro a giro';

  @override
  String get onboardingFeatureNostr =>
      'Eventos viales Nostr (radares, peligros, tráfico)';

  @override
  String get onboardingFeatureLightning =>
      'Propinas Lightning Network para reporteros de eventos';

  @override
  String get onboardingFeatureVoice =>
      'Guía de voz IA en el dispositivo (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Sin cuenta necesaria — sin rastreo, nunca';

  @override
  String get onboardingGetStarted => 'Empezar';

  @override
  String get onboardingNostrTitle => 'Identidad Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Opcional — conéctate para informar eventos viales, confirmar alertas y ganar propinas Lightning.';

  @override
  String get onboardingNostrConnected => 'Conectado';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (recomendado)';

  @override
  String get onboardingAmberSubtitle =>
      'Conéctate con la app firmante Amber. Tu clave privada nunca sale de Amber.';

  @override
  String get onboardingNsecTitle => 'Clave nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Pega tu clave privada. Almacenada en el Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Clave nsec inválida — comprueba e inténtalo de nuevo.';

  @override
  String get onboardingSkip => 'Omitir por ahora';

  @override
  String get onboardingContinue => 'Continuar';

  @override
  String get onboardingEnterNsec => 'Ingresa la clave nsec';

  @override
  String get onboardingSetupTitle => 'Configurar Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configura el acceso a la ubicación y la guía de voz opcional.';

  @override
  String get onboardingLocationTitle => 'Ubicación';

  @override
  String get onboardingLocationGranted => 'Acceso a ubicación concedido';

  @override
  String get onboardingLocationRequired => 'Requerido para la navegación GPS';

  @override
  String get onboardingGrantButton => 'Conceder';

  @override
  String get onboardingGrapheneTitle => 'Usuarios de GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Concede ubicación Precisa (no Aproximada) y permite acceso Siempre (no solo mientras se usa) en:\nAjustes → Aplicaciones → Roadstr → Permisos → Ubicación\n\nCon solo ubicación aproximada o \'solo mientras se usa\', la navegación GPS perderá la posición en segundo plano.';

  @override
  String get onboardingVoiceTitle => 'Guía de voz IA (opcional)';

  @override
  String get onboardingVoiceReady => 'Modelo de voz Kokoro listo';

  @override
  String get onboardingVoiceDownloading => 'Descargando modelo de voz…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Descarga el modelo IA Kokoro (82 MB) para voz en el dispositivo';

  @override
  String get onboardingVoiceChecking => 'Comprobando estado del modelo…';

  @override
  String get onboardingDownloadButton => 'Descargar';

  @override
  String get onboardingVoiceLaterHint =>
      'También puedes descargar el modelo de voz más tarde desde\nAjustes → Voz de navegación.';

  @override
  String get onboardingReadyTitle => '¡Estás listo para navegar!';

  @override
  String get onboardingReadyBody =>
      'Roadstr abrirá el mapa ahora.\nPuedes configurar todo lo demás en Ajustes.';

  @override
  String get onboardingLetsGo => '¡Vamos!';

  @override
  String get onboardingProfileLoading => 'Cargando perfil…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Voice';

  @override
  String get kokoroVoiceFemale => 'Female';

  @override
  String get kokoroVoiceMale => 'Male';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Male voice not available for this language';

  @override
  String get kokoroSpeedTitle => 'Speech speed';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Optional: your saved favorites can sync across your devices through the Nostr relays, end-to-end encrypted (NIP-44) with your own key — relays only ever see ciphertext, and nobody but you can read the contents. Enable it anytime from Settings.';

  @override
  String get parkingSaveHere => 'Save parking here';

  @override
  String get parkingMarkerTitle => 'Parking spot';

  @override
  String get parkingNavigateHere => 'Navigate to parking';

  @override
  String get parkingRemove => 'Remove parking';

  @override
  String get parkingSavedSnack => 'Parking spot saved';

  @override
  String get parkingRemovedSnack => 'Parking spot removed';

  @override
  String get exportFavoritesTitle => 'Export favorites';

  @override
  String get exportFavoritesDesc =>
      'Save your favorite places to a JSON file you can back up or move to another device.';

  @override
  String get exportEncryptToggle => 'Encrypt with a password';

  @override
  String get exportPasswordHint => 'Password';

  @override
  String get exportButton => 'Export';

  @override
  String get exportSuccessSnack => 'Favorites exported';

  @override
  String get exportFailedSnack => 'Export failed';

  @override
  String get importFavoritesTitle => 'Import favorites';

  @override
  String get importButton => 'Import';

  @override
  String get importPasswordPrompt =>
      'This file is encrypted — enter the password';

  @override
  String importSuccessSnack(int n) {
    return '$n favorites imported';
  }

  @override
  String get importFailedSnack => 'Import failed — check the file and password';

  @override
  String get syncFavoritesTitle => 'Sync favorites';

  @override
  String get syncFavoritesDesc =>
      'Publish your favorites, end-to-end encrypted, to your Nostr relays so they follow you across devices. Relays only ever see ciphertext — nobody but you can read the contents.';

  @override
  String get syncFavoritesEnable => 'Enable sync';

  @override
  String get syncNowButton => 'Push to Nostr';

  @override
  String get syncPullButton => 'Pull from Nostr';

  @override
  String get syncPushingStatus => 'Publishing…';

  @override
  String get syncPullingStatus => 'Fetching…';

  @override
  String get syncSuccessSnack => 'Favorites synced';

  @override
  String get syncFailedSnack => 'Sync failed';

  @override
  String get syncNotAvailableAmber =>
      'Encrypted sync isn\'t available with Amber sign-in yet';

  @override
  String syncLastSyncLabel(String when) {
    return 'Last synced: $when';
  }

  @override
  String get syncNoIdentity =>
      'Sign in with Nostr (Settings → Profile) to enable sync';

  @override
  String get syncPullConfirmTitle => 'Replace local favorites?';

  @override
  String get syncPullConfirmBody =>
      'This will merge favorites fetched from Nostr with the ones already on this device.';

  @override
  String get onboardingVpnNotice =>
      'Para máxima privacidad — los avisos se propagan por la red Nostr — se recomienda usar una VPN. Mullvad, pagable con Bitcoin, es la opción recomendada.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Para un funcionamiento fiable, configura el permiso de ubicación de la app en «Permitir siempre», no solo mientras la app está en uso.';
}
