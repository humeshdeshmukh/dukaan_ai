# Dukaan AI - Ad Monetization Plan (Enhanced)

## Overview
Ad monetization strategy using **Micro Ads** - small, contextual ads shown during natural waiting periods (scanning, processing, loading). No watermarks, no forced rewards, no frustration.

---

## Ad Network Recommendation

### Primary: Google AdMob
- **Why**: Best for Indian market, reliable payments, easy integration
- **Cost**: FREE to integrate
- **Requirements**: Google Play Developer Account (₹2,100 one-time)

### Secondary: Facebook Audience Network
- Higher fill rates when AdMob doesn't have ads
- Use as mediation partner

---

## Micro Ads Strategy (Core Concept)

### What are Micro Ads?
Small, non-blocking ads shown during **natural waiting moments** when user is already waiting for something. User doesn't feel interrupted because they're already in a "waiting" state.

### Key Principle
> **"Show ads when user is waiting, not when user is working"**

---

## Ad Placement Map

### 🔄 During Processing States (Micro Ads)

| Process | Ad Type | Duration | Placement |
|---------|---------|----------|-----------|
| **Bill Scanning (OCR)** | Small Banner | 2-5 sec | Below scanning animation |
| **Item List Processing** | Native Card | 3-6 sec | Below progress indicator |
| **PDF Generating** | Small Banner | 1-3 sec | Below "Generating..." text |
| **Image Upload** | Native Card | 2-4 sec | Below upload progress |
| **AI Processing** | Medium Banner | 3-8 sec | Below AI thinking animation |
| **Data Syncing** | Small Banner | 1-3 sec | Below sync indicator |

### 📱 Static Placements (Always Visible)

| Screen | Ad Type | Position | User Impact |
|--------|---------|----------|-------------|
| **Bill List** | Banner (320x50) | Bottom | Very Low |
| **Dashboard** | Native Ad | Between cards | Low |
| **Transaction History** | Native Ad | Every 10 items | Low |
| **Settings** | Banner (320x50) | Bottom | Very Low |
| **Khata List** | Banner (320x50) | Bottom | Very Low |

### ⏸️ Transition Interstitials

| Trigger | Frequency | Type |
|---------|-----------|------|
| After saving bill | Every 4th bill | Interstitial |
| After PDF download | Every 3rd download | Interstitial |
| After adding customer | Every 5th customer | Interstitial |
| App resume (background > 5 min) | Once per session | Interstitial |

---

## Visual Flow: Micro Ads in Action

```
┌─────────────────────────────────────┐
│         BILL SCANNING               │
├─────────────────────────────────────┤
│                                     │
│      📷 [Camera Preview]            │
│                                     │
│      ════════════════════           │
│      "Scanning items..."            │
│      ▓▓▓▓▓▓▓▓░░░░░ 65%             │
│      ════════════════════           │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  🛒 Shop smart, save more!  │    │  ← Micro Ad
│  │     [Small Banner Ad]       │    │     (During scan)
│  └─────────────────────────────┘    │
│                                     │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│       ITEM LIST PROCESSING          │
├─────────────────────────────────────┤
│                                     │
│   🔄 Processing 12 items...         │
│                                     │
│   ✓ Rice 5kg - ₹250                │
│   ✓ Sugar 2kg - ₹90                │
│   ⏳ Calculating totals...          │
│                                     │
│  ┌─────────────────────────────┐    │
│  │ ┌───┐ Best Deals Near You   │    │  ← Native Ad Card
│  │ │ 🏪│ Save up to 20% today  │    │     (Blends with UI)
│  │ └───┘ [Sponsored]           │    │
│  └─────────────────────────────┘    │
│                                     │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│         PDF GENERATING              │
├─────────────────────────────────────┤
│                                     │
│         📄                          │
│    Generating Invoice...            │
│    ░░░░░░░░░░░░░░░░░               │
│                                     │
│  ┌─────────────────────────────┐    │
│  │   [Banner Ad - 320x50]      │    │  ← Micro Ad
│  └─────────────────────────────┘    │
│                                     │
└─────────────────────────────────────┘
```

---

## Implementation Architecture

### Ad Manager Structure

```
app/src/main/java/com/dukaan/ai/
└── ads/
    ├── AdManager.kt              // Singleton - manages all ads
    ├── MicroAdController.kt      // Controls micro ads timing
    ├── composables/
    │   ├── BannerAdView.kt       // Composable banner
    │   ├── NativeAdCard.kt       // Native ad card
    │   └── ProcessingAdOverlay.kt // Micro ad during loading
    ├── InterstitialAdHelper.kt   // Interstitial management
    └── AdConfig.kt               // Frequency & placement config
```

