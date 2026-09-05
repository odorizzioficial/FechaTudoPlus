# Fecha Tudo Plus

Utilitário Android para controlar aplicativos em segundo plano, com o layout
"Kinetic Utility" preservado exatamente como foi enviado (cores, tipografia,
espaçamentos, cartões, navegação inferior e as quatro abas).

## Nome e pacote

O nome exibido é **Fecha Tudo Plus** (`app_name`, sem tradução). O identificador
do pacote continua `com.bgcontrol.plus`: trocá-lo faria o Android tratar a nova
versão como outro aplicativo, e o usuário perderia listas, agendamentos e
configurações na atualização.

## Como compilar

1. Abra a pasta do projeto no Android Studio (Ladybug ou mais recente).
2. Deixe o Gradle sincronizar — o wrapper baixa o Gradle 8.7 automaticamente.
   Se preferir a linha de comando, gere o wrapper uma vez com `gradle wrapper`
   (o `gradle-wrapper.jar` é binário e não pode ser incluído no código-fonte).
3. Gere o APK: `./gradlew assembleDebug`
   Saída em `app/build/outputs/apk/debug/app-debug.apk`.
4. Para o APK de lançamento assinado: `./gradlew assembleRelease` depois de
   configurar sua chave em `signingConfigs`.

Requisitos: JDK 17, compileSdk 35, minSdk 26, AGP 8.7.3, Gradle 8.9.

## Fontes

Inter e JetBrains Mono não acompanham o código-fonte por questão de licença de
distribuição. O tema usa as métricas exatas do design (tamanhos, pesos,
entrelinhas e espaçamento) com a sans-serif e a monoespaçada do sistema.

Para usar as fontes originais:

1. Baixe Inter (Regular, Medium, SemiBold, Bold) e JetBrains Mono (Medium).
2. Copie os `.ttf` para `app/src/main/res/font/` com estes nomes:
   `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`,
   `inter_bold.ttf`, `jetbrains_mono_medium.ttf`.
3. Em `ui/theme/Type.kt`, troque as duas famílias por:

```kotlin
val UiFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)
val MonoFontFamily = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))
```

Nada mais precisa mudar: todos os estilos já apontam para essas duas famílias.

## Arquitetura

MVVM com injeção manual em `AppContainer`.

```
ui/screens      as quatro telas
ui/components   cartões, selo de status, barra inferior, seletor de apps
ui/theme        cores, tipografia e formas do DESIGN.md
viewmodel       estado de cada aba
data/           Room (entities, dao, database) + repositório
preferences/    DataStore
shizuku/        ShizukuManager + serviço privilegiado (AIDL)
monitor/        leitura de processos e vigia dos apps bloqueados
util/           pacotes, formatação
```

## Dados

Tudo fica no aparelho: Room sobre SQLite (`bg_control_plus.db`) para as listas
de restritos e bloqueados, DataStore para as configurações. Sem conta, sem
login, sem servidor e sem envio de dados para a internet.

A regra de exclusividade entre Restritos e Bloqueados é aplicada no repositório
antes de qualquer inserção, e o nome do pacote é chave primária nas duas
tabelas, o que impede duplicação.

## O que é real e o que o Android limita

Nenhuma informação da interface é fabricada. Quando um dado não pode ser obtido,
aparece "—" e a tela explica o motivo.

**Com Shizuku autorizado**
- Lista real de processos e memória residente (`ps` com UID shell).
- Encerramento real de aplicativos (`am force-stop`), usado tanto pelo
  "Fechar Tudo" quanto pelo vigia dos bloqueados.
- Memória recuperada é medida antes de cada encerramento e só então somada.
- O "Fechar Tudo" dispara todos os force-stop em paralelo dentro de um único
  comando de shell: uma ida e volta de IPC em vez de uma por aplicativo.

**Por que a lista mostra o que mostra**

A aba lê a tabela de processos com `ps -A -o PID,RSS,CMDLINE`. A coluna é
CMDLINE, não NAME: o NAME do toybox vem do `comm` do kernel, cortado em 15
caracteres, e com ele todo pacote de nome mais longo chegava truncado, sem casar
com nenhum aplicativo instalado — boa parte da lista sumia. Onde CMDLINE não
existir, há uma leitura alternativa com NAME.

A lista mostra apenas o que pode mesmo ser encerrado: aplicativos do usuário,
com ou sem ícone na gaveta, e sem os processos persistentes do sistema. Apps do
sistema apareciam antes, mas o "Fechar Tudo" não os derrubava — um botão que não
cumpre o que promete é pior que a ausência dele. O filtro de ícone na gaveta
continua valendo no seletor, onde só faz sentido oferecer apps abríveis.

