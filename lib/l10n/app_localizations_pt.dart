// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Portuguese (`pt`).
class AppLocalizationsPt extends AppLocalizations {
  AppLocalizationsPt([String locale = 'pt']) : super(locale);

  @override
  String get searchHint => 'Para onde quer ir?';

  @override
  String get calculatingRoute => 'Calculando rota…';

  @override
  String get routeNotFoundTitle => 'Rota não encontrada';

  @override
  String get noRouteFound => 'Nenhuma rota encontrada. Verifique sua conexão.';

  @override
  String get emptyServerResponse =>
      'Resposta do servidor vazia. Verifique sua conexão.';

  @override
  String routeError(String error) {
    return 'Erro no cálculo da rota: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS indisponível — Configurações → App → Roadstr → Permissões → Localização';

  @override
  String get acquiringGps => 'Obtendo GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Servidor GraphHopper não configurado — usando OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Chave API do GraphHopper não configurada — usando OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Chave API do OpenRouteService não configurada — usando OSRM';

  @override
  String get chooseRoute => 'Escolher rota';

  @override
  String routeOptionsCount(int count) {
    return '$count opções';
  }

  @override
  String get cancel => 'Cancelar';

  @override
  String get startNavigation => 'Iniciar navegação';

  @override
  String get fastestRoute => 'Mais rápida';

  @override
  String get now => 'Agora';

  @override
  String get history => 'Histórico';

  @override
  String get clearHistory => 'Limpar';

  @override
  String get selectedPosition => 'Posição selecionada';

  @override
  String get bottomBarProfile => 'Perfil';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Configurações';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Mapa';

  @override
  String get sectionPrivacy => 'Privacidade';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Idioma';

  @override
  String get themeLightNostr => 'Claro · Nostr Violeta';

  @override
  String get themeLightBitcoin => 'Claro · Bitcoin Laranja';

  @override
  String get langSystem => 'Padrão do sistema';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Manter tela ligada';

  @override
  String get keepScreenOnDescription =>
      'Evita o modo de suspensão durante a navegação';

  @override
  String get rotateMap => 'Mapa segue a direção';

  @override
  String get rotateMapDescription =>
      'O mapa gira conforme a direção de condução';

  @override
  String get mapTileUrlLabel => 'URL dos tiles do mapa';

  @override
  String get routingProviderLabel => 'Provedor de roteamento';

  @override
  String get osrmProvider => 'OSRM (público, sem chave necessária)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (local/privado)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (chave API)';

  @override
  String get openrouteProvider => 'OpenRouteService (chave API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Chave API do GraphHopper (opcional)';

  @override
  String get verify => 'Verificar';

  @override
  String get graphhopperServerUrlRequired =>
      'Insira a URL do servidor antes de verificar.';

  @override
  String get successTitle => 'Sucesso';

  @override
  String get graphhopperServerReachable => 'Servidor GraphHopper acessível';

  @override
  String get errorTitle => 'Erro';

  @override
  String get close => 'Fechar';

  @override
  String get privacyMode => 'Modo privacidade';

  @override
  String get privacyModeDescription =>
      'Não enviar dados de telemetria anônimos';

  @override
  String get infoVersion => 'Versão';

  @override
  String get infoProtocol => 'Protocolo';

  @override
  String get infoMaps => 'Mapas';

  @override
  String get infoRouting => 'Roteamento';

  @override
  String get infoSource => 'Fonte';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (auto-hospedado)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (nuvem)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Perfil';

  @override
  String get notConnected => 'Não conectado';

  @override
  String get loginWithNostrTitle => 'ENTRAR COM NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'A chave privada nunca sai do seu dispositivo · Recomendado';

  @override
  String get nsecLoginOption => 'Inserir nsec';

  @override
  String get nsecLoginDescription =>
      'Chave privada armazenada localmente · Menos seguro';

  @override
  String get connectedViaAmber => 'Conectado via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Conectado via nsec';

  @override
  String get publicKeyLabel => 'CHAVE PÚBLICA';

  @override
  String get npubCopiedToClipboard =>
      'npub copiado para a área de transferência';

  @override
  String get logoutTitle => 'Desconectar';

  @override
  String get logoutConfirmation =>
      'Remover credenciais Nostr deste dispositivo?';

  @override
  String get logoutButton => 'Desconectar';

  @override
  String get nostrIdentityInfo =>
      'Com uma identidade Nostr você pode publicar alertas de trânsito, relatórios e pontos de interesse de forma descentralizada na rede Nostr, sem servidores centrais.';

  @override
  String get warningTitle => 'Aviso';

  @override
  String get nsecWarning =>
      'Você está prestes a inserir sua chave privada Nostr diretamente em um aplicativo. Qualquer pessoa com acesso físico ou remoto ao seu dispositivo pode lê-la e assumir permanentemente o controle da sua identidade Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  O método seguro é Amber (NIP-55): o nsec nunca sai do cofre do assinante do app.';

  @override
  String get nsecRiskAcknowledgment =>
      'Entendo o risco e quero continuar mesmo assim';

  @override
  String get continueButton => 'Continuar';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber é um assinante de aplicativo Android compatível com NIP-55. Sua chave privada fica isolada dentro do Amber e nunca é compartilhada.';

  @override
  String get requestKeyFromAmber => 'Solicitar chave pública ao Amber';

  @override
  String get amberNotFound =>
      'Amber não encontrado. Instale-o pela Play Store ou insira seu npub manualmente.';

  @override
  String get waitingForAmberResponse => 'Aguardando resposta do Amber…';

  @override
  String get pasteNpubManually => 'Cole seu npub manualmente:';

  @override
  String get confirmNpub => 'Confirmar npub';

  @override
  String get enterNsecTitle => 'Inserir nsec';

  @override
  String get loginButton => 'Entrar';

  @override
  String get invalidNpubTitle => 'npub inválido';

  @override
  String get invalidNsecTitle => 'nsec inválido';

  @override
  String get invalidNpubMessage =>
      'Certifique-se de ter colado o npub correto.';

  @override
  String get invalidNsecMessage =>
      'Certifique-se de ter colado o nsec correto.';

  @override
  String get amberResponseError => 'Erro de resposta do Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'ou';

  @override
  String get gpsNotActiveTitle => 'GPS não ativo';

  @override
  String get gpsDisabledMessage =>
      'Ative o GPS nas configurações do dispositivo para obter sua localização e usar a navegação.';

  @override
  String get openSettings => 'Configurações';

  @override
  String get myLocation => 'Minha localização';

  @override
  String get loginToReport =>
      'Entre com Nostr (seção Perfil) para reportar eventos';

  @override
  String get navigateHere => 'Navegar até aqui';

  @override
  String get reportEventHere => 'Reportar evento aqui';

  @override
  String get zapSendSats => 'Zap ⚡ (enviar sats)';

  @override
  String get sendZap => 'Enviar um Zap';

  @override
  String get chooseAmountSats => 'Escolha o valor em satoshi (sats):';

  @override
  String get customAmount => 'Valor personalizado…';

  @override
  String get zapSending => 'Enviando…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Obtendo endereço Lightning…';

  @override
  String get noLightningAddress => 'Este repórter não tem endereço Lightning';

  @override
  String get requestingInvoice => 'Solicitando fatura…';

  @override
  String get lnurlUnavailable => 'LNURL não disponível';

  @override
  String get invoiceFailed => 'Não foi possível gerar a fatura';

  @override
  String get openingWallet => 'Abrindo carteira…';

  @override
  String get payingViaNwc => 'Pagando via NWC…';

  @override
  String get noLightningWallet => 'Nenhuma carteira Lightning encontrada';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats enviados!';
  }

