# CarDeck 🚗

**CarDeck is the Android app that reads what your car has to say.**
Plug a small OBD2 adapter into the diagnostic port, open the app, and see live RPM, speed, temperatures, boost pressure, fuel level…
along with the history of all your trips, including their fuel consumption and cost.

Free, no account, and your data stays on your phone. Available in English, French, German, Russian and Spanish.

> 🧑‍🔧 **A personal project.** CarDeck is built for my own cars and for the ones I get my hands on, mostly to explore and understand them.
> It isn't meant to become a popular app or one for everyone: there are plenty of OBD apps out there, and this one simply does what I need.
> It's shared as is, in case it's useful to someone, with no promise of support or of new vehicles.

📥 [Download the latest APK](https://github.com/ToftMalone/CarDeck/releases/latest) (Android 8.0 or newer).

## What CarDeck does

### 📊 Dashboard
Two live dashboards, switched with one tap:
- **Standard**: speed, RPM, coolant and oil temperature, fuel level, fuel consumption.
- **Sport**: intake air temperature, manifold and boost pressure (in bar), ignition advance.

### 📟 OBD2 data
Every engine value your vehicle provides, sorted by category, along with the state of the link to the adapter.
The list stays visible even when the adapter is disconnected (values show up as soon as it connects).

**Continuous reading**: as long as the ignition is on, CarDeck reads the data all the time, whether the screen is open or not,
each at its own pace.

| Pace | Data |
|---|---|
| ⚡ **Every round** | Speed, RPM, engine load, ignition advance, manifold pressure, air flow, throttle, barometric pressure |
| 🔁 **1 round in 3** | Absolute load; demanded, actual and friction torque; coolant, intake and manifold surface temperatures; relative throttle, B, C and commanded; short and long term fuel trims (banks 1 and 2); commanded air-fuel ratio; fuel and fuel rail pressures; injection timing; fuel rate; fuel per cylinder; oxygen sensors; EVAP purge |
| 🐢 **1 round in 6** | Fuel level (in % and estimated litres), ethanol content, outside temperature, odometer, engine run time, distance with the warning light on, oil temperature |

Only these values are read, and only if your car provides them. Instant fuel consumption, manifold pressure in bar, and a trip's fuel
consumption and cost are **calculated** from these measurements.

**Explorer** (**Contribution** tile on the home screen): a single button that adapts to the car when you start it, for **every car**
and **read-only** (only read services are sent: never clearing, writing, actuator control or unlocking; the airbags, addresses
7F1 / 7F9, are never queried):
- 🅿️ **stationary, full scan**: searches every module on 11-bit CAN (from 0x600 to 0x7FF, whatever their response address), on 29-bit
  CAN and on the K-line (including modules that wake at one address but expect another one in their messages, like the VW MK60
  ABS: 03 → 28); identification (part numbers, system names), KWP `21xx` manufacturer data blocks, UDS `22xxxx` identifiers in
  ranges of 10,000, memory reads, CAN bus map, fault codes before / after. If interrupted, it resumes where it stopped;
- 🚗 **while driving, recording**: OBD values, manufacturer blocks (and new blocks discovered on the way) and bus traffic, timestamped
  together. It stops when you ask or when the ignition is switched off. At the end, every byte (and pair of bytes, in both orders,
  signed or not) of the blocks and bus frames is compared with the OBD values measured at the same moment over the whole drive:
  counters are set aside, the small time lag is searched, and each lead comes with its formula (e.g. `speed ≈ x / 100`), in
  `PISTES.md` and the report. Method adapted from [CANExplorer](https://github.com/AKJ7/CANExplorer) (MIT licence).
- 💥 **knock search** (separate button, while driving; only offered on the Swift Sport ZC33S, the only car reverse-engineered for it):
  during firm full-load accelerations, ignition advance, load and engine blocks are read as fast as possible, then CarDeck ranks the
  bytes that behave like knock-induced ignition retard (idle off load, moving under load, advance lost at equal RPM and load).
  Candidates to confirm, not a measurement.
- Result: a **report** (modules, live blocks, plain-text answers, refused identifiers, **decoding leads by correlation**,
  **engine maps**) plus all the raw data, to **export** as one file.

### 🩺 Diagnosis
Reads fault codes (stored, pending, permanent) and freeze frame data, with a built-in code database **translated into the app's
5 languages** (so it works offline): nearly **1,000 generic P0xxx codes** from the SAE J2012 standard (engine, fuel, ignition, emissions,
electrical, transmission…) with their severity, the most common ones detailed with a description, possible causes and recommendations.
Code search included.

**Two levels, for every car**:
- 🩺 **Basic diagnosis**: the usual OBD codes (engine, warning light, freeze frame), in a few seconds.
- 🔬 **Full diagnosis**: every module in the car as well (ABS, airbag, body…), 1 to 2 minutes.

**Clearing faults** after a diagnosis, also in two levels: **basic** (engine OBD codes, warning light, freeze frame) or **full** (every
module that had codes as well). Cleared codes disappear from the app straight away; if a module refuses (engine running, for example),
CarDeck says which one and why, and its codes stay listed.

**Full diagnosis**: the adapter is reset first, then CarDeck reads the OBD2 codes of every module, also queries the engine module
directly, searches every module on the CAN network (engine, ABS…) and reads the fault codes of each one (UDS and KWP2000). The scan
takes 1 to 2 minutes, but leaves nothing out.

**Every car**: the scan adapts to the detected protocol. On 11-bit CAN (most cars since 2008), it runs the full diagnosis above. On
29-bit CAN, K-Line or J1850 (many older cars), it reads the OBD2 codes and freeze frame data of every module that answers. A code
reported by several modules is listed for each of them.

**✅ Ready for the emissions test?**: CarDeck reads the check engine light and the status of the emissions self-tests the car runs on
its own (catalyst, oxygen sensors, particulate filter, EGR…). After clearing the faults or disconnecting the battery, these tests start
over and the emissions test may fail: CarDeck tells you which ones are not complete, the distance driven since the codes were cleared,
and how to drive so they complete.

**Scan log**: each diagnosis records every question sent to the car and every answer; share it from the Diagnosis screen to
investigate a fault that wasn't found.

### 🗺️ Trips
Open CarDeck (or let it wake up over Bluetooth, see below): the trip starts **on its own** as soon as the engine runs.
A stalled engine doesn't end it. It ends when the ignition is switched off, when the engine is off and the car stationary for
1 minute, or when the link to the adapter is lost for 1 minute. CarDeck then goes to sleep, without any notification.
If you open the app without driving, it also goes to sleep after 5 minutes without a trip, once you leave it.

For each trip: GPS track on an OpenStreetMap map, distance, durations, speeds, **average fuel consumption**, **trip cost** (at the
day's fuel price near the destination, from prix-carburants.gouv.fr, so in France only for now; fuel type chosen per vehicle), plus
the engine data recorded along the way.
Drag the trip panel with your finger to make more room for the map or the data.
You can replay a trip, filter the history by period and **export** a full trip (summary, GPX track, CSV data).
The **history** can also be exported in one go for a spreadsheet (Excel, LibreOffice): one row per trip in the chosen period (date,
times, places, distance, speeds, fuel used, consumption, price, cost) and a total row, handy for mileage expenses.
In the history, a long press on a trip starts **multi-selection**: you can **merge** several trips into one (end to end, track and data
included) or **delete** them.

### ⏰ Automatic wake-up
For each vehicle, you choose whether CarDeck wakes up when the phone connects over Bluetooth to your car's head unit, or never
(you then open the app yourself). For the wake-up to work, Android must let CarDeck ignore battery optimisation: the app asks you
when you turn the option on.

### 💾 Backup
All your data (vehicles, trips, diagnoses, settings) can be backed up to a file or to a server on your network, then restored from
**Settings → Backup**. Each time you back up, you choose the protection:

- 🔐 **Encrypted by CarDeck**: encrypted (AES-256) with the app's key, no password, restorable on any device with CarDeck.
- 🔓 **No protection**: plain file, no password. Anyone who gets the file can read all your data, GPS tracks included.

A modified or damaged encrypted file is rejected on restore. Older backups protected by a password or tied to a device can still be
restored, but new ones can't be created. **Original codings** are no longer backed up: they are only restored from an older
device-bound backup (a plain file could have been forged by someone else). CarDeck doesn't use Android's automatic backup (Google
Drive) for your data.

#### 🗄️ SMB server (NAS, PC, router)
**Settings → Backup → SMB server**: enter the server (name or IP address), the **share**, the **folder** (`CarDeck` by default) and your
account. You can also paste the full address as is (`smb://nas/Backups/CarDeck`). The **Test the connection** button checks the login,
the share and write access. Then **Back up to the server** uploads a `CarDeck-date.cdbak` file and **Restore from the server** lists the
backups in the folder, newest first.

- 🔒 SMB 2 and 3 only (the obsolete SMB 1 isn't supported), NTLM login, **signed exchanges**: a device on the network can neither tamper
  with nor impersonate the server.
- 🔑 The server password is **encrypted with an Android Keystore key**, kept apart from the settings: it is **never written to a
  backup** and a restore never touches it.
- 📶 The phone must be able to reach the server: same Wi-Fi network, or a VPN. Don't expose your SMB server to the Internet.

### 🏠 A garage for several cars
Each car has its own profile (trips, diagnoses, OBD2 adapter). To create one, you pick your vehicle **in three lists**: the **brand**,
the **model**, then the **generation and engine**. For example Suzuki › Swift › Sport ZC33S › K14C 1.4 BoosterJet. A search field gets you
there faster ("Clio V 1.0 TCe", "Golf GTI"…).

**Don't know your car's model?** Tap **"I don't know my car's model"** at the top of the list: the OBD2 adapter reads the car's
serial number (VIN) and CarDeck works out, **offline**, the manufacturer and the year (for manufacturers that encode it in the VIN,
which Renault, PSA or Mercedes, for example, don't). It then preselects the brand and lists the models built that year first; when the
model code is known, the vehicle is recognised directly (Swift Sport) or at least its model and generation (Clio V). The VIN doesn't
reliably give the exact model, so you confirm it in the list.
The home screen tiles can be rearranged with a long press, and each launch greets you with a different message.

## Supported vehicles

- **A list of more than 2,000 engines** from 48 brands (Peugeot, Renault, Citroën, Volkswagen, Toyota, Suzuki, BMW, Audi, Porsche,
  Ferrari…) with their models, generations and petrol, diesel, hybrid and LPG engines.
- These vehicles use **standard OBD2 data** (RPM, speed, temperatures, pressures, fault diagnosis, trips and cost). The protocol is
  detected automatically on connection.
- Your vehicle isn't listed? Pick **Other vehicle**: every petrol car since 2001 and diesel car since 2004 has an OBD2 port.
- Some vehicles are **reverse-engineered** by CarDeck, with model-specific data:

| Model | Reverse-engineered |
|---|---|
| Suzuki Swift Sport ZC33S (K14C 1.4 BoosterJet, 140 hp) | ✅ Read-only (except clearing the codes you ask for), oil, fuel, odometer |
| Renault Clio V 1.0 TCe 90 hp | ✅ All 15 modules mapped (OBD on 29-bit CAN), queried by the diagnosis; engine and ABS / ESP identified |

The list is a starting point: it hasn't been tested on every car, only on the Swift Sport.

## Languages

CarDeck is available in **English, French, German, Russian and Spanish**. It follows the **phone's language** (English if that
language isn't available). On Android 13 and later, you can also set CarDeck's language on its own: Android settings › Apps › CarDeck ›
Language. Translations live in `app/src/main/assets/i18n/strings.tsv` (one French text per line, followed by each translation).

## OBD2 adapter

CarDeck works with an **ELM327** adapter over **classic Bluetooth**, **Bluetooth LE** or **Wi-Fi**. Plug it into the diagnostic port
(under the steering wheel), switch the ignition on, then pair it with your vehicle from the app.

## Respect for your car and your data

- 🔒 On every car without coding features (the Swift Sport included), CarDeck is **read-only**: it looks, it writes nothing to the car,
  apart from clearing fault codes when you ask (no coding, no writing, no unlocking).
- 📍 Trips and diagnoses stay **on your phone**. The only things that leave it: the fuel price request (with the trip's arrival
  position, rounded to the kilometre), OpenStreetMap tiles and the update check.
- 🔐 Your backups are **encrypted by CarDeck**, or plain if you want, in a file or on your own SMB server.
- 🛡️ On cars that support coding, the **original coding** is saved with the car's serial number (VIN): CarDeck refuses to write it
  back to another car, and reads each setting again right before writing it.
- 🔄 Updates are offered at launch and can be installed from **Settings → App**, where you'll also find the changelog.

## Install

Download the APK of the latest version from the [releases](https://github.com/ToftMalone/CarDeck/releases) and install it on your
Android phone (8.0 or newer).

## Support CarDeck ☕

CarDeck is free. If you find it useful, you can support its development on [Ko-fi](https://ko-fi.com/toftmalone).
