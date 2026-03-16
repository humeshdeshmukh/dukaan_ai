# Dukaan AI - Publishing & AdMob Guide

## AdMob Configuration (Already Done)

### App ID (in AndroidManifest.xml)
```
ca-app-pub-1860002386592592~7229866403
```

### Ad Unit IDs (in AdConfig.kt)
| Ad Unit | ID | Use |
|---|---|---|
| Banner | `ca-app-pub-1860002386592592/2913149193` | All list screens |
| Interstitial | `ca-app-pub-1860002386592592/3023862231` | After saves/actions |
| Micro Banner | `ca-app-pub-1860002386592592/9344528899` | During scanning |
| Native | `ca-app-pub-1860002386592592/9655520084` | Dashboard cards |

### Switch Test ↔ Production
In `app/src/main/java/com/dukaan/ai/ads/AdConfig.kt` line 18:
```kotlin
const val IS_TEST_MODE = false   // false = real ads (production)
const val IS_TEST_MODE = true    // true  = test ads  (development)
```

---

## App Signing (Already Done)

- **Keystore file**: `app/release.keystore`
- **Key alias**: `dukaan_alias`
- **Store password**: `password`
- **Key password**: `password`

> ⚠️ Keep the keystore file safe. If lost, you cannot update the app on Play Store.

---

## Build Release AAB

```bash
./gradlew bundleRelease
```

Output file location:
```
app/build/outputs/bundle/release/app-release.aab
```

---

## Google Play Store Setup

### Developer Account
- URL: https://play.google.com/console
- One-time fee: ₹2,100
- Payment: Credit/Debit card

### App Details to Fill

| Field | Value |
|---|---|
| **App name** | Dukaan AI - Billing & Khata |
| **Package ID** | com.dukaan.ai |
| **Version** | 1.0 (versionCode: 1) |
| **Min Android** | Android 7.0 (API 24) |
| **Target Android** | Android 14 (API 34) |
| **Category** | Business |
| **Content rating** | Everyone |
| **Price** | Free |

### Short Description (80 chars max)
```
Smart billing, khata & OCR scanner for Indian shopkeepers
```

### Full Description
```
Dukaan AI is the smartest billing app for Indian shopkeepers.

✅ Voice Billing — Create bills by speaking in Hindi/English
✅ Smart Khata — Track customer dues and payments
✅ Bill Scanner — Scan purchase bills with AI (OCR)
✅ Wholesale Orders — Manage supplier orders
✅ PDF Invoices — Generate professional GST invoices
✅ WhatsApp Share — Send bills directly to customers

Built for small businesses, kirana shops, and wholesalers.
Works offline. No internet required for billing.
```

### Required Assets
| Asset | Size | Notes |
|---|---|---|
| App Icon | 512 x 512 px PNG | No transparency |
| Feature Graphic | 1024 x 500 px PNG | Banner image |
| Phone Screenshots | Min 2, Max 8 | 16:9 ratio recommended |
| Tablet Screenshots | Optional | 1280x800 px |

---

## Privacy Policy (Required by AdMob)

### Free Hosting Option
1. Go to https://privacypolicygenerator.info
2. Fill: App name = Dukaan AI, Company = your name
3. Enable: **Advertising** (AdMob), **Camera** (OCR)
4. Generate → Copy link
5. Paste URL in Play Console → Store listing → Privacy Policy

### What to mention in policy
- Camera permission: Used for bill scanning (OCR)
- Internet permission: Used for AI features and ads (AdMob)
- Data stored: Bills and customer data stored locally on device
- Third-party: Google AdMob shows ads

---

## Play Store Submission Steps

1. Go to **play.google.com/console** → Create app
2. Fill **Store listing** (name, description, screenshots, icon)
3. Fill **Content rating** questionnaire → Submit
4. Set **Pricing** → Free → Select India (or all countries)
5. Go to **Production** → Create new release
6. Upload `app-release.aab`
7. Fill release notes → Review → Submit

### Release Notes (v1.0)
```
First release of Dukaan AI
• Voice billing in Hindi/English
• Smart Khata for customer tracking
• AI Bill Scanner (OCR)
• PDF invoice generation
• WhatsApp sharing
```

---

## After Publishing

### Link App to AdMob
1. Go to admob.google.com → Apps
2. Click your app → **Link to Google Play**
3. Search for "Dukaan AI" → Link
4. Wait 24–48 hours for real ads to appear

### Revenue Timeline
| Time | What happens |
|---|---|
| Day 1-2 | App under Google review |
| Day 3-7 | App approved & live |
| Day 7-14 | AdMob verifies & activates |
| Day 15+ | Real ad revenue starts |
| Day 30 | First payment threshold (₹7,000 min) |

### Payment Setup in AdMob
1. AdMob → Payments → Add payment info
2. Select: **Wire Transfer** or **Check**
3. Minimum payout: **$100 (~₹8,300)**
4. Payment date: **Monthly (21st of each month)**

---

## Version Update Process (Future Releases)

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`
2. Run `./gradlew bundleRelease`
3. Upload new AAB to Play Console → Production → Create release

```kotlin
// app/build.gradle.kts
versionCode = 2        // increment by 1 each release
versionName = "1.1"    // human readable version
```

---

*Document created: March 2026*
*App: Dukaan AI | Package: com.dukaan.ai*
