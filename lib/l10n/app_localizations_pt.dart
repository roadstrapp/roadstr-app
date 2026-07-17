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
      'Roadstr é uma aplicação de navegação experimental, de código aberto e mantida pela comunidade, baseada em dados do OpenStreetMap e no protocolo Nostr, disponibilizada para utilização em qualquer país. Ao transferir, instalar ou utilizar esta aplicação, o utilizador aceita incondicionalmente a totalidade dos termos seguintes, sem limitação territorial.\n\n🚗 A SEGURANÇA RODOVIÁRIA EM PRIMEIRO LUGAR\nO condutor deve manter sempre os olhos na estrada e cumprir todas as leis de trânsito aplicáveis e a sinalização existente, que prevalecem sempre sobre qualquer indicação da aplicação. O dispositivo nunca deve ser manuseado durante a condução; deve ser fixado num suporte homologado e visível antes de iniciar a marcha, e a atenção nunca deve ser desviada da estrada para interagir com o dispositivo enquanto o veículo estiver em movimento.\n\n⚠️ ASSUNÇÃO DE RISCO — A NÍVEL MUNDIAL\nAo utilizar o Roadstr, em qualquer país e ao abrigo de qualquer sistema jurídico, o utilizador assume consciente e voluntariamente TODOS os riscos associados à sua utilização, incluindo, sem limitação: acidentes de trânsito, lesões pessoais, morte, danos materiais, danos no veículo, multas, sanções administrativas, reboque, apreensão do veículo, responsabilidade criminal, ou qualquer outra consequência decorrente, direta ou indiretamente, da confiança depositada na aplicação. O utilizador é o único responsável por cada decisão de condução e de navegação.\n\n🚫 SEM GARANTIA\nO Roadstr é disponibilizado estritamente \"TAL COMO SE ENCONTRA\" e \"CONFORME DISPONÍVEL\", sem qualquer garantia de qualquer tipo, expressa, implícita ou legal, incluindo, sem limitação, garantias de exatidão, integralidade, fiabilidade, disponibilidade, comerciabilidade, adequação a um fim específico e não violação de direitos de terceiros. Os dados cartográficos, o cálculo de rotas, os limites de velocidade, os radares e as informações sobre zonas de tráfego condicionado (ZTL/ZAC/LTZ) provêm de fontes abertas mantidas pela comunidade (OpenStreetMap, Overpass API), que podem estar incompletas, desatualizadas ou incorretas para qualquer país, região ou município, em qualquer momento e sem aviso prévio. Cabe exclusivamente ao utilizador verificar de forma independente, antes e durante a viagem, a legalidade e a acessibilidade de qualquer rota sugerida, confrontando-a com a sinalização e a regulamentação local oficial.\n\n📍 PRECISÃO E GPS\nO posicionamento por GPS pode ser impreciso ou estar indisponível. Todas as indicações, distâncias e alertas são fornecidos apenas a título indicativo e nunca devem ser utilizados como único fundamento para uma decisão de condução.\n\n🛡️ LIMITAÇÃO DE RESPONSABILIDADE\nNa máxima medida permitida pela legislação aplicável em cada jurisdição, os programadores, os colaboradores e qualquer parte envolvida na criação ou distribuição do Roadstr não serão responsáveis por quaisquer danos diretos, indiretos, incidentais, consequenciais, especiais, exemplares ou punitivos de qualquer natureza — incluindo lesões pessoais, morte ou perdas financeiras — decorrentes do uso ou da impossibilidade de uso da aplicação, mesmo que tenham sido alertados sobre a possibilidade de tais danos. Nas jurisdições que não permitam, total ou parcialmente, esta limitação, a responsabilidade fica limitada à extensão mínima permitida pela lei dessa jurisdição.\n\n🤝 INDEMNIZAÇÃO\nO utilizador compromete-se a indemnizar e a isentar de responsabilidade os programadores e colaboradores relativamente a qualquer reclamação, dano, perda ou despesa (incluindo honorários legais) decorrentes da utilização da aplicação pelo utilizador ou da violação destes termos.\n\n🔒 PRIVACIDADE\nNenhum dado de localização é transmitido aos servidores próprios do Roadstr. O cálculo de rotas é efetuado através de serviços de terceiros (OSRM, GraphHopper, OpenRouteService), aos quais são enviadas apenas as coordenadas de partida e de destino.\n\n⚖️ DIVISIBILIDADE\nCaso alguma disposição destes termos seja considerada inexequível numa determinada jurisdição, essa disposição será limitada ou separada na medida mínima necessária, permanecendo todas as restantes disposições em pleno vigor e efeito.\n\nAo utilizar o Roadstr, em qualquer parte do mundo, o utilizador confirma ter lido, compreendido e aceite incondicionalmente todos os termos acima, assumindo total e integralmente a responsabilidade — e todo o risco — pela utilização da aplicação e por qualquer consequência daí resultante.';

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
      'Zona de tráfego limitado à frente — a rota entra nela';

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
  String get kokoroVoiceGenderTitle => 'Voz';

  @override
  String get kokoroVoiceFemale => 'Feminina';

  @override
  String get kokoroVoiceMale => 'Masculina';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Voz masculina não disponível para este idioma';

  @override
  String get kokoroSpeedTitle => 'Velocidade da fala';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Opcional: os seus favoritos guardados podem sincronizar-se entre os seus dispositivos através dos relays Nostr, encriptados ponta a ponta (NIP-44) com a sua própria chave — os relays só veem texto cifrado e ninguém além de si pode ler o conteúdo. Ative quando quiser nas Definições.';

  @override
  String get parkingSaveHere => 'Guardar estacionamento aqui';

  @override
  String get parkingMarkerTitle => 'Lugar de estacionamento';

  @override
  String get parkingNavigateHere => 'Navegar até ao estacionamento';

  @override
  String get parkingRemove => 'Remover estacionamento';

  @override
  String get parkingSavedSnack => 'Estacionamento guardado';

  @override
  String get parkingRemovedSnack => 'Estacionamento removido';

  @override
  String get exportFavoritesTitle => 'Exportar favoritos';

  @override
  String get exportFavoritesDesc =>
      'Guarde os seus locais favoritos num ficheiro JSON que pode fazer backup ou transferir para outro dispositivo.';

  @override
  String get exportEncryptToggle => 'Encriptar com palavra-passe';

  @override
  String get exportPasswordHint => 'Palavra-passe';

  @override
  String get exportButton => 'Exportar';

  @override
  String get exportSuccessSnack => 'Favoritos exportados';

  @override
  String get exportFailedSnack => 'A exportação falhou';

  @override
  String get importFavoritesTitle => 'Importar favoritos';

  @override
  String get importPasswordPrompt =>
      'Este ficheiro está encriptado — introduza a palavra-passe';

  @override
  String importSuccessSnack(int n) {
    return '$n favoritos importados';
  }

  @override
  String get importFailedSnack =>
      'A importação falhou — verifique o ficheiro e a palavra-passe';

  @override
  String get syncFavoritesTitle => 'Sincronizar favoritos';

  @override
  String get syncFavoritesDesc =>
      'Publique os seus favoritos, encriptados ponta a ponta, nos seus relays Nostr para que o sigam em todos os dispositivos. Os relays só veem texto cifrado — ninguém além de si pode ler o conteúdo.';

  @override
  String get syncNowButton => 'Enviar para o Nostr';

  @override
  String get syncPullButton => 'Obter do Nostr';

  @override
  String get syncPassphraseTitle => 'Frase-senha de sincronização (opcional)';

  @override
  String get syncPassphraseDesc =>
      'Segunda camada de criptografia para os favoritos sincronizados: mesmo que a sua chave Nostr fosse comprometida, o snapshot nos relays permanece ilegível sem esta frase-senha. Você a insere uma vez em cada novo dispositivo. Deixe vazio para desativar.';

  @override
  String get syncSuccessSnack => 'Favoritos sincronizados';

  @override
  String get syncFailedSnack => 'A sincronização falhou';

  @override
  String syncLastSyncLabel(String when) {
    return 'Última sincronização: $when';
  }

  @override
  String get syncNoIdentity =>
      'Inicie sessão com Nostr (Definições → Perfil) para ativar a sincronização';

  @override
  String get onboardingVpnNotice =>
      'Para máxima privacidade — os alertas são propagados pela rede Nostr — recomenda-se o uso de uma VPN. Mullvad, pagável em Bitcoin, é a escolha recomendada.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Para um funcionamento fiável, defina a permissão de localização da app como \"Permitir sempre\", não apenas durante a utilização.';

  @override
  String get trafficNormal => 'Trânsito normal';

  @override
  String get trafficModerate => 'Trânsito moderado';

  @override
  String get trafficHeavy => 'Trânsito intenso';

  @override
  String get avoidHighwaysChip => 'Evitar autoestradas';

  @override
  String get avoidTollsChip => 'Evitar portagens';

  @override
  String get preferShorterChip => 'Rota mais curta';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Mostrar pré-visualização da rota';
}
