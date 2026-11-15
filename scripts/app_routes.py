"""기사 앱 지도용 실제 도로 경로 만들기.

앱 묶음(assets/day_*.json)의 노드 8곳 사이 모든 방향 쌍에 대해 OSRM(router.project-osrm.org, OpenStreetMap 도로)
경로선과 갈림길 안내를 받아 android/app/src/main/assets/routes.json 으로 저장한다.
지도 그림·안내 문구용일 뿐이고, 배차 계산의 주행시간은 그대로 묶음의 travel 표(시간대별)를 쓴다.

실행: python scripts/app_routes.py   (인터넷 필요, 56쌍, 1초 간격)
"""
import glob, json, os, sys, time, urllib.request

ROOT = os.environ.get("H2_ROOT", os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
ASSETS = os.path.join(ROOT, "android", "app", "src", "main", "assets")
OSRM = "https://router.project-osrm.org/route/v1/driving/"

MOD = {"left": "좌회전", "right": "우회전", "slight left": "왼쪽 방향", "slight right": "오른쪽 방향",
       "sharp left": "급좌회전", "sharp right": "급우회전", "straight": "직진", "uturn": "유턴"}


def say(st):
    m = st["maneuver"]; ty = m.get("type", ""); mod = m.get("modifier", "")
    road = st.get("ref") or st.get("name") or ""
    dest = (st.get("destinations") or "").split(":")[-1].strip()
    where = dest or road
    if ty == "depart": return f"{road or '도로'}로 출발"
    if ty == "arrive": return "목적지 도착"
    if ty in ("off ramp",): return f"{MOD.get(mod, '')} 나들목 · {where}".strip(" ·")
    if ty in ("on ramp", "merge"): return f"{road or where} 진입"
    if ty == "fork": return f"{MOD.get(mod, '')} 갈림길 · {where}".strip(" ·")
    if ty in ("roundabout", "rotary"): return f"회전교차로 · {where}".strip(" ·")
    if ty == "new name": return None          # 길 이름만 바뀜: 안내 안 함
    if ty == "continue" and mod in ("straight", ""): return None
    return f"{MOD.get(mod, '진행')} · {where}".strip(" ·")


def main():
    b = json.load(open(sorted(glob.glob(os.path.join(ASSETS, "day_*.json")))[-1], encoding="utf-8"))
    nodes = b["nodes"]
    out = {"source": "OSRM demo server, © OpenStreetMap contributors (ODbL)", "routes": {}}
    for a in nodes:
        for c in nodes:
            if a["id"] == c["id"]: continue
            url = f"{OSRM}{a['lon']},{a['lat']};{c['lon']},{c['lat']}?overview=full&geometries=polyline6&steps=true"
            req = urllib.request.Request(url, headers={"User-Agent": "h2-corridor-app-routes/1.0"})
            r = json.load(urllib.request.urlopen(req, timeout=30))["routes"][0]
            steps, d = [], 0.0
            for st in r["legs"][0]["steps"]:
                t = say(st)
                if t: steps.append({"at_m": round(d), "text": t,
                                    "lat": round(st["maneuver"]["location"][1], 6), "lon": round(st["maneuver"]["location"][0], 6)})
                d += st["distance"]
            out["routes"][f"{a['id']}>{c['id']}"] = {"km": round(r["distance"] / 1000, 2), "min": round(r["duration"] / 60, 1),
                                                    "line": r["geometry"], "steps": steps}
            print(a["id"], "->", c["id"], out["routes"][f"{a['id']}>{c['id']}"]["km"], "km", len(steps), "안내", file=sys.stderr)
            time.sleep(1.0)
    p = os.path.join(ASSETS, "routes.json")
    json.dump(out, open(p, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(p, os.path.getsize(p) // 1024, "KB")


if __name__ == "__main__":
    main()
