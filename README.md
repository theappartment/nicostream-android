# NicoStream Android

Client per Android e Android TV / Google TV, con icona condivisa con iOS, con player nativo Media3, Home con anteprime silenziose, preferiti e Continua a guardare.

## Download e aggiornamenti

Scarica [NicoStream.apk dall’ultima release](https://github.com/theappartment/nicostream-android/releases/latest/download/NicoStream.apk).

Dopo la prima installazione, usa **Impostazioni → Controlla aggiornamenti**. L’app controlla anche automaticamente una volta al giorno quando sei nella Home. Download e installazione richiedono una scelta esplicita; Android mostra la propria conferma e può chiedere di autorizzare NicoStream a installare aggiornamenti.

Prima dell’installazione vengono verificati SHA-256, dimensione, nome del pacchetto, versione e certificato di firma. Le release ufficiali conservano l’identità `com.nicostream.demo`, così si aggiornano sopra la demo già installata mantenendo dati e preferenze. Le versioni di sviluppo firmate con altre chiavi non possono sostituire un’installazione ufficiale.

La chiave privata di firma non è contenuta in questo repository. `official-signing.sha256` contiene solo l’impronta pubblica del certificato.

## Uso

- Android 8 / API 26 o successivi. Touch e telecomando supportati.
- La demo locale include due film aperti della Blender Foundation; non richiede un server.
- Per i cataloghi degli addon serve un motore VPS compatibile: il server ufficiale `https://nicostream.theappartment.it` è già configurato e non è modificabile dall’interfaccia. Un nuovo dispositivo va autorizzato una sola volta con la chiave privata; poi il collegamento è automatico a ogni apertura. Il server e le credenziali personali non sono inclusi nell’APK.
- **Cerca** interroga insieme Stream4me / StreamingCommunity sul dispositivo e ToonItalia sul motore VPS. Ogni scheda indica la sorgente e conserva il suo percorso originale. Un servizio non disponibile non elimina i risultati dell’altro.
- **Catalogo → Program Feed** usa il parser e il resolver condivisi con iOS: palinsesto per giorno/lingua, canali rapidi e impostazioni del feed. I video compatibili partono nel player nativo con gli header della sorgente; le pagine senza un video nativo vengono segnalate. Le schede della home mostrano gli stemmi dei club riconosciuti, il logo F1 per Formula 1 e i loghi ATP/WTA per il tennis. Le dirette non salvano punti di ripresa. Nelle impostazioni Program Feed, **Proposte di oggi nella home** mostra/nasconde anche la riga **Program Feed · Oggi** nella home **Scopri**, con eventi in italiano futuri e in corso stimati; richiede Android 0.3.5 e il motore aggiornato.
- **Domini sorgenti:** la VPS aggiorna automaticamente l’indice ufficiale Stream4me; Android segue il dominio corrente di StreamingCommunity per Home, ricerca e puntate. Preferiti e riprese vengono riallineati. In caso di errore si conserva l’ultima copia valida. Puoi forzare il controllo da **Impostazioni → Aggiorna domini sorgenti**.
- La cronologia e i punti di ripresa sono salvati sul dispositivo.
- Telecomando nel player: sinistra/destra scelgono i pulsanti; su porta alla barra temporale, dove sinistra/destra spostano di dieci secondi. A comandi nascosti, la prima pressione li mostra.

## Compilazione

Richiede Java 17 o successivo e Android SDK 35. Il Gradle Wrapper è incluso.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Su Windows usare `gradlew.bat`. Impostare `ANDROID_HOME` per il percorso dell’SDK.

GitHub Actions esegue test e build di sviluppo per push e pull request. Gli artefatti CI sono build di sviluppo, non release ufficiali firmate per aggiornare le installazioni esistenti.

## Pubblicare una versione ufficiale

Aggiornare `version.properties` con un `versionCode` crescente e una versione semantica, poi scrivere le note in `RELEASE_NOTES.md`.

Su una macchina con la chiave ufficiale e GitHub CLI autenticata:

```sh
python3 release.py --publish
```

La chiave può essere fornita tramite `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. Il comando controlla il certificato, compila e testa, poi pubblica `NicoStream.apk`, `nicostream-update.json` e `SHA256SUMS` in una release GitHub. Non generare una chiave sostitutiva per aggiornare app già distribuite: il certificato deve restare quello atteso.

Per compilare e verificare senza pubblicare, eseguire `python3 release.py`.

## Struttura

- `src/`: interfaccia, player, trasporto della sessione locale e aggiornamenti.
- `tests/`: test del catalogo, cronologia, richieste e contratto degli aggiornamenti.
- `res/`, `assets/`: risorse dell’app e catalogo demo.
- `release.py`: build e pubblicazione delle release ufficiali.

Questo repository contiene il client Android. Il motore addon e l’app iOS sono mantenuti separatamente; i contratti del client restano compatibili con il progetto condiviso.

## Opening and TV presentation (0.4.0)

Startup shows a short, skippable NicoStream logo animation over the real Home. It respects disabled system animations. Startup no longer launches a Kodi/Stream4me root job; provider metadata and Program Feed load independently, and explicit addon browsing still uses the engine normally. The access-key screen remains available on unconfigured devices.

Home shelves and catalog grids reserve space for scaled focused cards. Vertical focus navigation centers the active item within the content viewport, including the final row. Player controls keep their existing navigation.
