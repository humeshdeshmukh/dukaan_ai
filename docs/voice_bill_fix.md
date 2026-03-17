Now I have the full picture. Here's the complete analysis:

Voice Billing — Complete Flow & UI Breakdown
Screen Structure

VoiceBillingScreen
├── TopBar
│   ├── "Voice Bill" title
│   └── TabRow
│       ├── Tab 0: 🎤 New Bill   ← main creation screen
│       └── Tab 1: 🧾 History
│
├── [Tab 0] NewBillTab (LazyColumn)
│   ├── CustomerPickerRow         ← select/type customer
│   ├── CompactVoiceInput         ← mic + scan list button + live waveform
│   ├── Error banner (if any)
│   ├── Items Header              ← "Bill Items (N)" + Add + Clear All
│   ├── BillItemCard × N          ← each item: name, qty, unit, ₹ total + edit/delete
│   ├── BillSummaryCard           ← subtotal → discount% → tax% → grand total
│   ├── PaymentModeChips          ← CASH / UPI / BANK
│   └── Notes field
│
├── [Tab 1] HistoryTab
│   ├── Search bar
│   ├── Filter chips (sort, payment mode, date range)
│   ├── Today's bills group
│   ├── Older bills group
│   └── Total for selected range
│
└── Bottom Bar (Tab 0 only)
    ├── Save Draft
    ├── WhatsApp (save + send PDF)
    └── Save PDF (save + generate)




  Voice Input Component — CompactVoiceInput
  ┌──────────────────────────────────────────────────────┐
│  [🎤]   Tap to speak items...              [📷 scan] │
│         ▐▌ ▐▌ ▌ ▐ ▏  ← 5 animated RMS bars          │
│         "2 kg chawal 80 rupees..."   ← partial text  │
│         AI is parsing... ⟳          ← while Gemini   │
└──────────────────────────────────────────────────────┘


Background: blue tint (idle) → red tint (listening)
5 bars animate via animateFloatAsState(tween(100ms)) based on RMS audio level
Partial speech shown live in quotes as user speaks
Complete Voice Flow — Step by Step


User taps Mic
    │
    ├─ No RECORD_AUDIO permission → Android permission dialog → on grant → continue
    │
    ▼
ViewModel.toggleRecording()
    │
    ▼
SpeechManager.startListening(continuous=true, speechCode="hi-IN"/"en-IN"/...)
    │
    ├─ Creates Android SpeechRecognizer
    ├─ Sets LANGUAGE_MODEL_FREE_FORM
    ├─ Adds 12 Indian language extras (en-IN, hi-IN, mr-IN, ta-IN, te-IN, bn-IN...)
    └─ EXTRA_PARTIAL_RESULTS = true
    │
    ▼ While user speaks:
    │
    ├─ onRmsChanged() → normalize RMS (-2..10dB → 0..1) → audioLevel StateFlow
    │   UI: 5 live bars animate in real-time
    │
    ├─ onPartialResults() → speechText StateFlow
    │   UI: live text shown in quotes under mic
    │
    ▼ Speech ends (silence detected):
    │
    onResults() → finalResult SharedFlow emits text
    │
    [Continuous mode: SpeechManager auto-restarts after 500ms]
    │
    ▼
ViewModel.processSpeech(text)
    │
    ├─ isParsing = true  →  UI shows "AI is parsing... ⟳"
    │
    ▼
GeminiBillingService.parseBillingSpeech(text, languageCode)
    [gemini-2.5-flash-lite]
    │
    ▼
List<BillItem> returned
    │
    ▼
mergeItems(existing, new)
    │  same name + same unit → qty += qty, price += price
    │  new item → append
    │
    ▼
recalculate()
    │
    ▼
UI updates: new items appear with slide-in + fade animation


Calculation Formula (Voice Billing)
This is different from the OCR scan — user sets the % manually, app calculates ₹ amounts:  


subtotal      = Σ item.total (= item.price, which is the line total)
discountAmount = subtotal × discountPercent / 100
afterDiscount  = subtotal - discountAmount
taxAmount      = afterDiscount × taxPercent / 100    ← GST on post-discount ✓
grandTotal     = afterDiscount + taxAmount

Set via BillSummaryCard sliders/inputs. Recalculated on every item add/edit/delete.

Scan List Feature (Secondary Input)
When user taps the 📷 icon instead of mic:

GmsDocumentScanning (up to 5 pages)
    │
    ▼
processScannedCustomerListPages()
    │
    Step 1: ML Kit OCR (on original bitmap)
    Step 2: enhanceImageForHandwriting()
            ─ ColorMatrix: contrast 1.4×, brightness -30
            ─ Makes handwritten pen marks stand out
    Step 3: geminiService.parseCustomerListImage(enhancedBitmap, ocrText)
            [gemini-2.5-flash-lite + image]
    Step 4: Merge duplicates by name+unit, sum qty+price
    Step 5: mergeItems(existingBill, scannedItems)
    Step 6: recalculate()
    UI: snackbar "N items added from scanned list!"


    Issues Found — Same as OcrViewModel Before Our Fix
BillingViewModel has the exact same 3 issues we already fixed in OcrViewModel:

#	Issue	Location in BillingViewModel
1	Only DevanagariTextRecognizerOptions used	Line 87-89
2	Scan list OCR runs sequentially	Line 453: bitmaps.map { extractTextFromBitmap(it) }
3	No bitmap resize before Gemini on enhanced bitmaps	Lines 462, 515, 554
Also one extra issue unique to BillingViewModel:

enhanceImageForHandwriting() allocates a new full-resolution Bitmap for every page — then sends it full-res to Gemini. Should resize after enhancement, before Gemini call.
Want me to apply the same 3 fixes to BillingViewModel.kt now?