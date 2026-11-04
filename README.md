# Hahnemann & Co
Offline-Nachschlagewerk (WebView). Inhalt: app/src/main/assets/index.html
Paket: de.autoknigge.hahnemannco

1. Ordner als neues GitHub-Repo hochladen (z. B. Autoknigge/HahnemannCo).
2. Actions -> Build starten. Ergebnis unter "Artifacts": APK zum Testen, AAB für den Play Store.
3. AAB signieren: Secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD anlegen (sonst unsigniert).
Neue Inhalte: index.html ersetzen, versionCode in app/build.gradle erhöhen.
