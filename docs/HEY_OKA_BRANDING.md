# HEY OKA — Branding and implementation notes

HEY OKA is a personalized Android assistant built on the open-source Mika project.

## Identity
- Product name: HEY OKA
- Assistant name: Oka
- Intended spoken wake phrase: “Hey Oka”
- Original upstream: https://github.com/wangzan101/Mika
- License: Apache-2.0; preserve upstream copyright and notices.

## Current implementation status
- Android launcher/app label changed to HEY OKA.
- Repository overview updated to identify this as a personalized Mika fork.
- Accessibility service description updated to HEY OKA.
- Existing package ID and Kotlin namespaces are intentionally unchanged in this first pass to avoid breaking app upgrades, stored data, deep links, providers, and integrations.

## Voice roadmap
The upstream app currently supports user-initiated hold-to-talk speech input. That is not equivalent to an always-on hotword detector. “Hey Oka” must not be advertised as working until a dedicated wake-word implementation is added, with explicit microphone consent, visible listening state, battery/privacy controls, and device testing.

## Visual roadmap
Replace the launcher artwork and app-specific visual identity with the user's approved robotic mosaic avatar. Keep accessibility labels and contrast usable; animation should be optional and respect Android battery/reduced-motion settings.

## Build validation
Run the upstream Gradle test/build workflow after each code change. Do not publish an APK until the Android build and device smoke tests pass.