### MicroAdController Logic

```kotlin
class MicroAdController {
    // Show micro ad only if process takes > 1.5 seconds
    fun shouldShowMicroAd(processType: ProcessType): Boolean {
        val minDuration = when (processType) {
            ProcessType.OCR_SCAN -> 1500L      // 1.5 sec
            ProcessType.ITEM_PROCESS -> 2000L  // 2 sec
            ProcessType.PDF_GENERATE -> 1000L  // 1 sec
            ProcessType.AI_PROCESS -> 2000L    // 2 sec
        }
        return estimatedDuration >= minDuration
    }

    // Auto-hide when process completes
    fun onProcessComplete() {
        hideCurrentMicroAd()
    }
}
```

---

## Ad Frequency Limits (Anti-Frustration)

| Rule | Limit |
|------|-------|
| Max interstitials per hour | 4 |
| Min gap between interstitials | 3 minutes |
| Max micro ads per session | 15 |
| Micro ad minimum display | 1.5 seconds |
| Micro ad maximum display | 8 seconds |
| Banner refresh rate | 60 seconds |

### Smart Frequency Logic

```kotlin
object AdFrequencyManager {
    private var lastInterstitialTime = 0L
    private var interstitialCountThisHour = 0
    private var microAdCountThisSession = 0

    fun canShowInterstitial(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastInterstitialTime
        return timeSinceLast > 180_000 && // 3 min gap
               interstitialCountThisHour < 4
    }

    fun canShowMicroAd(): Boolean {
        return microAdCountThisSession < 15
    }
}
```

---

## Cost Breakdown

| Item | Cost | Type |
|------|------|------|
| Google Play Developer Account | ₹2,100 | One-time |
| AdMob Integration | ₹0 | Free |
| Firebase Analytics | ₹0 | Free tier |
| Privacy Policy Hosting | ₹0 | GitHub Pages |
| **Total Initial Cost** | **₹2,100** | |

---

## Revenue Projections (Updated)

### AdMob eCPM Rates (India)

| Ad Type | eCPM (₹) | Notes |
|---------|----------|-------|
| Banner | ₹8 - ₹20 | Higher with good placement |
| Native | ₹15 - ₹35 | Blends well, better engagement |
| Interstitial | ₹25 - ₹60 | Best revenue per impression |

### Revenue by User Base

#### 1,000 DAU (Starting Out)

| Ad Type | Daily Impressions | Monthly Revenue |
|---------|-------------------|-----------------|
| Banners (5 screens) | 8,000 | ₹1,920 - ₹4,800 |
| Micro Ads (processing) | 3,000 | ₹900 - ₹2,100 |
| Native Ads | 2,000 | ₹900 - ₹2,100 |
| Interstitials | 250 | ₹188 - ₹450 |
| **Total** | | **₹3,900 - ₹9,450/month** |

#### 10,000 DAU (Growing)

| Ad Type | Daily Impressions | Monthly Revenue |
|---------|-------------------|-----------------|
| Banners | 80,000 | ₹19,200 - ₹48,000 |
| Micro Ads | 30,000 | ₹9,000 - ₹21,000 |
| Native Ads | 20,000 | ₹9,000 - ₹21,000 |
| Interstitials | 2,500 | ₹1,875 - ₹4,500 |
| **Total** | | **₹39,000 - ₹94,500/month** |

#### 50,000 DAU (Established)

| Ad Type | Monthly Revenue |
|---------|-----------------|
| All Ads Combined | **₹2,00,000 - ₹4,75,000/month** |

### Why Micro Ads Increase Revenue

| Factor | Impact |
|--------|--------|
| More ad impressions | +40% more ad views |
| Better viewability | Ads seen during focus moments |
| Higher engagement | Users not annoyed = better CTR |
| Lower skip rate | Can't skip what you're waiting for |

---

## Screen-by-Screen Implementation

### 1. Bill Scanning Screen

```kotlin
@Composable
fun BillScanningScreen() {
    Column {
        CameraPreview()

        if (isScanning) {
            ScanningProgress()

            // Micro Ad - shows only during scanning
            MicroAdBanner(
                adUnitId = AdConfig.MICRO_AD_SCANNING,
                minDisplayTime = 1500
            )
        }
    }
}
```

### 2. Item Processing

```kotlin
@Composable
fun ItemProcessingOverlay(items: List<ScannedItem>) {
    Column {
        ProcessingAnimation()
        ItemProgressList(items)

        // Native micro ad card
        MicroNativeAdCard(
            adUnitId = AdConfig.MICRO_AD_PROCESSING
        )
    }
}
```

### 3. Bill List Screen

