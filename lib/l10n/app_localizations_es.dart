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
  String get bottomBarNotifications => 'Notifications';

  @override
  String get bottomBarProfile => 'Perfil';

  @override
  String get bottomBarMenu => 'Menú';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get notificationsEmpty => 'No notifications yet';

  @override
  String get notificationsEmptyBody =>
      'Zaps and reactions to your road reports will appear here.';

  @override
  String get notificationsLoginRequired => 'Connect your Nostr profile';

  @override
  String get notificationsLoginRequiredBody =>
      'Sign in with Amber or nsec to receive notifications from other users.';

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
  String get autoCenterOnLaunch => 'Centrar en mi posición al iniciar';

  @override
  String get autoCenterOnLaunchDesc =>
      'Usa el GPS automáticamente solo si el permiso de ubicación ya ha sido concedido';

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
  String get notifZapTitle => 'Zap recibido';

  @override
  String notifZapBody(int sat) {
    return '¡Has recibido un zap de $sat sats!';
  }

  @override
  String get notifConfirmedTitle => 'Reporte confirmado';

  @override
  String notifConfirmedBody(String category) {
    return 'Tu reporte de $category fue confirmado por otro conductor';
  }

  @override
  String get notifDeniedTitle => 'Reporte cuestionado';

  @override
  String notifDeniedBody(String category) {
    return 'Alguien indicó que tu reporte de $category ya no está';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, luego $second';
  }

  @override
  String get reportSpeedLimitHint => 'Límite de velocidad (opcional)';

  @override
  String get reportedSpeedLimit => 'Límite de velocidad informado';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Radar informado, límite de velocidad $limit $unit';
  }

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
      'Roadstr es una aplicación de navegación experimental, de código abierto y mantenida por la comunidad, basada en datos de OpenStreetMap y en el protocolo Nostr, puesta a disposición para su uso en cualquier país. Al descargar, instalar o utilizar esta aplicación, el usuario acepta incondicionalmente la totalidad de los siguientes términos, sin limitación territorial.\n\n🚗 LA SEGURIDAD VIAL ANTE TODO\nEl conductor debe mantener en todo momento la vista en la carretera y cumplir con todas las leyes de tráfico aplicables y la señalización vigente, que siempre prevalecen sobre cualquier indicación de la aplicación. Nunca debe manipularse el dispositivo mientras se conduce; asegúrelo en un soporte homologado y visible antes de ponerse en marcha, y nunca desvíe la atención de la carretera para interactuar con él mientras el vehículo esté en movimiento.\n\n⚠️ ASUNCIÓN DE RIESGO — A NIVEL MUNDIAL\nAl utilizar Roadstr, en cualquier país y bajo cualquier ordenamiento jurídico, el usuario asume consciente y voluntariamente TODOS los riesgos relacionados con su uso, incluidos, entre otros: accidentes de tráfico, lesiones personales, muerte, daños materiales, daños al vehículo, multas, sanciones administrativas, remolque, inmovilización del vehículo, responsabilidad penal, o cualquier otra consecuencia derivada directa o indirectamente de la confianza depositada en la aplicación. El usuario es el único responsable de cada decisión de conducción y navegación.\n\n🚫 SIN GARANTÍA\nRoadstr se proporciona estrictamente \"TAL CUAL\" y \"SEGÚN DISPONIBILIDAD\", sin garantía de ningún tipo, ya sea expresa, implícita o legal, incluidas, entre otras, las garantías de exactitud, integridad, fiabilidad, disponibilidad, comerciabilidad, idoneidad para un fin determinado y no infracción. Los datos cartográficos, el cálculo de rutas, los límites de velocidad, los radares y la información sobre zonas de tráfico restringido (ZTL/ZAC/LTZ) proceden de fuentes abiertas mantenidas por la comunidad (OpenStreetMap, Overpass API) que pueden estar incompletas, desactualizadas o ser inexactas para cualquier país, región o municipio, en cualquier momento y sin previo aviso. El usuario es el único responsable de verificar de forma independiente, antes y durante el desplazamiento, la legalidad y accesibilidad de cualquier ruta sugerida cotejándola con la señalización y la normativa local oficial.\n\n📍 PRECISIÓN Y GPS\nEl posicionamiento GPS puede ser inexacto o no estar disponible. Todas las indicaciones, distancias y alertas se proporcionan únicamente a modo orientativo y nunca deben utilizarse como única base para una decisión de conducción.\n\n🛡️ LIMITACIÓN DE RESPONSABILIDAD\nEn la medida máxima permitida por la legislación aplicable en cualquier jurisdicción, los desarrolladores, colaboradores y cualquier parte involucrada en la creación o distribución de Roadstr no serán responsables de ningún daño directo, indirecto, incidental, consecuente, especial, ejemplar o punitivo de ningún tipo —incluidas lesiones personales, muerte o pérdidas económicas— derivado del uso o de la imposibilidad de uso de la aplicación, incluso si se hubiera advertido de la posibilidad de tales daños. Cuando una jurisdicción no permita la totalidad o parte de esta limitación, la responsabilidad quedará limitada en la medida mínima permitida por la ley de dicha jurisdicción.\n\n🤝 INDEMNIZACIÓN\nEl usuario se compromete a indemnizar y mantener indemnes a los desarrolladores y colaboradores frente a cualquier reclamación, daño, pérdida o gasto (incluidos los honorarios legales) derivados del uso de la aplicación por parte del usuario o del incumplimiento de estos términos.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ DIVISIBILIDAD\nSi alguna disposición de estos términos resultara inaplicable en una jurisdicción determinada, dicha disposición se limitará o separará en la medida mínima necesaria, y todas las demás disposiciones permanecerán en pleno vigor y efecto.\n\nAl utilizar Roadstr, en cualquier parte del mundo, el usuario confirma haber leído, comprendido y aceptado incondicionalmente todos los términos anteriores, y asume la responsabilidad plena y completa —y todo el riesgo— por el uso de la aplicación y cualquier consecuencia que se derive de ello.';

  @override
  String get readOnWikipedia => 'Leer en Wikipedia';

  @override
  String get openInBrowser => 'Abrir en el navegador';

  @override
  String get wikipediaLoadFailed =>
      'No se pudo cargar Wikipedia de forma segura.';

  @override
  String get retry => 'Reintentar';

  @override
  String get poiDetailsFromOsm => 'Información de OpenStreetMap';

  @override
  String get poiCategory => 'Categoría';

  @override
  String get poiOperator => 'Operador';

  @override
  String get poiCuisine => 'Cocina';

  @override
  String get poiAccessibility => 'Accesibilidad';

  @override
  String get poiWheelchairYes => 'Accesible en silla de ruedas';

  @override
  String get poiWheelchairLimited => 'Acceso limitado en silla de ruedas';

  @override
  String get poiWheelchairNo => 'No accesible en silla de ruedas';

  @override
  String get poiContact => 'Contacto';

  @override
  String get poiAddress => 'Dirección';

  @override
  String get poiWebsite => 'Sitio web';

  @override
  String get poiAccessPrivate => 'Acceso privado';

  @override
  String get poiAccessCustomers => 'Solo clientes';

  @override
  String get poiAccessPermit => 'Se requiere permiso';

  @override
  String get poiAccessNo => 'Sin acceso público';

  @override
  String get poiAccessDestination => 'Acceso solo al destino';

  @override
  String get poiLightningAccepted => 'Acepta Lightning';

  @override
  String get poiBitcoinAccepted => 'Acepta Bitcoin';

  @override
  String get poiParkingDetails => 'Aparcamiento';

  @override
  String get poiParkingSurface => 'En superficie';

  @override
  String get poiParkingUnderground => 'Subterráneo';

  @override
  String get poiParkingMultiStorey => 'De varias plantas';

  @override
  String get poiParkingStreetSide => 'Junto a la calle';

  @override
  String get poiParkingLane => 'En la calle';

  @override
  String get poiParkingRooftop => 'En la azotea';

  @override
  String get poiFee => 'Tarifa';

  @override
  String get poiFree => 'Gratis';

  @override
  String get poiPaid => 'De pago';

  @override
  String get poiCapacity => 'Capacidad';

  @override
  String get poiMaxStay => 'Estancia máxima';

  @override
  String get poiPrice => 'Precio';

  @override
  String get poiChargingDetails => 'Carga';

  @override
  String get poiConnectorType2 => 'Tipo 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Diésel';

  @override
  String get poiPetrol95 => 'Gasolina 95';

  @override
  String get poiSmokingAllowed => 'Se permite fumar';

  @override
  String get poiSmokingOutside => 'Fumar en el exterior';

  @override
  String get poiSmokingAreas => 'Zonas para fumadores';

  @override
  String get poiSmokeFree => 'Sin humo';

  @override
  String get poiOutdoorSeating => 'Asientos al aire libre';

  @override
  String get poiTakeaway => 'Para llevar';

  @override
  String get poiTakeawayOnly => 'Solo para llevar';

  @override
  String get gpsSignalLost => 'Señal GPS perdida';

  @override
  String get poiOpenNow => 'Abierto ahora';

  @override
  String get poiClosedNow => 'Cerrado';

  @override
  String poiOpensAt(String when) {
    return 'Abre: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Cierra: $when';
  }

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
    return 'viento $speed';
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
      'Zona de tráfico limitado por delante — la ruta entra en ella';

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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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
  String get kokoroVoiceGenderTitle => 'Voz';

  @override
  String get kokoroVoiceFemale => 'Femenina';

  @override
  String get kokoroVoiceMale => 'Masculina';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Voz masculina no disponible para este idioma';

  @override
  String get kokoroSpeedTitle => 'Velocidad de voz';

  @override
  String get kokoroVolumeTitle => 'Volumen de voz';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Opcional: tus favoritos guardados pueden sincronizarse entre tus dispositivos a través de los relés Nostr, cifrados de extremo a extremo (NIP-44) con tu propia clave — los relés solo ven texto cifrado y nadie más que tú puede leer el contenido. Actívalo cuando quieras desde Ajustes.';

  @override
  String get parkingSaveHere => 'Guardar aparcamiento aquí';

  @override
  String get parkingMarkerTitle => 'Lugar de aparcamiento';

  @override
  String get parkingNavigateHere => 'Navegar al aparcamiento';

  @override
  String get parkingRemove => 'Eliminar aparcamiento';

  @override
  String get parkingSavedSnack => 'Aparcamiento guardado';

  @override
  String get parkingRemovedSnack => 'Aparcamiento eliminado';

  @override
  String get exportFavoritesTitle => 'Exportar favoritos';

  @override
  String get exportFavoritesDesc =>
      'Guarda tus lugares favoritos en un archivo JSON que puedes respaldar o mover a otro dispositivo.';

  @override
  String get exportEncryptToggle => 'Cifrar con contraseña';

  @override
  String get exportPasswordHint => 'Contraseña';

  @override
  String get exportButton => 'Exportar';

  @override
  String get exportSuccessSnack => 'Favoritos exportados';

  @override
  String get exportFailedSnack => 'La exportación falló';

  @override
  String get importFavoritesTitle => 'Importar favoritos';

  @override
  String get importPasswordPrompt =>
      'Este archivo está cifrado — introduce la contraseña';

  @override
  String importSuccessSnack(int n) {
    return '$n favoritos importados';
  }

  @override
  String get importFailedSnack =>
      'La importación falló — comprueba el archivo y la contraseña';

  @override
  String get syncFavoritesTitle => 'Sincronizar favoritos';

  @override
  String get syncFavoritesDesc =>
      'Publica tus favoritos, cifrados de extremo a extremo, en tus relés Nostr para que te sigan en todos tus dispositivos. Los relés solo ven texto cifrado — nadie más que tú puede leer el contenido.';

  @override
  String get syncNowButton => 'Enviar a Nostr';

  @override
  String get syncPullButton => 'Recuperar de Nostr';

  @override
  String get syncPassphraseTitle =>
      'Frase de contraseña de sincronización (opcional)';

  @override
  String get syncPassphraseDesc =>
      'Segunda capa de cifrado para los favoritos sincronizados: aunque tu clave Nostr se viera comprometida, la copia en los relays sigue siendo ilegible sin esta frase. La introducirás una vez en cada dispositivo nuevo. Déjala vacía para desactivarla.';

  @override
  String get syncSuccessSnack => 'Favoritos sincronizados';

  @override
  String get syncFailedSnack => 'La sincronización falló';

  @override
  String syncLastSyncLabel(String when) {
    return 'Última sincronización: $when';
  }

  @override
  String get syncNoIdentity =>
      'Inicia sesión con Nostr (Ajustes → Perfil) para activar la sincronización';

  @override
  String get onboardingVpnNotice =>
      'Para máxima privacidad — los avisos se propagan por la red Nostr — se recomienda usar una VPN. Mullvad, pagable con Bitcoin, es la opción recomendada.';

  @override
  String get trafficNormal => 'Tráfico normal';

  @override
  String get trafficModerate => 'Tráfico moderado';

  @override
  String get trafficHeavy => 'Tráfico intenso';

  @override
  String get avoidHighwaysChip => 'Evitar autopistas';

  @override
  String get avoidTollsChip => 'Evitar peajes';

  @override
  String get preferShorterChip => 'Ruta más corta';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Ver vista previa de la ruta';

  @override
  String get avoidHighwaysAndTolls => 'Evitar autopistas y peajes';

  @override
  String get avoidRouteUnavailable =>
      'No se encontró una ruta que evite tanto las autopistas como las carreteras de peaje.';

  @override
  String get avoidanceUnavoidableSection =>
      'Minimiza autopistas/peajes · tramo inevitable';
}
