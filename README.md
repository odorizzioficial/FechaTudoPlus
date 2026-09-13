<img width="1672" height="941" alt="NovoFechaTudoAppsPrincipal" src="https://github.com/user-attachments/assets/476a26ac-8034-4510-9347-756668396c3c" />

# Fecha Tudo Plus

App Android nativo (Kotlin + Jetpack Compose) que mostra quais aplicativos estão **realmente** rodando no seu aparelho e devolve o controle deles para você. Encerra apps em segundo plano, protege os que não podem ser fechados, bloqueia os que insistem em voltar, congela os que você quer desativar por completo e agenda limpezas para acontecerem sozinhas. Usa o **Shizuku** para permissões privilegiadas, sem root. Todo o estado fica salvo localmente (Room/SQLite) — sem servidor, sem conta, sem coleta de dados.

## ⚡ Em Execução

- Lista os processos ativos com a **memória real** que cada um ocupa, lida direto da tabela de processos do sistema.
- Sem ligar "apps do sistema", já aparecem os apps de sistema que são seguros de fechar — identificados pela categoria real que a Play Store atribui a eles (Google Play Services, Play Store e afins). Serviços internos do Android (SystemUI, drivers) ficam escondidos até você ligar o toggle.
- **Fechar Tudo** encerra todos de uma vez, em paralelo, ignorando automaticamente os protegidos.
- No menu de cada app: forçar parada, bloquear, restringir ou **congelar**, sem sair da tela.
- O cartão dos recentes some junto com o processo — mesmo se o app estiver aberto na tela no momento do encerramento.

## 🛡️ Restritos

Aplicativos que **nunca** serão encerrados pelo Fechar Tudo.

- Na primeira abertura, o app pergunta ao Android quem exerce cada papel no aparelho — tela inicial, telefone, mensagens, teclado, relógio, câmera — e protege esses automaticamente. Nada de lista fixa: funciona em qualquer fabricante.
- Carteiras digitais também entram protegidas por padrão: Samsung Wallet, Google Wallet, Nubank, PicPay, Mercado Pago e outras — para não travar pagamentos por aproximação sem querer.
- O Shizuku e derivados entram protegidos também, já que encerrá-los derrubaria as próprias funções do app.
- Um botão devolve os padrões caso você remova algum sem querer.

## 🚫 Bloqueados

Aplicativos que devem parar assim que saem da tela.

- Enquanto o app está aberto, funciona **100% normal** — abre, usa, fecha quando você quiser. No instante em que sai do primeiro plano, o processo é encerrado.
- A restrição usa appops de segundo plano e o balde "restricted" de standby, sem suspender o pacote: o app nunca fica indisponível para abertura manual, só é impedido de continuar rodando escondido.
- Um vigia checa a cada fração de segundo e reencerra na hora se o app tentar voltar sozinho — cobre até casos teimosos como o Google Play Services.
- Tudo é revertido ao desbloquear.
- Um app pode estar em Restritos e Bloqueados ao mesmo tempo: protegido do Fechar Tudo, mas encerrado ao sair do primeiro plano.

## ❄️ Congelados

Aplicativos completamente desativados, para quem quer ir além do bloqueio.

- Desativa o app por inteiro (`pm disable-user`): some da gaveta, não aparece em lugar nenhum e não usa memória — mais forte que o bloqueio, que só impede o segundo plano.
- Funciona com apps do sistema e do usuário.
- Reversível a qualquer momento: descongelar reabilita o app e ele volta à gaveta normalmente.
- Atalho direto no menu de três pontos da aba Em Execução, e disponível também como ação no Agendamento.

## 🫧 Bolha Mágica e Notificação Fixa

Acesso rápido sem precisar abrir o app.

- **Bolha flutuante**: fica por cima de qualquer app na tela. Toque único encerra tudo (exceto protegidos); segure por 2 segundos para abrir direto as configurações dela.
- Opacidade ajustável de 0% a 100%, tamanho ajustável, e a posição é lembrada mesmo depois de fechar e reabrir o app.
- Botão de reset devolve a bolha para o centro da tela, caso ela fique em algum lugar difícil de alcançar.
- **Notificação fixa**: mostra o app rodando na barra e oferece o Fechar Tudo direto de lá. Se você arrastar ela pra descartar sem querer, volta sozinha automaticamente — enquanto a opção estiver ligada.