Vale notar a diferença para ferramentas que parecem listar mais: várias delas
mostram todos os aplicativos instalados e deixam você encerrar qualquer um. Aqui
a lista é do que está mesmo em memória, com a memória de cada processo.

**Sem Shizuku**
- Desde o Android 8 um aplicativo não enxerga processos de terceiros. A aba
  "Em execução" passa a listar apenas os apps usados recentemente (via acesso ao
  uso), sem memória, e a tela informa isso.
- O encerramento cai para `killBackgroundProcesses`, que o sistema pode ignorar.

**Acesso ao uso (PACKAGE_USAGE_STATS)**
- Concedido manualmente pelo usuário nas configurações do Android. Sem ele não é
  possível detectar qual app está em primeiro plano, e o bloqueio não funciona.

**Apps que se recusam a morrer**

Alguns aplicativos (o "Ligar ao Windows" é o caso clássico) voltam sozinhos
segundos depois do `force-stop`, porque o sistema os religa. O encerramento é
escalonado: encerra, confere na tabela de processos e, se o app voltou, mata os
processos em segundo plano e encerra de novo. O resultado exibido é o real.

Ao entrar na lista de Bloqueados, o app também recebe a restrição profunda de
segundo plano — `appops RUN_IN_BACKGROUND ignore`, `RUN_ANY_IN_BACKGROUND ignore`
e `standby-bucket restricted`, exatamente o que o Android aplica em
"Bateria > Restrito". É isso que impede o religamento automático. Tudo é
revertido ao desbloquear.

**Recentes**

O `force-stop` derruba o processo, mas em várias ROMs o cartão continua na tela
de recentes. Por isso o encerramento roda
`am force-stop; pm suspend --user 0; pm unsuspend --user 0` (Android 9+):
suspender e liberar o pacote faz o sistema descartar as tarefas dele.

Não se usa `pm disable-user` aqui. Desabilitar também limparia os recentes, mas
a tela inicial apaga os atalhos do pacote e reabilitar não os devolve — dano
permanente para o usuário. Suspender é reversível e não toca em atalhos, widgets
nem na gaveta. O `unsuspend` vem no mesmo comando e é repetido em seguida.

**Apps que o sistema reinicia sozinho**

Play Services, Play Store e componentes parecidos são reiniciados pelo framework
logo após o `force-stop`. Quando a verificação mostra que o processo voltou, a
tela diz isso em vez de fingir sucesso, e sugere bloquear o app — o bloqueio
aplica a restrição profunda de segundo plano, que é o único freio real.

**Bloqueio em segundo plano**
- O app bloqueado funciona normalmente enquanto está em primeiro plano. No
  instante em que sai, o vigia encerra o processo — sem período de carência.
- Enquanto um app bloqueado está aberto a verificação roda a cada 400 ms, para
  reagir na hora da saída. Sem nenhum app bloqueado em uso, o ritmo cai para 4 s.
- O `force-stop` derruba o processo e, na maioria das ROMs, remove também o
  cartão do app da tela de recentes. Isso depende do fabricante — não é algo que
  o aplicativo consiga forçar.
- Não existe forma de impedir 100% dos processos sem root; o app não promete
  isso em lugar nenhum da interface.

## Desempenho

- O mapa de aplicativos instalados é guardado por um minuto: montá-lo carrega o
  rótulo de cada pacote pelo PackageManager, e sem cache isso rodaria a cada
  2,5 segundos com a aba aberta.
- Ícones e a checagem de "app do sistema" ficam em cache de memória, então rolar
  a lista não relê o PackageManager a cada quadro.
- O APK de lançamento usa R8 em modo completo, além do encolhimento de recursos.

## Consumo de bateria

- O vigia só verifica o primeiro plano com a tela ligada; com a tela apagada o
  intervalo sobe para 60 segundos.
- O ritmo é adaptativo: 400 ms enquanto um app bloqueado está aberto, 4 s parado.
- A aba "Em execução" atualiza a cada 2,5 segundos apenas enquanto está visível,
  e relê a lista assim que o app volta ao primeiro plano, para que um aplicativo
  recém-aberto apareça na hora. O selo "APLICATIVOS" força uma releitura manual.

## Desinstalação e limpeza

`allowBackup` está desligado e não há regras de backup: ao desinstalar, o Android
apaga o banco, o DataStore e o cache, e nada volta depois por restauração da
nuvem. Enquanto o backup estava ligado, uma reinstalação podia trazer de volta
listas antigas.

## Primeira abertura

