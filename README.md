# Begood Music Player (B.M.P) — app Android

Player para as músicas que estão **no próprio celular**. Mesmo esquema e visual do app da Coleção de CDs,
mas lendo os arquivos locais em vez da rede.

## O que faz
- **Pastas**: em ⚙ Configurações, adicione as pastas que quiser (ou toque no + nas pastas encontradas).
  Sem nenhuma escolhida, usa a pasta padrão **Music** (memória interna e cartão SD).
  "Escanear agora" procura arquivos novos.
- **Abas**: Álbuns, Artistas, Músicas, Gêneros e Buscar. **Toque longo** numa música marca várias (tocar, fila, editar em lote). Menu ⋮ em cada música: tocar a seguir, adicionar à fila,
  editar tags, ir para o álbum.
- **Letras** (ícone de letra em "Tocando agora"): procura nas tags, em arquivo .lrc/.txt com o mesmo nome
  e, se não achar, no LRCLIB (internet, gratuito, com letra sincronizada). Também: pesquisar no Google,
  colar/escrever a letra e "Gravar na música".
- **Equalizador paramétrico** (ícone de barras): até 10 bandas com frequência, ganho e largura (Q) livres,
  tipos pico/graves/agudos, pré-amplificação com "Auto", presets prontos e seus. Vale para alto-falante, fone e Bluetooth.
- **Editor de tags** (menu ⋮): uma música (tudo, inclusive letra), o álbum inteiro ou várias músicas marcadas (lote: artista, álbum, artista do álbum,
  ano, gênero, capa). "Atualizar pela internet" busca no iTunes e Deezer pelo artista + álbum e preenche dados,
  capa em alta resolução e número das faixas. No Android 11+ o sistema pede confirmação para alterar os arquivos.
- **Bluetooth**: controles do fone/carro, título e capa no visor do carro, pausa ao desconectar,
  navegação por álbuns/artistas pelo carro e Android Auto, botão play do carro retoma a última fila com o app fechado,
  e opção (Configurações) de voltar a tocar ao conectar.

## Instalar no celular
1. Copie `app\build\outputs\apk\debug\app-debug.apk` (ou `BegoodMusicPlayer.apk`, na pasta do projeto) para o celular.
2. Toque no arquivo e permita "instalar apps desconhecidos".
3. Abra o app e toque em **Permitir** (acesso às músicas e notificações).

Ou, com o celular no USB e a "Depuração USB" ativa:
```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Compilar
```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew assembleDebug
```
Assinatura oficial opcional: chave em `app/assinatura/bmp.jks` e senha em `keystore.properties` (`senha=...`).
Esses arquivos **não** vão para o git.

## Como foi feito (técnico)
- Kotlin + Jetpack Compose; reprodução com Media3/ExoPlayer em `MediaLibraryService` (`player/PlayerService.kt`).
- Músicas lidas do MediaStore e filtradas por pasta (`data/Repositorio.kt`).
- Equalizador: filtros biquad (Audio EQ Cookbook) num `AudioProcessor` do ExoPlayer (`player/Equalizador.kt`).
- Tags: JAudioTagger, editando uma cópia e gravando de volta via `MediaStore.createWriteRequest` (`data/Tags.kt`).
- Capas embutidas: endereço `bmpcapa://` lido pelo Coil e pela notificação (`data/Capas.kt`).
