# PAYVI — Setup & How It Works

PAYVI has two parts, both in this repo:

- `wordpress-plugin/payvi-connector/` — a WordPress/WooCommerce plugin for
  **myjda.net**. It exposes a small, authenticated REST API so the Android
  app can read new orders (with their payment screenshot) and report back
  a verification status.
- `android-app/` — the Android app (Kotlin) that pairs with the plugin,
  runs on-device OCR on payment screenshots, watches SMS from numbers you
  choose, and shows a payment status per order.

This plugin **only reads** data other snippets on this site already write
(`_mv_payment_screenshot_url`, `_mv_vendor_name`, `_mv_chosen_payment_method`,
`_jds_flag`, `_jds_match`). It doesn't touch checkout, doesn't add fields,
and can't conflict with any of the 90 existing WPCode snippets — it writes
only its own `_payvi_*` order meta keys.

## 1. Install the WordPress plugin

1. In the PAYVI app, open **Get the Plugin** → Share or Save the ZIP.
2. In WP Admin: **Plugins → Add New → Upload Plugin**, choose the ZIP,
   **Install Now**, then **Activate**.
3. Go to **WooCommerce → PAYVI Connector**.
4. Tap **Show Pairing QR Code** in WP Admin, then in the app tap
   **Connect Store → Scan QR Code**. (Manual entry of the Site URL / API
   Key / API Secret shown on that page also works if the camera isn't
   available.)
5. The app calls `/wp-json/payvi/v1/verify` immediately — if it succeeds,
   the pairing is saved; nothing is stored on the phone before that check
   passes.

**Security note:** the pairing QR/manual fields contain a secret
equivalent to a password for this store's order data. If a phone is lost,
open **WooCommerce → PAYVI Connector → Regenerate Pairing** — this
immediately invalidates the old key/secret and the phone will need to be
re-paired.

## 2. Choose which SMS numbers to watch

Open **Choose SMS Numbers** in the app, grant SMS permission, and select
one or more senders (e.g. `JazzCash`, `EasyPaisa`, your bank's SMS
shortcode, or a specific phone number). Only messages from selected
senders are ever read for matching.

## 3. Turn on background monitoring

The main screen has a **Background monitoring** switch. When on, the app:

- polls the store for new/updated orders (foreground service, default
  every 5 minutes — configurable in Settings; a WorkManager job also runs
  every ≥15 minutes as a backstop so nothing is missed if the app process
  gets killed),
- reacts immediately when a new SMS arrives from a watched sender,
- runs on-device OCR (Google ML Kit — no internet, no per-image cost) on
  each order's payment screenshot,
- compares the OCR'd name/amount/date/transaction ID/number against SMS
  from the watched senders, and
- reports the result back to the store as an order note + order meta.

## How a status is decided

For each order that has a payment screenshot:

1. **Duplicate Payment** — if the store's existing screenshot-duplicate
   detector (`_jds_flag` / `_jds_match`, SHA-256 exact-file match, already
   running on this site) flagged this order's screenshot as reused from
   another order. Checked first, regardless of any SMS.
2. Otherwise, OCR extracts Name / Amount / Date / Transaction ID / Number
   from the screenshot, and every SMS from a watched sender near the
   order's date is scored against it (also cross-checked against the
   order's own total/phone, since OCR can misread digits):
   - **Completed** — the amount matched *and* at least one of
     (transaction ID / number / name) also matched.
   - **Not Sure** — only one signal matched (e.g. amount alone).
   - **Not Received** — no SMS matched anything (including "no SMS from a
     watched sender in the expected window at all").

A human can always override the status from the order detail screen, and
tap **Re-check** to re-run the pipeline on demand.

## Known limitations (please read before relying on this for money)

- **OCR/matching is best-effort, not certified.** JazzCash/EasyPaisa/bank
  receipt formats vary, and OCR can misread a digit. The 4-outcome design
  (with "Not Sure" as a safety net) exists specifically because no
  screenshot+SMS matcher can be 100% certain — "Completed" means "strong
  match found," not "guaranteed genuine." Keep a human checking the
  "Not Sure" and "Not Received" queues.
- **This is an internal/sideloaded app, not a Play Store app.** Google
  Play policy restricts the `READ_SMS`/`RECEIVE_SMS` permissions to apps
  that are the user's default SMS handler. Since PAYVI's whole purpose is
  reading *specific senders'* payment SMS (not being a full SMS app), it's
  built to be installed directly (APK shared to your team, not published
  on the Play Store).
- **This sandbox could not run a full Android build.** Network access to
  Google's Maven repo (`dl.google.com`), which the Android Gradle Plugin
  and every AndroidX/ML Kit/ZXing library are fetched from, is blocked by
  this environment's outbound network policy. So while every file was
  written carefully and cross-checked (see "What was verified" below),
  **the first real build must happen in Android Studio** (or any machine
  with normal internet access) — open `android-app/`, let Gradle sync,
  then Build → Make Project before installing on a phone.
- A message can currently match more than one order if it happens to fit
  both (e.g. two orders for the same amount from the same customer in the
  same window) — there's no per-SMS "already used" lock yet. In practice
  the amount+txn-id/number combination makes this rare, but a human
  reviewing "Completed" orders occasionally is still worth doing.

## What was actually verified in this environment

- **WordPress plugin:** every PHP file passes `php -l` (no syntax
  errors). `Payvi_Auth::check()` — the security-critical auth logic — was
  exercised against a set of real behavioral tests (missing/invalid/valid
  credentials via Basic Auth header, `PHP_AUTH_USER/PW`, and the
  `X-PAYVI-Key/Secret` fallback headers; malformed input; `last_seen`
  bookkeeping), all passing. The QR code library vendored into the admin
  page (`assets/qrcode.min.js`, MIT-licensed, from the real `qrcode-generator`
  npm package — not reimplemented from memory) was round-trip tested:
  encode → render → decode with `jsQR`, verified byte-identical.
- **Matching engine** (`android-app/matching-engine/`, the module that
  parses OCR/SMS text and decides the status): a real, independent Kotlin
  module with no Android dependency, built and unit-tested with Gradle on
  the JVM — 18 tests covering realistic JazzCash/EasyPaisa/bank message
  formats, edge cases (garbage OCR text, date-window filtering, multiple
  candidate SMS, OCR missing a field but the order's own total still
  confirming it), all passing.
- **Everything else in the Android app** (Activities, layouts, services,
  Room, Retrofit): written using standard, well-documented Android/Jetpack
  APIs, then systematically cross-checked in this session — every
  `R.string`/`R.layout`/`R.id`/`R.color` reference used in code was
  confirmed to exist in the matching resource file (and vice versa for
  layout IDs per screen), every class named in `AndroidManifest.xml` was
  confirmed to exist with the right package, every `import
  net.myjda.payvi.*` was confirmed to resolve to a real declaration. One
  real bug was caught this way and fixed: the matching engine originally
  sent a matched transaction ID under the key `transaction_id`, while the
  plugin's status endpoint only recognized `txn_id` — now aligned. This
  is real verification, but it is not the same guarantee an actual
  compile gives; do a full Android Studio build before shipping.

## Project layout

```
wordpress-plugin/
  payvi-connector/            the plugin source
  payvi-connector.zip         built ZIP (also bundled into the app's assets)
android-app/
  matching-engine/            pure-Kotlin OCR/SMS field parsing + decision logic (unit tested)
  app/                        the Android app itself
docs/
  SETUP.md                    this file
```
