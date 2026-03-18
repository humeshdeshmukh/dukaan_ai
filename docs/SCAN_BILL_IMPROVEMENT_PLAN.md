# Scan Bill Feature - Improvement Plan

## Executive Summary
This document outlines accuracy issues, calculation problems, and UI improvements for the Scan Bill feature in Dukaan AI.

---

## Current Architecture Overview

```
Camera/Gallery → ML Kit OCR (Devanagari + Latin) → Gemini AI Vision → Bill Data → User Verification → Save
```

### Key Files
| Component | File Path |
|-----------|-----------|
| ViewModel | `feature-ocr/src/main/java/com/dukaan/feature/ocr/ui/OcrViewModel.kt` |
| Result Screen | `feature-ocr/src/main/java/com/dukaan/feature/ocr/ui/OcrResultScreen.kt` |
| AI Parser | `core/core-network/src/main/java/com/dukaan/core/network/ai/GeminiBillingService.kt` |
| Data Model | `core/core-network/src/main/java/com/dukaan/core/network/model/AIModels.kt` |

---

## PART 1: ACCURACY ISSUES IDENTIFIED

### Issue 1.1: No Subtotal Validation Against Items Sum
**Problem**: When Gemini returns a subtotal, there's no check if it matches `items.sumOf { it.total }`. This can lead to:
- Silent mismatches when items are missed
- User doesn't know if all items were captured

**Current Code** (GeminiBillingService.kt:160):
```kotlin
val finalSubtotal = if (subtotal > 0.0) subtotal else items.sumOf { it.total }
// No validation against each other!
```

**Solution**: Add mismatch detection and show warning to user.

### Issue 1.2: Price Field Ambiguity
**Problem**: `BillItem.price` is documented as "line total" but Gemini prompt asks for `quantity × per-unit rate`. If Gemini returns per-unit price, calculations break.

**Current Model** (AIModels.kt:31):
```kotlin
val price: Double   // Total price for this line item (NOT per-unit)
```

**Solution**: Add `unitPrice` field to model for accurate per-unit calculations.

### Issue 1.3: No Per-Unit Rate Display in Edit Dialog
**Problem**: When editing an item, user sees only total price. They can't verify if rate × qty = total.

**Current UI** (OcrResultScreen.kt:560-568):
```kotlin
OutlinedTextField(
    value = price,  // Just total price
    label = { Text(strings.priceLabel) },
    ...
)
```

**Solution**: Show per-unit rate (derived or entered) alongside total price.

### Issue 1.4: Discount Preservation When Items Change
**Problem**: When items are edited, `discountAmount` is preserved as rupee value even if subtotal changes significantly. This can cause incorrect discount proportions.

**Current Logic** (OcrViewModel.kt:312-328):
```kotlin
discountAmount = original.discountAmount,  // PRESERVED unchanged
discountPercent = newDiscountPercent,      // Only % recalculates
```

**Example Bug**:
- Original: Subtotal ₹1000, Discount ₹100 (10%)
- User removes ₹500 item
- New: Subtotal ₹500, Discount still ₹100 (now 20%!)

**Solution**: Ask user whether to preserve amount or percentage when subtotal changes significantly.

### Issue 1.5: No Cross-Verification Against Original Image
**Problem**: User has no automated way to verify extracted data against the bill image.

**Solution**: Add side-by-side comparison or highlight mode.

---

## PART 2: DISCOUNT CALCULATION ISSUES

### Issue 2.1: No Per-Item Discount Support
**Problem**: Indian wholesale bills often have per-item discounts (e.g., "10% off on Sugar"). Current model only supports bill-level discount.

**Current Model** (AIModels.kt):
```kotlin
data class BillItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double  // No discount field!
)

data class Bill(
    val discountPercent: Double = 0.0,  // Bill-level only
    val discountAmount: Double = 0.0,   // Bill-level only
)
```

**Impact**: Items with individual discounts must have the discounted price as `price` field, losing discount tracking.

**Solution**:
```kotlin
data class BillItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,         // Per-unit rate
    val discountPercent: Double = 0.0,  // Item-level discount
    val discountAmount: Double = 0.0,   // Item-level discount
) {
    val grossTotal: Double get() = unitPrice * quantity
    val netTotal: Double get() = grossTotal - discountAmount
}
```

### Issue 2.2: Discount Recalculation Logic Flawed
**Problem**: When items change, discount amount is fixed but percentage recalculates — contradicts user expectation.

**Current** (OcrViewModel.kt:315-316):
```kotlin
val newDiscountPercent = if (newSubtotal > 0 && original.discountAmount > 0)
    (original.discountAmount / newSubtotal * 100) else original.discountPercent
```