```kotlin
@Composable
fun BillListScreen() {
    Scaffold(
        bottomBar = {
            // Persistent banner
            AdMobBanner(adUnitId = AdConfig.BANNER_BILL_LIST)
        }
    ) {
        LazyColumn {
            items(bills) { bill ->
                BillCard(bill)
            }
        }
    }
}
```

---

## User Experience Rules

### ✅ DO

1. Show micro ads only during genuine waiting
2. Auto-dismiss when process completes
3. Keep micro ads small (320x50 or 300x100)
4. Use native ads that match app design
5. Cache ads in advance for instant display
6. Respect user - max 15 micro ads per session

### ❌ DON'T

1. Never block user input with ads
2. Never show ad before process starts
3. Never force user to wait extra for ad
4. Never show adult/gambling content
5. Never stack multiple ads
6. Never show interstitial mid-action

---

## Implementation Timeline

| Week | Tasks |
|------|-------|
| **Week 1** | AdMob setup, Banner integration on all screens |
| **Week 2** | MicroAdController, Processing state ads |
| **Week 3** | Native ads, Interstitial with frequency control |
| **Week 4** | Testing, Analytics setup, Play Store update |

---

## Files to Create/Modify

### New Files
```
app/src/main/java/com/dukaan/ai/ads/
├── AdManager.kt
├── AdConfig.kt
├── MicroAdController.kt
├── AdFrequencyManager.kt
└── composables/
    ├── BannerAdView.kt
    ├── MicroAdBanner.kt
    ├── MicroNativeAdCard.kt
    └── ProcessingAdOverlay.kt
```

### Files to Modify
```
feature-billing/
├── BillScanScreen.kt      // Add micro ad during scan
├── ItemListScreen.kt      // Add micro ad during processing
├── BillDetailScreen.kt    // Add banner at bottom
└── BillListScreen.kt      // Add banner at bottom

feature-ocr/
└── OcrProcessingScreen.kt // Add micro ad during OCR

app/
├── MainActivity.kt        // Initialize AdMob
└── DukaanApplication.kt   // Initialize Mobile Ads SDK
```

---

## Technical Setup

### 1. Add Dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.google.android.gms:play-services-ads:22.6.0")
}
```

### 2. Manifest Setup

```xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

    <application>
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-XXXXXXXX~XXXXXXXX"/>
    </application>
</manifest>
```

### 3. Initialize in Application

```kotlin
class DukaanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        AdManager.preloadAds()
    }
}
```

---

## Ad Unit IDs Needed

| Placement | Ad Format | Test ID |
|-----------|-----------|---------|
| Bill List Banner | Banner | ca-app-pub-3940256099942544/6300978111 |
| Dashboard Banner | Banner | ca-app-pub-3940256099942544/6300978111 |
| Micro Ad Scanning | Banner | ca-app-pub-3940256099942544/6300978111 |
| Micro Ad Processing | Native | ca-app-pub-3940256099942544/2247696110 |
| Bill Save Interstitial | Interstitial | ca-app-pub-3940256099942544/1033173712 |

> Note: Replace test IDs with real IDs after AdMob approval

---

## Checklist

- [ ] Create AdMob account
- [ ] Create Privacy Policy (GitHub Pages - free)
- [ ] Register app in AdMob
- [ ] Create ad units (Banner, Native, Interstitial)
- [ ] Add dependency to build.gradle
- [ ] Update AndroidManifest.xml
- [ ] Create AdManager singleton
- [ ] Create MicroAdController
- [ ] Implement banner on all list screens
- [ ] Implement micro ads in processing states
- [ ] Implement interstitial with frequency control
- [ ] Test with test ad IDs
- [ ] Replace with production ad IDs
- [ ] Submit app update to Play Store

---

## Summary

| Metric | Value |
|--------|-------|
| **Initial Cost** | ₹2,100 only |
| **Monthly Revenue (1K users)** | ₹4,000 - ₹9,500 |
| **Monthly Revenue (10K users)** | ₹39,000 - ₹95,000 |
| **Monthly Revenue (50K users)** | ₹2L - ₹4.75L |
| **User Frustration** | Minimal (ads during wait) |
| **Implementation Time** | 3-4 weeks |

---

## Key Advantages of This Approach

1. **No Watermarks** - Clean PDFs, professional look
2. **No Forced Rewards** - Users don't feel manipulated
3. **Natural Ad Timing** - Ads when waiting, not interrupting
4. **Higher Revenue** - More impressions without annoyance
5. **Better Retention** - Users stay because app isn't annoying

---

*Document Updated: March 2026*
*Strategy: Micro Ads during Processing*
*For: Dukaan AI Android App*
