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
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Idioma';

  @override
  String get themeLightNostr => 'Claro · Nostr Violeta';

  @override
  String get themeLightBitcoin => 'Claro · Bitcoin Laranja';

  @override
  String get themeDarkNostr => 'Escuro · Nostr Violeta';

  @override
  String get themeDarkBitcoin => 'Escuro · Bitcoin Laranja';

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

  @override
  String get sectionFavorites => 'Locais salvos';

  @override
  String get addFavorite => 'Adicionar local';

  @override
  String get favoriteLabelHint => 'Nome (ex. Casa, Escritório)';

  @override
  String get favoriteAddressHint => 'Endereço';

  @override
  String get favoriteGeocodingError =>
      'Endereço não encontrado. Tenta um endereço mais específico.';

  @override
  String get trafficAlertTitle => 'Novo tráfego na rota';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category reportado $age na sua rota.\n\nDeseja encontrar uma rota alternativa?';
  }

  @override
  String get trafficContinue => 'Continuar';

  @override
  String get trafficRecalculate => 'Recalcular rota';

  @override
  String get navExitTitle => 'Sair da navegação?';

  @override
  String get navExitBody => 'Deseja parar a navegação e voltar ao mapa?';

  @override
  String get navContinue => 'Continuar navegação';

  @override
  String get navExit => 'Sim, sair';

  @override
  String get loadingInfo => 'A carregar informações…';

  @override
  String get conditionsOnRoute => 'Condições na rota';

  @override
  String get calculateRoute => 'Calcular rota';

  @override
  String get sectionNavigationVoice => 'Voz de navegação';

  @override
  String get voiceGuidance => 'Orientação por voz';

  @override
  String get voiceGuidanceDesc =>
      'Ler instruções de direção em voz alta durante a navegação';

  @override
  String get testVoiceEngine => 'Testar motor de voz';

  @override
  String get testVoiceEngineDesc =>
      'Toque para verificar o motor TTS e obter instruções de configuração';

  @override
  String get ttsDialogTitle => 'Motor de voz em falta';

  @override
  String get ttsDialogBody =>
      'Não foi encontrado nenhum motor Text-to-Speech funcional.\n\nO Roadstr depende apenas de software de código aberto — instale um destes motores gratuitos a partir da F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Voz natural, lista de idiomas limitada';

  @override
  String get ttsEspeakDesc => 'Cobre mais de 100 idiomas, voz robótica';

  @override
  String get ttsInstallNote =>
      '⚠️ Após a instalação:\n1. Definições Android → Acessibilidade → Conversão de texto em voz\n2. Selecione o motor que acabou de instalar\n3. Transfira os dados de voz do seu idioma\n4. Reinicie o Roadstr completamente';

  @override
  String get ttsTestNow => 'Testar agora';

  @override
  String get voiceUnsupportedTitle => 'Orientação por voz indisponível';

  @override
  String get voiceUnsupportedBody =>
      'O seu idioma ainda não é suportado para indicações por voz. As instruções de navegação continuarão a aparecer como texto no ecrã.';

  @override
  String get kokoroModelTitle => 'Modelo de voz (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Não baixado · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Baixando...';

  @override
  String get kokoroModelStatusReady => 'Modelo de voz pronto';

  @override
  String get kokoroModelDownloadBtn => 'Baixar';

  @override
  String get kokoroModelSupportedLangs =>
      'Suporta: italiano, inglês, espanhol, francês, japonês, chinês, português';

  @override
  String get autoDarkMode => 'Tema escuro automático';

  @override
  String get autoDarkModeDesc => 'Ativa o tema escuro ao pôr do sol';

  @override
  String get settingsImperialUnits => 'Unidades imperiais';

  @override
  String get settingsImperialUnitsDesc =>
      'Milhas e pés em vez de quilómetros e metros';

  @override
  String get arrivedTitle => '🎉 Chegou!';

  @override
  String get arrivedBody => 'Você chegou ao seu destino.';

  @override
  String get arrivedFeedbackPrompt => 'Como foi?';

  @override
  String get feedbackBad => 'Mal';

  @override
  String get feedbackGood => 'Bem!';

  @override
  String get feedbackDialogTitle => 'Diga-nos o que correu mal';

  @override
  String get feedbackHint => 'Descreva o problema…';

  @override
  String get feedbackSent => 'Comentário enviado — obrigado! 🙏';

  @override
  String get feedbackSubmit => 'Enviar';

  @override
  String get transportModeCar => 'Carro';

  @override
  String get transportModeWalk => 'A pé';

  @override
  String etaArrivalLabel(String time) {
    return 'Cheg. $time';
  }

  @override
  String get supportRoadstr => 'Apoie o Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copiado para a área de transferência';
  }

  @override
  String get disclaimerTitle => 'Aviso importante';

  @override
  String get disclaimerAccept => 'Li e aceito';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Ler na Wikipédia';

  @override
  String searchOnEngine(String engine) {
    return 'Pesquisar no $engine';
  }

  @override
  String get plannerFromHint => 'De…';

  @override
  String get plannerToHint => 'Destino…';

  @override
  String departEta(String dep, String arr) {
    return 'Partida $dep  →  Chegada $arr';
  }

  @override
  String get modeCar => 'Carro';

  @override
  String get modeBike => 'Bicicleta';

  @override
  String get modeWalk => 'A pé';

  @override
  String windSpeed(String speed) {
    return 'vento $speed km/h';
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
  String get weatherClear => 'Limpo';

  @override
  String get weatherPartlyCloudy => 'Prt. nublado';

  @override
  String get weatherCloudy => 'Nublado';

  @override
  String get weatherFog => 'Nevoeiro';

  @override
  String get weatherLightRain => 'Chuva fraca';

  @override
  String get weatherRain => 'Chuva';

  @override
  String get weatherSnow => 'Neve';

  @override
  String get weatherShowers => 'Aguaceiros';

  @override
  String get weatherThunderstorm => 'Tempestade';

  @override
  String get ztlAheadWarning =>
      'Zona ZTL à frente — rota entra em área restrita';

  @override
  String get ztlInsideWarning => 'Zona de tráfego limitado';

  @override
  String get onboardingAppSubtitle => 'Navegação Nostr de código aberto';

  @override
  String get onboardingWelcomeTitle => 'Bem-vindo';

  @override
  String get onboardingWelcomeBody =>
      'Tudo o que um app de navegação precisa — sem abrir mão da sua privacidade.';

  @override
  String get onboardingFeatureNav => 'Navegação GPS curva a curva';

  @override
  String get onboardingFeatureNostr =>
      'Eventos viários Nostr (radares, perigos, trânsito)';

  @override
  String get onboardingFeatureLightning =>
      'Gorjetas Lightning Network para repórteres de eventos';

  @override
  String get onboardingFeatureVoice =>
      'Orientação de voz IA no dispositivo (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Nenhuma conta necessária — sem rastreamento, nunca';

  @override
  String get onboardingGetStarted => 'Começar';

  @override
  String get onboardingNostrTitle => 'Identidade Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Opcional — conecte-se para reportar eventos viários, confirmar alertas e ganhar gorjetas Lightning.';

  @override
  String get onboardingNostrConnected => 'Conectado';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (recomendado)';

  @override
  String get onboardingAmberSubtitle =>
      'Conecte-se com o app assinante Amber. Sua chave privada nunca sai do Amber.';

  @override
  String get onboardingNsecTitle => 'Chave nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Cole sua chave privada. Armazenada no Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Chave nsec inválida — verifique e tente novamente.';

  @override
  String get onboardingSkip => 'Pular por agora';

  @override
  String get onboardingContinue => 'Continuar';

  @override
  String get onboardingEnterNsec => 'Digite a chave nsec';

  @override
  String get onboardingSetupTitle => 'Configurar Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure o acesso à localização e o guia de voz opcional.';

  @override
  String get onboardingLocationTitle => 'Localização';

  @override
  String get onboardingLocationGranted => 'Acesso à localização concedido';

  @override
  String get onboardingLocationRequired => 'Necessário para navegação GPS';

  @override
  String get onboardingGrantButton => 'Conceder';

  @override
  String get onboardingGrapheneTitle => 'Usuários do GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Conceda localização Precisa (não Aproximada) e permita acesso Sempre (não só ao usar) em:\nConfigurações → Apps → Roadstr → Permissões → Localização\n\nCom apenas localização aproximada ou \'somente ao usar\', a navegação GPS perderá a posição em segundo plano.';

  @override
  String get onboardingVoiceTitle => 'Orientação de voz IA (opcional)';

  @override
  String get onboardingVoiceReady => 'Modelo de voz Kokoro pronto';

  @override
  String get onboardingVoiceDownloading => 'Baixando modelo de voz…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Baixe o modelo IA Kokoro (82 MB) para voz no dispositivo';

  @override
  String get onboardingVoiceChecking => 'Verificando status do modelo…';

  @override
  String get onboardingDownloadButton => 'Baixar';

  @override
  String get onboardingVoiceLaterHint =>
      'Você também pode baixar o modelo de voz depois em\nConfigurações → Voz de navegação.';

  @override
  String get onboardingReadyTitle => 'Você está pronto para navegar!';

  @override
  String get onboardingReadyBody =>
      'Roadstr abrirá o mapa agora.\nVocê pode configurar tudo mais em Configurações.';

  @override
  String get onboardingLetsGo => 'Vamos!';

  @override
  String get onboardingProfileLoading => 'Carregando perfil…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get onboardingVpnNotice =>
      'Para máxima privacidade — os alertas são propagados pela rede Nostr — recomenda-se o uso de uma VPN. Mullvad, pagável em Bitcoin, é a escolha recomendada.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Para um funcionamento fiável, defina a permissão de localização da app como \"Permitir sempre\", não apenas durante a utilização.';
}