**Solution**: Provide UI option: "Preserve discount amount" vs "Preserve discount percentage".

---

## PART 3: SUBTOTAL ACCURACY ISSUES

### Issue 3.1: Multiple Subtotal Sources Without Validation
**Problem**: Subtotal can come from three sources with no consistency check:
1. Gemini's extracted subtotal
2. Sum of `items.sumOf { it.total }`
3. After editing: always recalculated from items

**Locations**:
- GeminiBillingService.kt:160: `if (subtotal > 0.0) subtotal else items.sumOf { it.total }`
- OcrResultScreen.kt:307: `bill.subtotal.takeIf { it > 0 } ?: bill.items.sumOf { it.total }`
- OcrViewModel.kt:313: `items.sumOf { it.total }`

**Solution**: Add validation layer that compares Gemini subtotal vs items sum and warns on mismatch.

### Issue 3.2: Missing Items Not Detected
**Problem**: If Gemini misses an item but returns correct total, user won't notice.

**Example**:
- Bill has 5 items, Gemini extracts 4
- Gemini extracted correct totalAmount from image
- Items sum doesn't match total — no warning shown

**Solution**: Show "Items total vs Bill total" comparison with visual indicator.

### Issue 3.3: Rounding Errors Not Handled
**Problem**: Double arithmetic can cause small mismatches (e.g., ₹99.999 vs ₹100.00).

**Solution**: Add tolerance-based comparison for financial values.

---

## PART 4: UI IMPROVEMENT PLAN

### UI Improvement 4.1: Enhanced Item Card
**Current**: Shows name, qty, unit, total price only.

**Improved Design**:
```
┌─────────────────────────────────────────────────┐
│ Sugar                                    [Edit] │
│ 2 kg × ₹45/kg                           [Delete]│
│ Discount: 10% (-₹9)                             │
│ ─────────────────────────────────────────────── │
│ Line Total:                            ₹81.00   │
└─────────────────────────────────────────────────┘
```

**Changes**:
- Show per-unit rate (unitPrice) × qty
- Show item discount if present
- Show line total clearly

### UI Improvement 4.2: Enhanced Edit Dialog
**Current Fields**: Name, Qty, Unit, Price (total)

**Improved Fields**:
```
┌──────────────────────────────────────────┐
│                 Edit Item                 │
├──────────────────────────────────────────┤
│ Item Name: [Sugar________________]        │
│                                           │
│ [Qty: 2___] [Unit: kg▼]                  │
│                                           │
│ Rate (per unit): ₹[45.00_____]           │
│                                           │
│ Discount:  [10_%] or ₹[9.00___]          │
│                                           │
│ ───────────────────────────────────────  │
│ LINE TOTAL:                    ₹81.00    │
│ (Auto-calculated: rate × qty - discount) │
├──────────────────────────────────────────┤
│            [Cancel]    [Save]            │
└──────────────────────────────────────────┘
```

### UI Improvement 4.3: Subtotal Mismatch Warning Banner
**When to show**: Items sum ≠ Gemini subtotal (tolerance: ₹0.50)

```
┌──────────────────────────────────────────────────┐
│ ⚠️ SUBTOTAL MISMATCH                             │
│                                                  │
│ Items Total:      ₹1,150.00                     │
│ Bill Subtotal:    ₹1,200.00                     │
│ Difference:       ₹50.00                         │
│                                                  │
│ Possible missing item. Please verify against     │
│ the bill image.                                  │
│                                                  │
│ [View Bill Image]  [Use Items Total]  [Ignore]  │
└──────────────────────────────────────────────────┘
```

### UI Improvement 4.4: Split-Screen Verification Mode
**Purpose**: Allow user to compare extracted data with bill image.

```
┌─────────────────────┬─────────────────────┐
│                     │                     │
│    BILL IMAGE       │   EXTRACTED DATA    │
│    (zoomable)       │                     │
│                     │   Sugar   2kg  ₹90  │
│    [original bill]  │   Rice    5kg ₹250  │
│                     │   Dal     1kg  ₹80  │
│                     │   ...               │
│                     │                     │
│                     │   Total: ₹420.00   │
│                     │                     │
└─────────────────────┴─────────────────────┘
         [Switch to List View]
```

### UI Improvement 4.5: Accuracy Confidence Indicator
**Purpose**: Show AI confidence for extracted data.

```
Items Extracted: 12/12 ✓  (High Confidence)
Subtotal Match:  ✓ ₹1,200.00
Discount Found:  ✓ 5% (-₹60)
GST Detected:    ✓ 18% (+₹205.20)
                 ─────────────
Grand Total:     ₹1,345.20
```

