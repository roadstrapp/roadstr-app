# Destinazioni JSON da altre app Android

Roadstr accetta una destinazione inviata esplicitamente alla propria activity launcher. Il
contratto è quello già usato da Tankful:

```json
{"label":"Stazione di servizio","lat":45.0703,"lon":7.6869}
```

Il mittente deve usare:

- action `android.intent.action.SEND`;
- MIME type `application/json`;
- JSON nell'extra `android.intent.extra.TEXT` (`Intent.EXTRA_TEXT`);
- un Intent esplicito ottenuto, per esempio, con
  `PackageManager.getLaunchIntentForPackage("app.roadstr")`.

Esempio Kotlin:

```kotlin
val launch = packageManager.getLaunchIntentForPackage("app.roadstr") ?: return
launch.action = Intent.ACTION_SEND
launch.type = "application/json"
launch.putExtra(
    Intent.EXTRA_TEXT,
    """{"label":"Stazione di servizio","lat":45.0703,"lon":7.6869}""",
)
startActivity(launch)
```

Roadstr gestisce sia un avvio a freddo sia una nuova consegna alla activity `singleTop`. Se è già
disponibile un fix GPS, avvia subito il calcolo; altrimenti apre il planner con la destinazione
compilata e lascia all'utente il controllo dell'origine e del pulsante **Calcola percorso**.

## Validazione

Il payload è trattato come input non attendibile e viene rifiutato senza cambiare lo stato corrente
quando una di queste condizioni non è rispettata:

- oggetto JSON chiuso con esattamente `label`, `lat` e `lon`, senza campi o chiavi duplicate;
- `label` non vuota, senza caratteri di controllo e lunga al massimo 300 caratteri;
- `lat` numerica e finita nell'intervallo `[-90, 90]`;
- `lon` numerica e finita nell'intervallo `[-180, 180]`;
- payload UTF-8 lungo al massimo 4096 byte.

Roadstr non dichiara un intent filter `ACTION_SEND`: l'integrazione resta esplicita e l'app non
compare come destinazione globale per qualsiasi condivisione Android.
