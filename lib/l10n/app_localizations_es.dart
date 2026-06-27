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
  String get sectionPrivacy => 'Privacidad';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Idioma';

  @override
  String get themeLightNostr => 'Claro · Nostr Violeta';

  @override
  String get themeLightBitcoin => 'Claro · Bitcoin Naranja';

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
  String get privacyMode => 'Modo privacidad';

  @override
  String get privacyModeDescription => 'No enviar datos de telemetría anónimos';

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
}
