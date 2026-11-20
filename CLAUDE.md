# Begood Music Player (B.M.P) — player de música Android

Player para as músicas que estão no próprio celular. Mesmo esquema e visual (âmbar sobre fundo escuro)
do app da Coleção de CDs (`Sistemas\colecao-de-cds-android`), mas lendo os arquivos locais.
Pedido original: `Begood Music Player - (B.M.P).md`. Detalhes de uso e técnicos: `README.md`.
Repositório: github.com/robertobegood-jpg/Begood-Music-Player-app-Android (público)

## Funções
- Pastas escolhidas para escanear (sem nenhuma, usa a pasta padrão Music).
- Letras: tags, .lrc/.txt, LRCLIB; Google; colar; gravar nas músicas (uma a uma ou todas de uma vez).
- Equalizador paramétrico em software (AudioProcessor do ExoPlayer) — vale para Bluetooth.
- Editor de tags (uma música, álbum ou lote) com capa e atualização pela internet (iTunes/Deezer).
- Bluetooth/carro: MediaLibraryService (AVRCP, Android Auto, retomar fila).
- Artistas por "artista do álbum" e "artista da faixa" (convidados "feat.").
- Playlists: manuais, automáticas (favoritas, mais tocadas, recentes, adicionadas), importar/exportar .m3u, no carro.
- Genius: playlist que combina com uma música (Last.fm, guardado no celular para funcionar offline; sem chave usa gênero/época).

## Como trabalhar comigo
- Responda e escreva tudo em **português do Brasil**, linguagem simples.
- **Antes de começar, rode `git pull`** (o projeto fica sincronizado com o GitHub).
- **Teste antes de entregar**: `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` e `gradlew assembleDebug`;
  corrija todos os erros. Copie o APK para `BegoodMusicPlayer.apk` na raiz (ignorado pelo git).
- Ao terminar uma mudança: commit com mensagem clara e **pergunte antes do `git push`**.
- Peça confirmação antes de apagar arquivos ou mudar o `applicationId` (`br.com.begood.bmp`).
- **NUNCA** commitar `*.jks`, `*.keystore`, `keystore.properties`, `app/assinatura/`, `local.properties`.
- Ao lançar versão: aumente `versionCode` e `versionName` em `app/build.gradle.kts`.
