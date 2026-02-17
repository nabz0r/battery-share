# Battery Share

Application Android minimaliste de partage de batterie entre deux telephones via Wi-Fi Direct.

**Zero compte. Zero pub. Ultra leger.**

---

## Concept

Lancez l'app sur deux telephones, choisissez qui donne et qui recoit,
connectez-vous et partagez votre charge. C'est tout.

## Fonctionnalites

| | |
|---|---|
| **Aucun compte** | Lancer et utiliser immediatement |
| **Wi-Fi Direct** | Connexion P2P directe, pas besoin de routeur ni internet |
| **UI minimaliste** | Ecran unique, dark theme, animations fluides |
| **Zero pub** | Aucune pub, aucun tracking, aucune collecte de donnees |
| **Ultra leger** | Zero dependance externe (Compose + AndroidX uniquement) |
| **Feedback haptique** | Vibration tactile sur chaque interaction |
| **Keep alive** | Ping automatique pour maintenir la connexion stable |
| **Ecran actif** | L'ecran reste allume pendant le transfert |

## Utilisation

```
1. Installer l'app sur les deux telephones
2. Ouvrir l'app sur les deux
3. Telephone A : appuyer sur DONNER
4. Telephone B : appuyer sur RECEVOIR
5. Le scan demarre automatiquement
6. Selectionner l'autre appareil dans la liste
7. Brancher un cable USB-C entre les deux telephones
8. Le transfert d'energie commence
```

## Note technique

> Le transfert physique d'energie entre deux telephones necessite un
> **cable USB-C to USB-C** (OTG) entre les appareils. L'app gere la
> decouverte Wi-Fi Direct, la connexion P2P et la synchronisation des
> niveaux de batterie en temps reel. Le transfert reel d'electricite
> est une fonctionnalite hardware.

## Architecture

```
app/src/main/java/com/batteryshare/
├── MainActivity.kt              # Entry point, permissions, edge-to-edge
├── BatteryShareViewModel.kt     # State management, coordination
├── model/
│   └── DeviceState.kt           # Etats (Role, ConnectionStatus, DeviceState)
├── battery/
│   └── BatteryMonitor.kt        # Monitoring batterie temps reel via Flow
├── connection/
│   └── P2PManager.kt            # Wi-Fi Direct (decouverte, socket, keepalive)
└── ui/
    ├── theme/
    │   └── Theme.kt             # Dark theme Material 3, edge-to-edge
    └── screens/
        └── HomeScreen.kt        # UI Compose (cercle, particules, animations)
```

### Composants cles

- **BatteryShareViewModel** : AndroidViewModel qui coordonne BatteryMonitor + P2PManager, expose un StateFlow unique
- **P2PManager** : Gere Wi-Fi Direct avec auto-discovery (refresh 15s), retry de connexion (3 tentatives), keepalive ping (5s), et SharedFlow d'erreurs
- **HomeScreen** : Ecran unique avec AnimatedContent entre les etats, cercle de batterie anime avec particules orbitantes, indicateur de flux d'energie anime, timer de connexion

## Build

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```

L'APK sera dans `app/build/outputs/apk/debug/` ou `release/`.

## Prerequis

- Android 8.0+ (API 26)
- Wi-Fi active
- Permission de localisation (requise par Android pour Wi-Fi Direct)
- Android 13+ : permission NEARBY_WIFI_DEVICES supplementaire

## Stack technique

- **Kotlin** - langage principal
- **Jetpack Compose** - UI declarative
- **Material 3** - design system
- **Wi-Fi Direct** (android.net.wifi.p2p) - connexion P2P
- **ViewModel** + StateFlow - architecture reactive
- **Zero dependance externe** - pas de Firebase, pas de Retrofit, rien

## Changelog

### v1.1
- Architecture ViewModel pour une meilleure gestion d'etat
- Animations ameliorees : particules orbitantes, flux d'energie anime entre appareils
- Retour haptique sur toutes les interactions
- Auto-scan a la selection du role
- Ping keepalive pour maintenir la connexion stable
- Retry automatique (3 tentatives) sur echec de connexion client
- Snackbar d'erreurs avec messages explicites en francais
- Timer de connexion en temps reel
- Ecran maintenu allume pendant le transfert
- Edge-to-edge display
- UI permission denied avec instructions
- Guide "Comment ca marche" sur l'ecran d'accueil
- Badge de role colore sur l'ecran de scan
- Indicateur de hint pendant la recherche

### v1.0
- Version initiale
- Decouverte Wi-Fi Direct et connexion P2P
- Synchronisation des niveaux de batterie
- UI dark minimaliste avec cercle anime

## Licence

MIT
