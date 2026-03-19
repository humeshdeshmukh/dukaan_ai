i want to add open cv for image cleaning and improve my bill scan feature more accuret and from my seting psf preview feature remove and resizing image before sending to gemini to reduce high token cost

I'll help you add OpenCV for image cleaning, improve bill scan accuracy, remove the PDF preview feature from settings, and add image resizing before sending to Gemini. This is a multi-step task that requires exploring the codebase and planning the implementation.

Let me enter plan mode to design the best approach for these changes.

I'm now in plan mode. Let me explore the codebase to understand the current bill scan implementation, Gemini integration, and settings features.

Excellent! I now have comprehensive information about:

Bill Scan & Gemini Integration:

Images sent at FULL RESOLUTION to Gemini (no resizing currently)
Uses ML Kit dual OCR (Devanagari + Latin)
Bitmap compressed at JPEG 90% when saved to disk, but full-res sent to Gemini
Main files: OcrViewModel.kt, GeminiBillingService.kt
Two API methods: parseBillImageWithOcr (single page) and parseMultiPageBillWithOcr (multi-page)
PDF Preview in Settings:

Located in SettingsScreen.kt (lines 279-289) - button
Wired in AppNavigation.kt (lines 687-726) - generates sample PDF with dummy data
Dialog shown at PdfPreviewDialog.kt
Easy to remove - just delete the button and its click handler
Existing Image Utilities:

enhanceImageForHandwriting in BillingViewModel.kt - uses ColorMatrix for contrast enhancement
No centralized utility class for image operations
JPEG compression at 90% quality
No resizing functions currently exist
Now I should move to Phase 2: Design - launch a Plan agent to design the implementation approach for:

Adding OpenCV for image cleaning and preprocessing
Image resizing before Gemini API calls
Removing PDF preview from settings
Let me launch a Plan agent to design this.

Excellent! I found all the key implementation details. Now let me design the implementation plan.

The user hasn't asked me anything yet - I'm still in plan mode. I've received excellent exploration and design agent results. Let me review what I have:

Bill scan and Gemini integration - Fully explored. I know:

Images sent at FULL RESOLUTION to Gemini (no resizing)
OcrViewModel.kt has three integration points
GeminiBillingService.kt receives Bitmap objects
Current: JPEG 90% compression but full bitmap sent
Settings and PDF preview - Fully explored. I know:

PDF preview button in SettingsScreen.kt (lines 279-289)
Wiring in AppNavigation.kt (lines 687-726, 768-777)
PdfPreviewDialog.kt component
Just need to remove the button and wiring
Existing image utilities - Fully explored:

No centralized utility classes
Some enhancement in BillingViewModel.kt for handwriting
Image compression at 90% JPEG
Implementation plan - The Plan agent has provided a comprehensive plan covering:

OpenCV integration (Android SDK 4.9.0)
Preprocessing pipeline (grayscale, adaptive threshold, CLAHE, denoising, sharpening)
Image resizing to 1024px max dimension (88.5% cost reduction)
Pipeline order: ML Kit OCR → Preprocess → Resize → Send to Gemini
PDF preview removal
Step-by-step implementation
Testing strategy
Risks and mitigations
Now I need to:

Review the plan from the agent
Potentially read some critical files to verify understanding
Ask the user any clarifying questions if needed
Write my final plan to the plan file
Call ExitPlanMode
Let me think about what questions I should ask the user:

The user's original request was quite specific - they wanted OpenCV for image cleaning and improved bill scan accuracy, plus resolution optimization. Looking at their request, they seem clear on the direction. I should ask about a few implementation details: whether the ~10MB OpenCV APK size increase is acceptable, if 1024px max dimension works for their use case or if they'd prefer different options like 768px or 1536px, whether preprocessing should always be active or user-configurable, and how they want to roll this out.

Perfect! I have comprehensive exploration results. Let me ask you a couple of clarifying questions before finalizing the implementation plan.

Input 
{
  "questions": [
    {
      "question": "OpenCV Android SDK will add approximately 10MB to your APK size. Is this acceptable for the accuracy improvements?",
      "header": "APK Size",
      "options": [
        {
          "label": "Yes, 10MB is acceptable",
          "description": "Accuracy improvement is worth the size increase. Most users have sufficient storage."
        },
        {
          "label": "Consider lightweight alternative",
          "description": "Prefer minimal size impact. Use only basic Android Canvas/ColorMatrix preprocessing without OpenCV."
        }
      ],
      "multiSelect": false
    },
    {
      "question": "What target resolution should we resize images to before sending to Gemini API?",
      "header": "Resolution",
      "options": [
        {
          "label": "1024px (Recommended)",
          "description": "Best cost/quality balance. ~5,000 tokens per image (88% cost reduction). Excellent quality for bill scanning."
        },
        {
          "label": "1536px - Higher quality",
          "description": "~9,000 tokens per image (80% cost reduction). Better for very detailed or large bills but higher cost."
        },
        {
          "label": "768px - More aggressive",
          "description": "~2,800 tokens per image (93% cost reduction). May lose some small text details."
        }
      ],
      "multiSelect": false
    }
  ]
}
Output

User has answered your questions: "OpenCV Android SDK will add approximately 10MB to your APK size. Is this acceptable for the accuracy improvements?"="1", "What target resolution should we resize images to before sending to Gemini API?"="2". You can now continue with the user's answers in mind.

Analyzing your answers...

Q: OpenCV Android SDK will add approximately 10MB to your APK size. Is this acceptable for the accuracy improvements?
A: 1
Q: What target resolution should we resize images to before sending to Gemini API?
A: 2
