# Battery Share

Application Android minimaliste pour partager de la charge batterie entre deux t&eacute;l&eacute;phones via Wi-Fi Direct.

## Concept

Lancez l'app sur deux t&eacute;l&eacute;phones, choisissez qui donne et qui re&ccedil;oit, connectez-vous et partagez.

## Fonctionnalit&eacute;s

- **Aucun compte requis** - lancer et utiliser imm&eacute;diatement
- **Wi-Fi Direct** - connexion P2P sans routeur ni internet
- **UI minimaliste** - &eacute;cran unique, dark theme
- **Z&eacute;ro pub** - aucune pub, aucun tracking
- **Ultra l&eacute;ger** - d&eacute;pendances minimales (Compose + AndroidX uniquement)

## Utilisation

1. Installer l'app sur les deux t&eacute;l&eacute;phones
2. Ouvrir l'app sur les deux
3. Sur le t&eacute;l&eacute;phone donneur : appuyer sur **DONNER**
4. Sur le t&eacute;l&eacute;phone receveur : appuyer sur **RECEVOIR**
5. Scanner et s&eacute;lectionner l'autre appareil
6. Le transfert commence automatiquement

## Note technique

Le transfert physique de charge entre deux t&eacute;l&eacute;phones n&eacute;cessite une connexion USB-C
(OTG) entre les appareils. L'app g&egrave;re la d&eacute;couverte, la connexion et la
synchronisation des niveaux de batterie via Wi-Fi Direct. Pour le transfert
r&eacute;el d'&eacute;nergie, connectez les deux t&eacute;l&eacute;phones avec un c&acirc;ble USB-C to USB-C.

## Build

```bash
./gradlew assembleDebug
```

L'APK sera dans `app/build/outputs/apk/debug/`.

## Pr&eacute;requis

- Android 8.0+ (API 26)
- Wi-Fi activ&eacute;
- Permission de localisation (requise pour Wi-Fi Direct)

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Wi-Fi Direct (android.net.wifi.p2p)
- Zero d&eacute;pendance externe
