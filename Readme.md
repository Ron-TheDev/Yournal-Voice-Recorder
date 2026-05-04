# Yournal

Yournal is a private, on-device voice recording and note-taking application for Android.

## Overview
Capture your thoughts instantly with real-time transcription. Yournal processes all audio locally on your device using the Vosk STT engine, ensuring your data never leaves your phone. It features a custom high-performance audio engine and a reactive data layer for a smooth user experience.

## Key Technical Features
- 🎙️ **Custom Audio Engine**: Dual-thread PCM capture and AAC encoding for high-quality, low-latency recording.
- 📝 **On-Device STT**: Powered by Vosk, providing real-time partial results and finalized transcriptions without internet access.
- 📂 **Room Persistence**: Reactive data flow using Room and RxJava3, including serialized amplitude data for instant waveform rendering.
- 🔒 **Privacy First**: No cloud processing. All audio, transcripts, and metadata are stored securely on your device.
- 🎨 **Modern UI**: Built with Navigation Components, ViewBinding, and Material Design 3.

## Quick Start
1.  Open Project in Android Studio (Ladybug+).
2.  Build and Run on your Android device (min SDK 26).
3.  Start recording and watch your transcript appear live!

For a deep dive into the architecture, engine logic, and data schema, see [documentation.md](documentation.md).

---
*Note: This project and its documentation are AI-generated.*