### UI Improvement 4.6: Quick Edit Chips for Common Units
**Purpose**: Speed up unit correction with common Indian units.

```
Unit: [kg]  [Suggested: g | L | pc | pkt | box | dozen]
```

### UI Improvement 4.7: Discount Edit in Summary Card
**Currently**: Discount is shown but cannot be edited in OCR result screen.

**Improvement**: Add edit capability for discount % and tax % in the summary card (like VoiceBillingScreen).

---

## PART 5: DATA MODEL IMPROVEMENTS

### Proposed BillItem Model V2
```kotlin
data class BillItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,              // Per-unit rate (new)
    val itemDiscountPercent: Double = 0.0,  // Item-level discount % (new)
    val itemDiscountAmount: Double = 0.0,   // Item-level discount ₹ (new)
) {
    val grossTotal: Double get() = unitPrice * quantity
    val netTotal: Double get() = (grossTotal - itemDiscountAmount).coerceAtLeast(0.0)

    // Backward compatibility
    val price: Double get() = netTotal
    val total: Double get() = netTotal
}
```

### Migration Strategy
1. Keep existing `price` field as computed property
2. Add `unitPrice` defaulting to `price / quantity`
3. Gemini prompt updated to return `unitPrice` instead of `price`
4. UI shows per-unit rate; total is calculated

---

## PART 6: IMPLEMENTATION PRIORITY

### Phase 1: Critical Accuracy Fixes (High Priority)
| Task | Effort | Impact |
|------|--------|--------|
| Add subtotal mismatch warning | 2 hrs | High |
| Show per-unit rate in item card | 1 hr | Medium |
| Add tolerance-based comparison | 0.5 hr | Medium |
| Update edit dialog with rate field | 2 hrs | High |

### Phase 2: Discount Improvements (Medium Priority)
| Task | Effort | Impact |
|------|--------|--------|
| Add `unitPrice` to BillItem model | 1 hr | High |
| Add item-level discount fields | 2 hrs | Medium |
| Update Gemini prompt for unitPrice | 1 hr | High |
| Option to preserve % or amount | 1 hr | Medium |

### Phase 3: UI Enhancements (Recommended)
| Task | Effort | Impact |
|------|--------|--------|
| Accuracy confidence indicator | 2 hrs | Medium |
| Split-screen verification mode | 4 hrs | High |
| Quick edit chips for units | 1 hr | Low |
| Editable discount in summary | 1 hr | Medium |

### Phase 4: Advanced Features (Future)
| Task | Effort | Impact |
|------|--------|--------|
| Per-item discount UI | 4 hrs | Medium |
| Auto-suggest unit corrections | 3 hrs | Low |
| Highlight items on image | 6 hrs | High |

---

## PART 7: GEMINI PROMPT IMPROVEMENTS

### Current Prompt Issues
1. Asks for `price` as "line total" but context implies per-unit rate
2. No instruction to detect per-item discounts
3. No confidence scoring for extracted fields

### Improved Prompt (Excerpt)
```
Return ONLY this JSON:
{
  "items": [
    {
      "name": "Sugar",
      "quantity": 2.0,
      "unit": "kg",
      "unitPrice": 45.0,           // Per-unit rate
      "lineTotal": 90.0,           // unitPrice × quantity
      "itemDiscount": 0.0,         // Discount on this item (rupees)
      "itemDiscountPercent": 0.0,  // Discount on this item (%)
      "confidence": 0.95           // AI confidence for this item
    }
  ],
  "subtotal": 1200.0,              // Sum of all lineTotals
  "itemsSum": 1200.0,              // Verification: sum we calculated
  "discountAmount": 60.0,
  "taxAmount": 205.20,
  "totalAmount": 1345.20,
  "confidence": 0.92               // Overall confidence
}
```

---

## Summary Checklist

### Accuracy
- [ ] Add subtotal vs items sum validation
- [ ] Add mismatch warning banner
- [ ] Add tolerance for rounding (₹0.50)
- [ ] Track per-unit rate in model

### Discount
- [ ] Add `unitPrice` to BillItem
- [ ] Add item-level discount fields
- [ ] Add option to preserve % or amount on edit
- [ ] Update Gemini prompt

### UI
- [ ] Enhanced item card with rate × qty
- [ ] Enhanced edit dialog with rate field
- [ ] Subtotal mismatch warning banner
- [ ] Split-screen verification mode
- [ ] Confidence indicator
- [ ] Editable discount/tax in summary

---

*Generated: 2026-03-18*
*For: Dukaan AI Android App*