## 📅 Agendar

- Crie grupos com nome, escolha os apps, defina a ação e programe o horário — hora, minuto e até **segundos**.
- Quatro ações por grupo: **forçar parada**, **bloquear**, **restringir** ou **congelar**.
- Repetição por dias da semana, por intervalo, ou execução única numa data escolhida.
- Roda via alarme exato (`AlarmManager`), com o app fechado. Os alarmes são reancorados após reiniciar o aparelho.
- Toque no grupo para ver os apps; toque em Alterar para editar sem refazer nada.

## 🔲 Bloco de Acesso Rápido

Um bloco nas Configurações Rápidas do Android encerra os apps em execução direto da barra de notificações, sem abrir o aplicativo. As mesmas proteções valem: restritos, tela inicial e o próprio app ficam de fora.

## 🌍 10 idiomas

🇧🇷 Português (Brasil) · 🇵🇹 Português (Portugal) · 🇺🇸 English · 🇪🇸 Español · 🇷🇴 Română · 🇫🇷 Français · 🇨🇳 中文 · 🇯🇵 日本語 · 🇰🇷 한국어 · 🇮🇳 हिन्दी

O idioma inicial vem do aparelho. A troca é instantânea, sem reiniciar a tela: um seletor no topo muda com um toque ou arrastando para cima e para baixo.

## 🎨 Interface

- **Material 3** com paleta própria, tema **escuro** (padrão) e **claro**.
- Efeito de **vidro** em cartões, selos, diálogos e barra inferior, com **intensidade ajustável de 0 a 100%** — ou desligado.
- Barra inferior flutuante com **vidro líquido de verdade**: refração SDF, dispersão cromática e brilho que acompanha a inclinação do aparelho.
- Navegação por deslize entre as abas, agora com seis abas: Em Execução, Restritos, Bloqueados, Congelados, Agendar e Configurações.
- Tela de Sobre explica cada modo separadamente, com ícone e descrição própria.
- Ícone com selo indicando quais apps são do sistema.

## 📱 Compatibilidade

| | |
|---|---|
| **Android** | 8.0 (API 26) até o mais recente |
| **Arquitetura** | MVVM · Room/SQLite · DataStore · Coroutines |
| **Root** | Não é necessário |

Algumas funções dependem da versão: limpar o cartão dos recentes e a restrição profunda pedem Android 9+; congelar apps pede Android 6+; o diálogo de adicionar o bloco rápido, Android 13+; a permissão de notificações e o vidro líquido completo, Android 13+ (degrada sozinho abaixo disso).

## 🔌 Shizuku

O Android não permite que um app comum leia processos de outros aplicativos nem os encerre de verdade. O **Shizuku** concede esse acesso usando as permissões do shell de depuração, **sem root**.

Compatível com **Shizuku**, **Shizuku Plus**, **Shevery** e **Sui** — a detecção é feita pelo serviço em execução, não pelo nome do pacote, então qualquer derivado que implemente a API funciona. O app tenta reconectar automaticamente se a ligação cair ao voltar do segundo plano.

Sem ele o app continua funcionando, mas o Android limita a leitura de processos e o encerramento. A tela avisa quando isso acontece, em vez de simular funcionamento.

## 🔒 Privacidade

Nenhum dado sai do aparelho. O app **não declara nem a permissão de internet** — não existe caminho técnico para enviar nada a lugar nenhum.

- Sem conta, sem login, sem servidor
- Sem anúncios, sem rastreadores, sem estatísticas de uso
- Listas e agendamentos em banco local (Room/SQLite), configurações em DataStore
- Backup na nuvem desligado: desinstalar apaga tudo, e reinstalar começa do zero

## 🙏 Créditos

- **Shizuku** — [RikkaApps](https://github.com/RikkaApps/Shizuku) · Apache License 2.0
- **Liquid Glass Android** — [QWEA0](https://github.com/QWEA0/Liquid-Glass-Android) · MIT License

## 📄 Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE).