Ao abrir pela primeira vez, um diálogo apresenta as três autorizações de que o
app depende — acesso ao uso, otimização de bateria e Shizuku — com o estado real
de cada uma. Tocar em qualquer linha abre o fluxo oficial do Android, e o estado
é relido quando o usuário volta. Dá para sair em "Agora não" a qualquer momento.

## Aplicativos protegidos por padrão

Na primeira execução o app pergunta ao próprio Android quem exerce cada papel no
aparelho — tela inicial, discador, mensagens, teclado, assistente, relógio e
câmera — e protege esses pacotes automaticamente. Não é uma lista fixa: cada
fabricante usa nomes diferentes, então a resolução é feita por papel
(`TelecomManager`, `Telephony.Sms`, `Settings.Secure.DEFAULT_INPUT_METHOD` e
`resolveActivity`). Isso evita que o "Fechar Tudo" derrube algo essencial em
qualquer celular. Também entram protegidos os aplicativos da família Shizuku (Shizuku, Sui e
derivados), detectados pelo nome do pacote: encerrar o app que concede o acesso
privilegiado derrubaria as próprias funções do BG Control.

O usuário pode desativar ou remover qualquer um deles na aba Restritos, e o
botão de atualizar ao lado de "Apps Protegidos" devolve os padrões que tenham
sido removidos — sem tocar nas outras abas. Ver `util/DefaultProtectedApps.kt`.

## Aparência de vidro

A barra inferior flutuante usa a **Liquid-Glass-Android**
(`com.github.QWEA0:liquidglass`, via JitPack): refração SDF real, dispersão
cromática e brilho que acompanha a inclinação do aparelho.

Como a biblioteca é do sistema de Views, a `MainActivity` usa um layout XML:
uma `FrameLayout` com o `ComposeView` do app inteiro e, por cima, o
`LiquidGlassView` apontando para ele em `backdropSourceId`. Os ícones das abas
são um segundo `ComposeView` desenhado dentro do vidro. As duas ilhas de Compose
compartilham o mesmo estado de aba, então tocar na barra e deslizar entre telas
continuam sincronizados.

O restante das superfícies — cartões das quatro abas, cartões das Configurações,
janelas internas, selos, diálogos, folhas e os botões "Fechar Tudo" e
"Adicionar" — usa o vidro em Compose de `ui/theme/Glass.kt`: translucidez em
gradiente, brilho na aresta superior e borda de luz, imitando de perto o
acabamento da barra.

Superfícies coloridas (selos e botões) são marcadas com `accent = true`. Sem
isso, ao desligar o vidro a opacidade ia a 100% e o fundo ficava da mesma cor do
texto — o rótulo sumia.

O interruptor e a intensidade são do usuário: Configurações → Aparência → Efeito
vidro, com um controle de 0 a 100%. A 0% as superfícies ficam sólidas; a 100%
chegam à translucidez de projeto. Superfícies coloridas (selos e botões) não
seguem a intensidade: se ficassem sólidas, o fundo assumiria a cor do texto.
Desligado, a barra volta a ser desenhada em Compose e todas as superfícies do
app ficam sólidas — o estado fica salvo no DataStore.

Por que só a barra usa a biblioteca: cada `LiquidGlassView` captura o desenho do
fundo a cada quadro. Uma barra faz isso uma vez por frame; um cartão por
aplicativo em uma lista rolando faria dezenas de capturas por quadro e o app
travaria. As demais superfícies usam o vidro em Compose, que custa quase nada.

## Troca de idioma sem reiniciar a tela

O app não usa `AppCompatDelegate.setApplicationLocales`, que recria a Activity e
provoca uma piscada preta a cada troca. `ui/theme/AppLocale.kt` fornece um
`Context` e uma `Configuration` com o idioma escolhido pelos CompositionLocals,
então as strings mudam na própria recomposição — instantâneo e sem reinício.

## Idiomas e a armadilha do português

O `values/` padrão é o **inglês**, e o português do Brasil fica em
`values-pt-rBR`. Isso não é detalhe: quando o padrão era pt-BR, um aparelho em
pt-BR recebia os textos de `values-pt-rPT`, porque o Android considera qualquer
variante da mesma língua mais próxima que a pasta padrão. O resultado era um app
em português europeu ("A monitorizar", "Definições") para usuários brasileiros.

## Bloco das Configurações Rápidas

`qs/CloseAllTileService.kt` registra um bloco "Fechar Tudo" na barra de
notificações: um toque encerra os aplicativos em execução sem abrir o app. Valem
as mesmas garantias da aba "Em execução" — os protegidos, a tela inicial e o
próprio Fecha Tudo Plus ficam de fora.

