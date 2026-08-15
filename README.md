# relaix-agent

The Android agent for [Relaix](https://github.com/KGMA74/relaix) — a self-hosted SMS gateway
whose sending nodes are ordinary Android phones. This app is what turns a phone into one of
those nodes.

## Status

**Scaffold only — this app sends nothing yet.**

What is here today is the stock Compose project: one activity saying hello. No enrollment, no
gRPC, no SMS. The control plane it will talk to is already functional end to end
([relaix-server](https://github.com/KGMA74/relaix-server)), which is precisely why the agent
is now the work in progress — there is finally a real server to test each piece against.

Implementation lands one atomic commit at a time; the git history is the record of what
exists so far.

```sh
./gradlew assembleDebug
```

## What this will do

A phone sits behind carrier NAT and can never be reached from the internet, so the agent
dials out and holds a gRPC bidirectional stream open for its entire uptime. The server pushes
jobs down the stream it already has:

1. **Enroll once.** The operator mints an enrollment token
   (`POST /admin/devices/enroll-token`) and shows the QR; the agent scans it, gets the server
   endpoint and the token from it, calls the unary `Enroll` RPC, and stores the long-lived
   `device_token` it gets back.
2. **Stay connected.** A foreground service holds the `Connect` stream open, re-registers
   after every drop with exponential backoff, and heartbeats on the interval the server
   dictates in `RegisterAck`.
3. **Report health.** Battery, charging state, signal as a normalized 0–4 level, SIM
   readiness, granted permissions. The server's scheduler decides from this whether the phone
   is fit to receive work.
4. **Send.** `SmsManager` sends the message, `JobAck` says the job was taken, and the
   platform's sent/delivered receipts come back as `JobResult` — including how many SMS parts
   were actually billed.

Every job is deduplicated by `job_id`: delivery to the device is at-least-once, so an agent
that has already handled an id **must not send twice**.

Design and rationale live in the monorepo:
[architecture](https://github.com/KGMA74/relaix/blob/main/docs/architecture.md) ·
[protocol](https://github.com/KGMA74/relaix/blob/main/docs/protocol.md).

## Development

Requires JDK 17+ and the Android SDK (compileSdk 36, minSdk 26). Gradle wrapper included.

```sh
./gradlew assembleDebug   # build the debug APK
./gradlew test            # unit tests
./gradlew lint            # Android lint
```

## Running against a real server

The point of building the agent now is that every commit can be checked against a running
control plane rather than a mock. From the monorepo:

```sh
docker compose up --build
```

That brings up Postgres and `gatewayd` — REST on `:8080`, gRPC on `:9090`, plaintext (the dev
stack configures no TLS certificate). Both ports are published on the host machine.

### Making the server reachable from the handset

`localhost` means the phone itself, so the endpoint advertised in the QR has to be an address
the handset can really dial. Which one depends on how the device is attached:

| Setup | Endpoint to advertise | Extra steps |
| --- | --- | --- |
| **Physical phone over USB** (what this project develops against) | `grpc://127.0.0.1:9090` | `adb reverse tcp:9090 tcp:9090` and `adb reverse tcp:8080 tcp:8080` — the phone's own localhost is then tunnelled to the PC over the cable. |
| Physical phone over Wi-Fi | `grpc://<PC LAN IP>:9090` | Same network, and a Windows Firewall inbound rule for TCP 8080/9090 — it blocks them by default. |
| Emulator (AVD) | `grpc://10.0.2.2:9090` | None. `10.0.2.2` is the emulator's alias for the host machine. Cannot send SMS — see below. |

```sh
# phone over USB — the default here
adb devices                                        # phone must show as "device", not "unauthorized"
adb reverse tcp:9090 tcp:9090
adb reverse tcp:8080 tcp:8080
RELAIX_PUBLIC_URL=grpc://127.0.0.1:9090 docker compose up --build   # from the monorepo
```

`adb reverse` is dropped when the cable is unplugged or `adb` restarts — re-run the two lines
after replugging. It is per-device: with several handsets attached, use `adb -s <serial>`.

**VS Code's port forwarding does not help here.** It forwards a port from a *remote* machine
(dev container, Codespace, SSH host) to your laptop, or publishes it as a public tunnel. The
server is already listening on this machine; what is missing is the opposite direction — the
phone reaching the PC — and that is exactly `adb reverse`, which also goes over the USB cable
and needs no firewall change at all.

### Enrolling

```sh
curl -X POST -H "Authorization: Bearer dev-api-key" \
     "localhost:8080/admin/devices/enroll-token?format=png" --output qr.png
```

Open `qr.png` on screen and scan it from the app. The QR encodes
`{"endpoint":"grpc://…","token":"…"}` — endpoint and token together, so the phone needs no
manual configuration. `grpc://` means cleartext, `grpcs://` means TLS.

### Test device

Development targets a **physical phone on Android 16 (API 36) over USB**. On the phone:
Developer options on, USB debugging on, and the RSA prompt accepted so `adb devices` reports
`device` rather than `unauthorized`.

An emulator is fine for enrollment, the `Connect` stream, reconnection, heartbeats and health,
but it has no radio and no SIM: **it cannot send a real SMS**. Everything downstream of
`SmsManager` — sent and delivered receipts, `parts_sent`, carrier error codes — needs the real
handset with an active SIM and credit.

### What Android 16 imposes

Recent releases changed the rules an always-connected agent lives under, and these are design
constraints rather than details to discover late:

- **Foreground service type.** `dataSync` is capped at 6 hours per 24 on Android 15+, after
  which the system stops the service — fatal for a service whose whole job is to stay
  connected. The stream service therefore declares `specialUse`, which has no such timeout.
  The Play Store asks for a justification for `specialUse`; this app is self-hosted and
  sideloaded, so that review does not apply.
- **`POST_NOTIFICATIONS`** is a runtime permission since API 33. Denied, the foreground
  notification is silent but the service still runs — the app must ask, and keep working
  either way.
- **Battery optimization.** Doze will not kill a foreground service, but aggressive OEM
  battery managers will. The status screen surfaces whether the app is exempt and offers to
  request it.
- **`SEND_SMS`** stays an ordinary runtime permission, granted by the user at first use.
  Play's restrictions on SMS permissions do not apply to a sideloaded build.
- **Cleartext.** gRPC over OkHttp with plaintext does not go through the platform's cleartext
  policy, so `usesCleartextTraffic` is not what makes the dev stack reachable. The debug
  manifest sets it anyway, for any HTTP tooling that does honour it.

## Generated code

`gen/` holds the Kotlin and Java generated from `proto/smsgateway/v1/device.proto`, which
lives in the [relaix monorepo](https://github.com/KGMA74/relaix) — the single source of truth
for the contract, shared with the Go server. Generation runs through four Buf Schema Registry
remote plugins (`protocolbuffers/java`, `protocolbuffers/kotlin`, `grpc/java`, `grpc/kotlin`),
which means regenerating needs no local `protoc` or plugin install — just `buf`.

That also means the runtime is **full `protobuf-java`/`grpc-java`, not the *lite* variants**:
no `javalite` plugin is published on the BSR, and installing one locally would have meant
maintaining a `protoc` toolchain just for this one case, defeating the point of using remote
plugins. The APK-size cost of the full runtime is accepted; see `docs/backlog.md` for detail.

The generated code is **committed here on purpose**, so building the app needs nothing but
the Android toolchain. To regenerate after a change to the proto, run `buf generate` from the
monorepo root — its `buf.gen.yaml` writes into this repository — then commit the result here
alongside the proto change and bump the submodule pointer in the monorepo.

## Author

Built by **[KGMA74](https://github.com/KGMA74)** — [ryukfearless.digital](https://ryukfearless.digital).

## License

[Apache License 2.0](LICENSE), same as the rest of Relaix.
