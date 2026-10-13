# Car Scanner

<img src="./docs/app_image.png" alt="CarScanner App">

An Android app that identifies cars from photos using AI, rates how rare they are, and turns spotting
cars into a collection game. Take a photo (or pick one from your gallery), earn points based on rarity
and value, level up, and fill your garage.

**Build your own copy:** this project is designed so anyone can run it on their own phone for free,
using their own Firebase project and their own Gemini API key. No server required.

## Features

- **Scan cars two ways**: live camera with a viewfinder, or pick an image from your gallery
- **AI identification**: make, model, year, body style, estimated value, production numbers, a fun fact
- **Rarity tiers**: Common, Uncommon, Rare, Epic, Legendary, Mythical (red) and Divine (animated rainbow)
- **Points and levels**: score depends on rarity, value, how few were built, age and AI certainty;
  re-scanning the same model only earns 10%
- **Garage**: your collection as rarity-colored cards, loaded in pages so it stays fast, with search,
  rarity filters and sorting
- **Profile page**: level, total garage value, unique models, scans this week, favorite brand,
  per-rarity breakdown, best scoring and most valuable cars
- **Fix wrong scans**: "Not the right car?" lets you type the real car and the AI fills in the details
- **Google sign-in**, delete/sign-out confirmations
- **Web-search grounding** (optional) so the AI can look up very new cars
- All data stays **on your phone**, separate per Google account

## Tech stack

Kotlin, Jetpack Compose (Material 3), CameraX, Room + Paging 3, Firebase Auth with Credential Manager
(Google sign-in), Coil, OkHttp, Gemini API.

## Build it yourself (about 15 minutes)

You need: [Android Studio](https://developer.android.com/studio) (latest stable), a Google account, and an
Android phone (or an emulator with Google Play).

### 1. Clone and open

```bash
git clone https://github.com/ItzjustElias/CarScanner.git
```

Open the folder in Android Studio and let Gradle sync (it will fail until step 2 is done, that's expected).

### 2. Create a free Firebase project (Google sign-in)

1. Go to the [Firebase console](https://console.firebase.google.com) and click **Add project**.
   The free Spark plan is enough; no credit card needed.
2. Add an **Android app** with package name `com.example.carscanner`
   (if you change the package name in `app/build.gradle.kts`, use that one instead).
3. Add your **debug SHA-1** to that app. In Android Studio open the Gradle panel, then
   `app > Tasks > android > signingReport`, and copy the `SHA1` of the `debug` variant.
   Or run `./gradlew signingReport` in a terminal.
4. In Firebase go to **Build > Authentication > Get started > Sign-in method** and enable **Google**.
5. Download **`google-services.json`** (Project settings > Your apps) and put it in the **`app/`** folder.
   **Do not commit it.** It's already in `.gitignore`.

> Download `google-services.json` *after* enabling Google sign-in and adding the SHA-1, otherwise it won't
> contain the web client ID the app needs.

### 3. Get a free Gemini API key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey) and create an API key (free tier, no card).
2. In the project root, create or open `local.properties` (Android Studio creates it) and add:

```properties
GEMINI_API_KEY=your_key_here
# Optional:
GEMINI_MODEL=gemini-2.5-flash
GEMINI_SEARCH=true
```

See `local.properties.example`. This file is git-ignored.

### 4. Run it

Connect your phone with USB debugging on (or start an emulator) and press **Run**.
To keep the app installed, use **Build > Build APK(s)** and install the APK on your phone.

## Configuration

| Setting (`local.properties`) | Default | What it does |
|---|---|---|
| `GEMINI_API_KEY` | none | Your Gemini API key (required) |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Model used for scans. Change it if Google renames or retires it |
| `GEMINI_SEARCH` | `true` | Lets the AI search the web for very new cars. Set to `false` if you hit quota errors. The app also falls back automatically |

## How scoring works

Base points per tier: Common 10, Uncommon 20, Rare 40, Epic 70, Legendary 120, Mythical 200, Divine 350.
On top of that:

- a bonus from the estimated value (logarithmic, so a $1M car isn't 100x a $10k car)
- a bonus for low production runs
- a bonus for classics (30+ and 50+ years old)
- multiplied by how certain the AI is, and reduced to 10% for models you've already scanned

The AI picks the tier, then the app enforces a floor for very valuable or very limited cars so a $5M car
can't be rated Common. All numbers are in `domain/PointsCalculator.kt` and `domain/Models.kt`.

## Project structure

```
app/src/main/java/com/example/carscanner/
  auth/       Google sign-in (Credential Manager + Firebase)
  data/       Room database, Gemini API client, repository
  domain/     rarity tiers, points and level rules
  ui/
    login/    sign-in screen
    garage/   collection, search/filter, detail sheet, "fix this car"
    scan/     camera, gallery picker, result screen
    profile/  stats page
    theme/    colors, typography, animated rarity styling
    components/ shared widgets
```

## Important notes

- **Your API key is compiled into the app.** That's fine for your own phone. Never publish an APK
  you built with your key, and never commit `local.properties`. For a public app you would need a
  backend that holds the key.
- **Photos are sent to Google's Gemini API** for identification. Free-tier requests may be used by Google
  to improve its products, so avoid photographing license plates or people.
- **The AI can be wrong**, especially for rare, modified or very new cars. Values and production numbers are
  estimates, not market data. Use "Not the right car?" to fix a scan.
- Free-tier API limits apply and change over time. Check [Google's rate limit docs](https://ai.google.dev/gemini-api/docs/rate-limits)
  and your usage in AI Studio.

## Troubleshooting

| Problem | Fix |
|---|---|
| Build error: `default_web_client_id` not found | `google-services.json` has no web client ID. Enable Google sign-in in Firebase, add your SHA-1, re-download the file, then clean and rebuild. Fallback: copy the Web client ID from Firebase into a string resource and reference it in `auth/AuthManager.kt` |
| Google sign-in fails ("developer error") | The SHA-1 in Firebase doesn't match your build. Re-add it and re-download `google-services.json` |
| "Missing Gemini API key" | Add `GEMINI_API_KEY` to `local.properties` and rebuild |
| "Free daily limit reached" | You hit the free quota. Wait for it to reset, or set `GEMINI_SEARCH=false` |
| "That Gemini model isn't available" | Change `GEMINI_MODEL` to a current model from AI Studio |

## Contributing

Issues and pull requests are welcome. Ideas: achievements, export/backup, a full car detail page,
dark/light theme toggle, more languages. Please don't commit secrets or `google-services.json`.

## License

[MIT](LICENSE). Replace `<YOUR NAME>` in the license file with your name before publishing.

This project is not affiliated with Google, Firebase or any car manufacturer. Brand names belong to their
respective owners.