O trabalho roda no escopo do Application, porque o serviço do bloco é
desvinculado assim que o painel fecha. Com a tela bloqueada, o encerramento só
acontece depois do desbloqueio (`unlockAndRun`), como o Android exige.

O cartão em Configurações reflete o estado real: enquanto o bloco não está no
painel ele oferece um "+"; depois de adicionado fica apagado e sem ação. Quem
informa isso é o próprio `TileService`, pelos retornos `onTileAdded` e
`onTileRemoved` do sistema — se o usuário tirar o bloco, o cartão volta a
oferecer a adição.

Para adicionar: Configurações → Bloco de Acesso Rápido. No Android 13 ou mais
novo abre o diálogo oficial do sistema; nas versões anteriores o app mostra o
passo a passo para arrastar o bloco manualmente.

## Tela Sobre

Uma tela só, em `ui/screens/AboutScreen.kt`: o resumo de como o app funciona
fica sempre visível e, abaixo, quatro seções que abrem e fecham — Shizuku,
termos de uso e responsabilidade, política de privacidade e segurança dos dados.
Todo o texto está em `strings.xml`, nos 10 idiomas.

Separar em telas diferentes seria legítimo, mas para quatro blocos de texto que
ninguém lê em sequência o acordeão evita uma navegação a mais sem esconder nada.
Se um dia a política precisar de uma URL pública (a Play Store exige isso na
ficha do app, não dentro dele), basta apontar para o mesmo texto.

## Assinatura

O rodapé da aba Configurações mostra "Feito: @odorizzioficial" com o ícone do
YouTube; um toque abre https://www.youtube.com/@odorizzioficial no app do
YouTube ou no navegador. O texto e a URL ficam em `strings.xml` marcados como
`translatable="false"`, já que não mudam com o idioma.

## Tema claro

O padrão é o escuro, como no design original; o claro é escolha manual do
usuário em Aparência.

O vidro não é o mesmo nos dois. No escuro a separação vem de um brilho branco na
aresta superior e de uma borda de luz. No claro esse brilho branco sobre
superfície clara simplesmente desaparece, então os painéis ficam bem mais
opacos, a borda passa a ser escura e uniforme, os brilhos do fundo caem para 35%
e o fundo fica um tom abaixo dos cartões, para que eles se destaquem.

A paleta clara também foi corrigida: `primaryContainer` e `tertiaryContainer` são
usados no app como cor de texto e de ícone (selos "Ativo" e "APLICATIVOS",
memória recuperada, tinta dos ícones). Nos valores pálidos do Material eles
sumiam sobre fundo branco, então no tema claro esses dois tokens recebem tons
escuros.

## Navegação

- Deslizar o dedo para a esquerda ou direita troca de aba (`HorizontalPager`).
- Tocar na barra inferior também troca, e os dois caminhos ficam sincronizados.
- O seletor de idioma fica no alto da tela, na altura do relógio: arraste para
  cima ou para baixo para percorrer os idiomas, ou toque para avançar.

## Compatibilidade

`minSdk 26` (Android 8.0) até `targetSdk 35` (Android 15). A Liquid Glass exige
API 24+ e ativa o pipeline completo com AGSL a partir do Android 13, degradando
sozinha nas versões anteriores. O Shizuku, o Room e o DataStore funcionam em
toda a faixa.

## Idiomas

Português (Brasil) é o padrão. Também acompanham Português (Portugal), English,
Español, Română, Français, 中文, 日本語, 한국어 e हिन्दी. A troca usa o sistema
oficial de localização por app do Android (`AppCompatDelegate.setApplicationLocales`
com `locales_config.xml`) e fica salva no DataStore. Nenhum texto está fixo no
código — tudo vem de `strings.xml`.

## Permissões pedidas

| Permissão | Para quê |
|---|---|
| QUERY_ALL_PACKAGES | listar os aplicativos instalados pelo usuário |
| PACKAGE_USAGE_STATS | saber qual app está em primeiro plano |
| KILL_BACKGROUND_PROCESSES | encerrar apps quando não há Shizuku |
| FOREGROUND_SERVICE (+ SPECIAL_USE) | manter o vigia dos bloqueados |
| POST_NOTIFICATIONS | notificação silenciosa do vigia |
| RECEIVE_BOOT_COMPLETED | reativar o vigia após reiniciar |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | abrir o pedido oficial do Android |
| moe.shizuku.manager.permission.API_V23 | falar com o Shizuku |

Cada pedido especial é explicado na tela antes de abrir o fluxo do Android.
Nada é concedido silenciosamente.
