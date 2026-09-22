# ProviderInstaller caller identity compatibility patch

Source baseline: GrapheneOS/platform_packages_apps_GmsCompat at
3fadfe0ec4c45a85186d623274eea83130fc1575 (GmsCompatLib manifest version 102).
The installed VoltageOS framework and library were inspected; this is an upstream
source patch, not a verified match to the complete VoltageOS source revision.

Wallet 26.33.969972112 falls back from a missing ProviderInstaller Dynamite module
to a GMS package context. With Play services 26.29.32 that context is subsequently
used for Phenotype configuration registration from Wallet's UID. The service
broker rejects the GMS package / Wallet UID pair.

This patch adds a client connection wrapper for the explicit GMS Phenotype START
action. It only rewrites transaction 46, service ID 51, and a calling-package field
that says com.google.android.gms, using the host context captured at library init.
It excludes GMS itself, contexts with a different UID, and cross-user bindings.
The remote broker still validates the real Binder caller; no UID or attestation
result is forged and no permission check is removed.

The Parcel rewrite preserves callbacks, unknown fields, trailing data, flags,
reply handling, and the caller's input position. Unknown/malformed requests pass
through. Resolved intents lacking the explicit package/action are intentionally
not handled in this first patch; live binding coverage must be checked on rollout.

## Validation

Run with a connected, explicitly selected device:

```
python3 lib/tests/providerinstaller/run-device-tests.py SERIAL /opt/android-sdk
```

Requirements: Java 17+, Android SDK platform 36 and build-tools 37.0.0, adb.
Tests compile against SDK signatures plus a compile-only hidden API stub. The
stub is excluded from the dex; app_process uses the device's real BinderWrapper,
Parcel and Binder. Tests only write a standalone dex to /data/local/tmp.

On a Xiaomi apollo running a modified VoltageOS Android 17 build, the suite passed 56 assertions, including
shorter/longer replacement strings, Binder references, unknown/trailing fields,
wrong services, already-correct identities, duplicate/malformed fields, input
position preservation, connection lifecycle forwarding and the real wrapper's
transaction/reply path. This is local Binder testing, not cross-process Binder
or Wallet end-to-end verification.

## Build and rollout boundary

In the matching ROM platform checkout, apply this patch under packages/apps/GmsCompat
and build `m GmsCompatLib`. The module needs platform APIs and the configured
`gmscompat_lib` certificate. The full Soong build was not available in this checkout.
Do not assume an arbitrary locally signed APK can replace the trusted library.

This was not reproduced on an unmodified GrapheneOS installation. Enabled app
hooks and the downstream ROM remain possible contributors to the observed issue.
The patch is a proposed compatibility workaround, not a proven upstream regression fix.

The next device test is a properly built/signed library, followed by Wallet
ProviderInstaller startup, confirming the Phenotype binding passes through this
wrapper and the package-identity error disappears. Wallet payment eligibility and
attestation are separate tests. No production library, app settings, modules or
Wallet data have been changed by preparing this patch.
