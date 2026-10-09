import json, polyline, sys
from valhalla import Actor
S = sys.argv[1]
full = Actor(f"{S}/out/vcar/config.json")
PAIRS = [
 ("Milano-Torino", (45.4642, 9.1900), (45.0620, 7.6780)), ("Firenze-Perugia", (43.7764, 11.2481), (43.1107, 12.3908)),
 ("Roma-Fiumicino", (41.8902, 12.4922), (41.8003, 12.2388)), ("Napoli-Pompei", (40.8531, 14.2723), (40.7500, 14.4890)),
 ("Trieste-Udine", (45.6495, 13.7768), (46.0620, 13.2370)), ("Genova-Savona", (44.4073, 8.9477), (44.3090, 8.4810)),
 ("Verona-Trento", (45.4390, 10.9944), (46.0679, 11.1211)), ("Cagliari-Sassari", (39.2238, 9.1217), (40.7259, 8.5556)),
 ("Palermo-Catania", (38.1157, 13.3615), (37.5079, 15.0830)), ("Bari-Lecce", (41.1171, 16.8719), (40.3515, 18.1750)),
 ("Aosta-Courmayeur", (45.7370, 7.3200), (45.7970, 6.9690)), ("Torino-urbano", (45.0300, 7.6650), (45.0710, 7.6660)),
 ("Roma-urbano", (41.8992, 12.4731), (41.9009, 12.5017)), ("Milano-Roma", (45.4642, 9.1900), (41.8902, 12.4922)),
 ("Venezia-Padova", (45.4903, 12.2420), (45.4064, 11.8768)), ("Ancona-LAquila", (43.6158, 13.5189), (42.3498, 13.3995)),
]
def snap(p):
    r = json.loads(full.locate(json.dumps({"locations": [{"lat": p[0], "lon": p[1]}], "costing": "auto", "verbose": True})))
    e = r[0]["edges"][0]; return [e["correlated_lat"], e["correlated_lon"]]
out = {"pairs": [{"name": n, "a": snap(a), "b": snap(b)} for n, a, b in PAIRS]}
# corridor reroutes along Firenze-Perugia
fp = out["pairs"][1]
r = json.loads(full.route(json.dumps({"locations": [{"lat": fp["a"][0], "lon": fp["a"][1]}, {"lat": fp["b"][0], "lon": fp["b"][1]}], "costing": "auto"})))
pts = polyline.decode(r["trip"]["legs"][0]["shape"], 6)
rer = []
for f in (0.25, 0.5, 0.75):
    la, lo = pts[int(len(pts) * f)]
    rer.append({"name": f"reroute-{int(f*100)}-on", "a": [la, lo], "b": fp["b"]})
    rer.append({"name": f"reroute-{int(f*100)}-off3km", "a": [la + 0.027, lo], "b": fp["b"]})
out["reroutes"] = rer
json.dump(out, open("app/src/main/assets/pairs.json", "w"), indent=1)
print(len(out["pairs"]), len(out["reroutes"]))
