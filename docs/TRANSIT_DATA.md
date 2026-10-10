# Adding public-transport coverage

Roadstr obtains public-transport itineraries from [Transitous](https://transitous.org/), a community-run, provider-neutral
routing service built from open timetable feeds. Roadstr does not keep a separate operator allow-list: once a compatible feed
is accepted and imported by Transitous, it is available to Roadstr without a new APK.

## Add an operator or region

1. Find an openly licensed, stable timetable URL. Transitous accepts GTFS and NeTEx for static schedules; start with the
   operator, a national/regional open-data portal, the Mobility Database or Transitland. Static data should cover several
   months when possible, and the URL must remain stable because Transitous checks it daily.
2. Check the [`feeds/` directory](https://github.com/public-transport/transitous/tree/main/feeds) for the country or ISO 3166-2
   region. Update that JSON file, or add it with a maintainer if the region is new.
3. Prefer a Mobility Database id or Transitland Atlas id when the feed is catalogued. Otherwise add an `http`/`ftp` source and
   record its licence URL and SPDX identifier when known.
4. Add GTFS-RT or SIRI only when it matches the static timetable. Realtime entries use the same source name as their static
   feed; Transitous supports trip updates and service alerts, but not vehicle positions as a standalone replacement for a
   schedule.
5. Open a pull request against `public-transport/transitous`. Its CI fetches and validates the feed automatically. You can also
   run `./src/fetch.py feeds/<region>.json` locally after following Transitous' setup instructions.
6. After the upstream import succeeds, verify the operator in the Transitous web UI and in Roadstr. No Roadstr code or release
   is required unless the upstream API contract itself changes.

The authoritative schema, examples, licence fields, realtime matching rules and local test commands are in the
[Transitous “Adding a region” documentation](https://transitous.org/doc/#adding-a-region). Feed problems should be fixed at
the source or in Transitous so every open client benefits, rather than patched into Roadstr alone.
