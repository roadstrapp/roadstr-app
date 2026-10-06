#!/usr/bin/env python3
"""Fake GPS for testing navigation on a phone, through the shell's location test-provider commands.

FOR A TEST PHONE, OR FOR A DAILY PHONE ONLY FOR A FEW MINUTES: while the test provider exists every app on
the phone believes it is where this script says. Always finish with `off`, which removes the provider and
puts the temporary shell permission back.

  simulate_drive.py on LAT LNG                         enable the test provider and put the phone there
  simulate_drive.py hold LAT LNG SECONDS               repeat the same position every second
  simulate_drive.py drive ROUTE.json SPEED_M_S [PARK_SECONDS]
                                                       drive along an OSRM route (the JSON of
                                                       /route/v1/driving/...?overview=full&geometries=geojson),
                                                       one fix a second, then stay at its end
  simulate_drive.py off                                remove the test provider and the temporary permission

Only a position is sent (the command takes no speed or bearing), so the app shows 0 km/h; route progress,
voice cues, reroutes and arrival all follow the position. Needs `adb` and an authorised phone.
"""
import json, math, subprocess, sys, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True)

def put(lat, lng, accuracy=5):
    r = sh("cmd", "location", "providers", "set-test-provider-location", "gps",
           "--location", f"{lat:.6f},{lng:.6f}", "--accuracy", str(accuracy))
    return r.returncode == 0

def on(lat, lng):
    print(sh("appops", "set", "2000", "android:mock_location", "allow").stdout.strip() or "mock_location allowed for the shell")
    r = sh("cmd", "location", "providers", "add-test-provider", "gps", "--supportsAltitude", "--supportsSpeed", "--supportsBearing")
    print("add-test-provider:", (r.stdout + r.stderr).strip() or "ok")
    sh("cmd", "location", "providers", "set-test-provider-enabled", "gps", "true")
    print("first fix sent:", put(float(lat), float(lng)))

def off():
    sh("cmd", "location", "providers", "remove-test-provider", "gps")
    sh("appops", "set", "2000", "android:mock_location", "default")
    print("test provider removed; shell appop:", sh("appops", "get", "2000", "android:mock_location").stdout.strip())

def hold(lat, lng, seconds):
    end = time.time() + seconds
    while time.time() < end:
        put(lat, lng); time.sleep(1.0)

def haversine(a, b):
    r = 6371000.0
    p1, p2 = math.radians(a[1]), math.radians(b[1])
    dp, dl = p2 - p1, math.radians(b[0] - a[0])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))

def drive(path, speed, park):
    coords = json.load(open(path))["routes"][0]["geometry"]["coordinates"]   # [lon, lat]
    step, carry, last = speed * 1.0, 0.0, coords[0]
    put(last[1], last[0]); sent = 1
    for nxt in coords[1:]:
        seg = haversine(last, nxt)
        while carry + seg >= step and seg > 0:
            t = (step - carry) / seg
            last = [last[0] + (nxt[0] - last[0]) * t, last[1] + (nxt[1] - last[1]) * t]
            seg = haversine(last, nxt); carry = 0.0
            put(last[1], last[0]); sent += 1; time.sleep(1.0)
        carry += seg; last = nxt
    end = coords[-1]
    print(f"drove {sent} fixes; parking at the end for {park} s", flush=True)
    hold(end[1], end[0], park)

if __name__ == "__main__":
    cmd, *a = sys.argv[1:]
    try:
        if cmd == "on": on(*a)
        elif cmd == "hold": hold(float(a[0]), float(a[1]), float(a[2]))
        elif cmd == "drive": drive(a[0], float(a[1]), float(a[2]) if len(a) > 2 else 20)
        elif cmd == "off": off()
    except KeyboardInterrupt:
        pass
