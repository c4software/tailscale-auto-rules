# Privacy Policy — Tailscale Auto Rules

Last updated: 12 August 2026

## Summary

Tailscale Auto Rules collects nothing, sends nothing and stores nothing outside
your device. It has no server, no account, no analytics and no advertising.

## Data the app handles

All of the following stays on your phone, in the app's private storage, and is
deleted when the app is uninstalled:

| Data | Why | Where it goes |
|---|---|---|
| Names (SSID) of the Wi-Fi networks you add or that the app learns | To recognise a network and apply the behaviour you chose for it | Local database, on the device only |
| The last 500 tunnel state changes (date, previous state, new state, rule) | To let you check why the tunnel changed | Local database, on the device only |
| Your settings (automation on/off, gesture learning, start on boot, verbose logging) | To remember your choices | Local preferences, on the device only |

The app never collects your name, your email address, your contacts, your
files, your position, your browsing or any device identifier.

## Location permission

Android classifies the name of the connected Wi-Fi network (SSID) as location
data, and grants access to it only to apps holding a location permission. This
is the only reason the app requests `ACCESS_FINE_LOCATION` /
`ACCESS_COARSE_LOCATION`.

The app reads the network name and nothing else. It does not read GPS
coordinates, does not track movement, and never transmits anything anywhere.
The permission can be refused: the app keeps working, but it can no longer
recognise your Wi-Fi networks by name.

## Foreground service

The app runs a foreground service in order to observe network changes
continuously. Android requires such a service to display a permanent
notification, which shows the tunnel state and the rule that decided it. The
service performs no network communication of its own.

## Interaction with the Tailscale client

The app sends a local Android broadcast to the official Tailscale client
(`com.tailscale.ipn`) asking it to connect or disconnect the tunnel. No data is
exchanged beyond that request, and the app has no access to your Tailscale
account, your devices or your traffic.

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Any change to this policy will be published in the application's public
repository, together with the version it applies to.

## Contact

Questions or requests: open an issue at
https://github.com/c4software/tailscale-auto-rules/issues
