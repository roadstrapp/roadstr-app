# Public-transport data and service policy

Roadstr obtains public-transport itineraries from [Transitous](https://transitous.org/), a community-run, provider-neutral
routing service built from open timetable feeds. Roadstr does not keep a separate operator allow-list: once a compatible feed
is accepted and imported by Transitous, it is available to Roadstr without a new APK.

The client uses the stable MOTIS v6 plan contract at `https://api.transitous.org/api/v6/plan`. The public endpoint is a
best-effort community service for light, FLOSS, non-commercial use—not infrastructure Roadstr may assume is unlimited.
Every request identifies the app/version/contact, and the itinerary panel links visibly to the data sources. Before enabling
routing for a broad production audience, maintainers must contact Transitous as required for resource-intensive routing or
many users. Staging and internal load-balancer hosts are never application endpoints. See the current
[API usage policy](https://transitous.org/api/) and [privacy policy](https://transitous.org/privacy/): journey coordinates,
time, options, IP address and User-Agent can occur in short-lived server logs, so the product must disclose remote routing.
The onboarding disclosure and every live itinerary panel now state that transit search, planning and verification send the
query, coordinates and travel time to Transitous or Entur and that the provider sees the requesting IP address.

Entur is the second production adapter for journeys wholly inside its Norway coverage. It uses Entur's GraphQL Journey
Planner with the required identified client header, bounded responses and provider-scoped IDs. It is not used as a worldwide
fallback and does not currently provide Roadstr's stop search or departure-board surface.

Offline public-transport routing is intentionally not shipped. An encrypted saved itinerary is readable without a network,
but its times are labelled as a snapshot until an explicit online verification succeeds.

For self-hosting, [MOTIS](https://github.com/motis-project/motis) can consume regional OSM plus GTFS/NeTEx and supported
realtime feeds behind the same versioned API family. Hosting a router does not grant rights to redistribute its input feeds;
licence, attribution, validity, monitoring and update obligations remain per dataset.

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
