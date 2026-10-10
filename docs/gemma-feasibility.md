# Gemma On-Device Feasibility Test

## Test Environment
- Device: Realme RMX3312
- Android version: 15
- RAM: 8 GB
- Test application: Google AI Edge Gallery
- Model: Gemma-4-E2B-it
- Displayed model download size: 2.6 GB

## Results

| Test | Result |
|---|---|
| Model generates a response | PASS |
| Generates a response with internet disabled | PASS |
| Integrated into ResQ-Mesh Lite | NOT TESTED |
| Inference latency and memory usage measured | NOT MEASURED |
| SOS extraction and triage accuracy evaluated | NOT TESTED |

## Decision

Gemma-4-E2B-it is a promising candidate for optional on-device assistance.
The Edge Gallery test confirms successful generation and offline operation
on the tested phone. It does not establish performance or compatibility
inside the ResQ-Mesh Lite application.

Keep the deterministic SOS form as the primary input method and fallback.
Evaluate Gemma integration, inference latency, and extraction accuracy
before relying on it for triage.

## Test Date
2026-10-10