  @override
  String get stillThere => 'Ainda lá';

  @override
  String get notThereAnymore => 'Não está mais lá';

  @override
  String get loginToConfirm => 'Entre com Nostr para confirmar ou contestar';

  @override
  String get reportAnEvent => 'Reportar um evento';

  @override
  String get optionalComment => 'Comentário opcional…';

  @override
  String get publish => 'Publicar';

  @override
  String get publishing => 'Publicando…';

  @override
  String get reportPublished => 'Relatório publicado ✓';

  @override
  String get myReports => 'MEUS RELATÓRIOS';

  @override
  String get noReportsYet => 'Nenhum relatório publicado';

  @override
  String get zapBalance => 'Saldo Zap';

  @override
  String get satoshiFromReports => 'Satoshi recebidos dos seus relatórios';

  @override
  String get reputationHigh => 'Alta';

  @override
  String get reputationMedium => 'Média';

  @override
  String get reputationLow => 'Baixa';

  @override
  String reputationLabel(String level) {
    return 'Reputação $level';
  }

  @override
  String reliability(int pct) {
    return 'Confiabilidade: $pct%';
  }

  @override
  String get confirmedLabel => 'Confirmado';

  @override
  String get removedLabel => 'Removido';

  @override
  String get positionLabel => 'Posição';

  @override
  String get loadingLabel => 'Carregando…';

  @override
  String get sectionWebSearch => 'Pesquisa na web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Cole seu URI NWC (Alby Hub, Mutiny, Cashu…) para pagar Zaps diretamente do aplicativo.';

  @override
  String get searchEngineQwantDesc =>
      'Europeu, com privacidade em primeiro lugar. Sem rastreamento, sem perfis publicitários. Recomendado.';

  @override
  String get searchEngineBraveDesc =>
      'Índice independente, código aberto. Sem dependência do Google ou Bing. Zero perfilamento.';

  @override
  String get searchEngineDdgDesc =>
      'Focado em privacidade e popular. Resultados parcialmente do Bing — tenha isso em mente.';

  @override
  String get searchEngineStartpageDesc =>
      'Resultados do Google sem entregar seus dados ao Google. Um compromisso razoável.';

  @override
  String get searchEngineGoogleDesc =>
      'Muito eficaz. Mas lembre-se: o Google te conhece melhor que sua mãe e vende tudo para anunciantes. Sua escolha. 🍪';

  @override
  String get categoryPolice => 'Polícia';

  @override
  String get categorySpeedCamera => 'Radar de velocidade';

  @override
  String get categoryTrafficJam => 'Engarrafamento';

  @override
  String get categoryAccident => 'Acidente';

  @override
  String get categoryRoadClosure => 'Estrada fechada';

  @override
  String get categoryConstruction => 'Obras';

  @override
  String get categoryHazard => 'Perigo';

  @override
  String get categoryRoadCondition => 'Estado da estrada';

  @override
  String get categoryPothole => 'Buraco';

  @override
  String get categoryFog => 'Neblina';

  @override
  String get categoryIce => 'Gelo';

  @override
  String get categoryAnimal => 'Animal';

  @override
  String get categoryOther => 'Outro';

  @override
  String get dateTimeLabel => 'Data / hora';

  @override
  String minutesAgo(int count) {
    return 'há $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'há ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'há ${count}d';
  }
}
