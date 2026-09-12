# DocVoice

[Português](README.md) · **English**

An Android app that **reads PDF documents out loud** and remembers where you
stopped.

No account. No ads. No Internet: the app **does not even hold permission to
reach the network**, so no document ever leaves the phone. That is written into
the app's own manifest and anyone can check it.

The text of documents you have opened is kept **on this phone** so the PDF
does not have to be opened again every time. It never leaves the device, never
goes into a backup, and you can erase it whenever you like — by removing the
document from the list, or all at once under **Settings → Erase everything
stored**.

Free as in freedom and free of charge, and it will stay that way — see
[Licence](#licence).

### ⬇ Download

**[DocVoice.apk](../../releases/latest/download/DocVoice.apk)** — open this file
on your phone. The link always points at the newest release.

Installing takes five steps, [further down](#installing-on-your-phone).
Needs Android 8.0 or newer.

### 🔎 Here to help test?

Read **[COMO_TESTAR.md](COMO_TESTAR.md)** (in Portuguese): eleven plain
questions and a place to write down what went wrong.

---

## Who it is for

People who have to get through long documents and would rather listen:
contracts, court decisions, manuals, reports. People who find long text hard to
read. Anyone who wants to hear a document on the way to work, phone in pocket,
screen off.

## What it does

- Opens a PDF that has **real text** in it and shows it in large type on a dark
  background.
- Reads it aloud, paragraph by paragraph, using the phone's own voice. It
  prefers European Portuguese; if that is missing it uses what is there and
  says so.
- Highlights the paragraph being read and follows the text on its own.
- **Remembers where you were** — after every paragraph, not only when you press
  pause. Close the app, come back tomorrow, carry on from the same place.
- Keeps reading with the **screen off** and the app closed, with a notification
  bar carrying back, pause and forward.
- Goes quiet when a call comes in.
- Speed from 0.75× to 2×.
- Detects whether the document is in Portuguese, Russian or English and picks
  the voice accordingly.
- Marks each new page discreetly: `fl. 1`, `fl. 2`.

## What it does **not** do yet

This app is unfinished, and it is only fair to say so:

- **Scanned documents are not read.** If the PDF is a photograph of pages, the
  app warns you that there is no text and stops there. Image recognition (OCR)
  is not built yet.
- **The headset button does nothing**, and neither do lock-screen controls.
- It has not been used on many different phones. Some manufacturers shut down
  background apps in their own way, and that is not yet mapped.

## Installing on your phone

1. Tap **[DocVoice.apk](../../releases/latest/download/DocVoice.apk)** to download. (Every version is under [Releases](../../releases).)
2. Open it on the phone (the **Files** or **Downloads** app).
3. The phone will warn you the app does not come from the Play Store. That is
   expected: tap **Settings**, turn on **Allow from this source**, go back and
   tap **Install**.
4. If Play Protect warns you, choose **Install anyway**.
5. The first time you press play, the phone asks whether the app may send
   notifications. Answer **Allow** — that is the bar with the controls.

No other permission is requested. If the phone asks for anything else, that is
a problem: [open an issue](../../issues).

**Requires:** Android 8.0 or newer.

## What the app asks for, and why

| Permission | What for |
|---|---|
| Foreground service | keep reading after you leave the app |
| Keep the processor awake | so the voice does not break up with the screen off |
| Notifications | the bar with back, pause and forward |

It does not ask for Internet, contacts, location, or storage. The document is
picked through the system's own file chooser, and only that one is read.

## Building from source

You need a computer with Java 17 and the Android SDK.

```sh
git clone <this repository>
cd DocVoice
./gradlew testDebugUnitTest    # the tests, no phone needed
./gradlew assembleDebug        # the .apk
```

The file lands in `app/build/outputs/apk/debug/`.

Every change pushed here is built and tested automatically; every version tag
publishes an `.apk` to [Releases](../../releases).

## How it is built

Kotlin and Jetpack Compose. Text is extracted with
[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android); the voice is
Android's own. Position and the recent-documents list live in DataStore —
three fields, no database.

Text is cut into blocks of 300 to 350 characters, and **only at punctuation**:
a long block beats a thought cut in half. Abbreviations such as `art.º`,
`fls.`, `Cf.` and numbers such as `15.000` do not end a sentence.

## Helping

The most useful thing by far: **install it, use it with a real document, and
say what went wrong**. Phone make and model, what happened, and a photo of the
screen if anything looks odd. [Open an issue](../../issues).

You do not need to know how to program to help.

## Licence

[GNU General Public License v3.0](LICENSE).

In plain words: anyone may use, study, change and pass on this app. Anyone
distributing a changed version must hand over the source too, under the same
licence. Nobody may take this, close it up and sell it as their own.

That is deliberate: this app was made to stay free.
