Below are **additional `.md` files** you should create in **Antigravity** to properly develop the app.
These files help AI understand the **product, architecture, database, API, UX, and development plan** .

Think of it like **documentation that guides the AI developer** .

You can create a folder:

```
docs/
```

And store these files.

---

# 📁 Recommended `.md` Files for Antigravity Development

```
docs/
│
├── PRODUCT_REQUIREMENTS.md
├── USER_FLOW.md
├── APP_ARCHITECTURE.md
├── DATABASE_SCHEMA.md
├── FEATURE_SPECIFICATIONS.md
├── VOICE_AI_PROMPTS.md
├── UI_UX_DESIGN_SYSTEM.md
├── API_SPECIFICATION.md
├── ERROR_HANDLING.md
├── SECURITY_PRIVACY.md
├── OFFLINE_SYNC_ARCHITECTURE.md
├── DEVELOPMENT_ROADMAP.md
├── TESTING_STRATEGY.md
└── MONETIZATION_STRATEGY.md
```

I will now give **the content for the most important ones** so you can paste directly.

---

# 1️⃣ PRODUCT_REQUIREMENTS.md

```markdown
# KiranaSarthi — Product Requirements

## Goal

Build the simplest AI powered shop management app for small Indian kirana stores.

The app must allow shopkeepers to manage:

- Sales
- Khata (Jama/Baki)
- Inventory
- Wholesale orders
- Bill scanning

using minimal typing.

The application should prioritize:

Voice input
Large buttons
Offline functionality
WhatsApp sharing
Hindi + Hinglish support

---

## Target Users

Primary users:

Small shop owners in India.

Characteristics:

- Age: 30–65
- Not tech savvy
- Prefer voice input
- Use WhatsApp daily

---

## Key Problems

1. Shopkeepers forget who owes money.
2. Bills from wholesalers are hard to record.
3. Stock tracking is manual.
4. Creating wholesale order lists takes time.

---

## Core Solution

Provide four main AI powered features:

1. Bill Scanner
2. Smart Khata
3. Voice Billing
4. Wholesale Order Generator

---

## Success Metrics

Daily Active Users

Voice usage rate

Bill scan success rate

Khata reminder usage

---

## Non Goals

Not a full accounting software.

Not a GST filing tool.
```

---

# 2️⃣ USER_FLOW.md

```markdown
# User Flow — KiranaSarthi

## App Launch

Splash Screen

↓

Language Selection

Hindi
English
Marathi

↓

Permissions

Camera
Microphone
Storage

↓

Shop Setup

Shop Name
Owner Name
Phone

↓

Dashboard
```

---

## Main Dashboard Flow

```
Dashboard

Scan Bill
Voice Billing
Smart Khata
Wholesale Order
Inventory
```

---

## Scan Bill Flow

```
Dashboard
↓
Scan Bill
↓
Camera Capture
↓
AI Processing
↓
Review Extracted Data
↓
Save Purchase
↓
Inventory Updated
```

---

## Khata Flow

```
Dashboard
↓
Smart Khata
↓
Search Customer
↓
Speak Entry
↓
Confirm Transaction
↓
Balance Updated
```

---

## Voice Billing Flow

```
Dashboard
↓
Voice Billing
↓
Start Listening
↓
Items Added
↓
Bill Summary
↓
Save / Share
```

---

# 3️⃣ APP_ARCHITECTURE.md

```markdown
# Application Architecture

Architecture Pattern:

Clean Architecture

Layers:

Presentation Layer
Domain Layer
Data Layer

---

## Presentation Layer

Jetpack Compose UI

ViewModels

State Management

---

## Domain Layer

Business logic

Use cases

Examples:

CreateSaleUseCase
AddKhataEntryUseCase
ParseVoiceInputUseCase

---

## Data Layer

Repositories

Room Database

Network APIs

---

## Modules

app
core
feature-billing
feature-khata
feature-ocr
feature-inventory
feature-orders
```

---

# 4️⃣ DATABASE_SCHEMA.md

```markdown
# Database Schema

Database: Room (SQLite)

---

## Customers

id
name
phone
balance
createdAt

---

## Transactions

id
customerId
amount
type
date

Types:

JAMA
BAKI
PAYMENT

---

## Inventory

id
name
stock
costPrice
sellingPrice

---

## Sales

id
items
total
date

---

## SellerPurchases

id
seller
item
qty
price
date

---

## WholesaleOrders

id
seller
items
status
date
```

---

# 5️⃣ VOICE_AI_PROMPTS.md

```markdown
# Voice AI Prompts

## Khata Entry Prompt

Understand the shopkeeper message.

Extract:

Customer name
Amount
Transaction type

Return JSON format.

Example input:

Ramesh 500 jama

Output:

{
customer: "Ramesh",
amount: 500,
type: "JAMA"
}
```

---

## Voice Billing Prompt

Extract items from shopkeeper speech.

Return structured items.

Example:

Chini 2 kilo 80

Output:

{
name: "Chini",
quantity: 2,
unit: "kg",
price: 40
}

````

---

# 6️⃣ UI_UX_DESIGN_SYSTEM.md

```markdown
# UI Design System

Theme: Premium Light

Primary Color: #0F62FE

Accent Color: #00B37E

Background: #FBFCFD

Card: #FFFFFF

Error: #FF6B6B

---

## Typography

Headings: Poppins

Body: Inter

Hindi support: Noto Sans Devanagari

---

## Button Rules

Minimum height: 56dp

Large touch area.

Rounded corners.

---

## Icon Style

Simple

Filled icons

High contrast
````

---

# 7️⃣ OFFLINE_SYNC_ARCHITECTURE.md

```markdown
# Offline Sync Architecture

The app should work without internet.

All data stored locally using Room.

When internet available:

Background sync using WorkManager.

Sync steps:

1. Detect internet
2. Upload unsynced data
3. Download updates
4. Resolve conflicts

Conflict strategy:

Last write wins.
```

---

# 8️⃣ DEVELOPMENT_ROADMAP.md

```markdown
# Development Roadmap

Phase 1 — MVP

Smart Khata
Voice Billing
Local database
WhatsApp share

Timeline:

4 weeks

---

Phase 2

Bill Scanner
Inventory
Wholesale order

Timeline:

8 weeks

---

Phase 3

AI price suggestion
Analytics dashboard
Cloud backup
```

---

# 9️⃣ TESTING_STRATEGY.md

```markdown
# Testing Strategy

Unit Tests

Database operations

Voice parsing

---

Integration Tests

OCR pipeline

Voice recognition

---

UI Tests

Jetpack Compose testing

---

Manual Testing

Real shop environment testing.
```

---
