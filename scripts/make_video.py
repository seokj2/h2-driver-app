import sys, os, subprocess
from PIL import Image, ImageDraw, ImageFont
src, out, font_dir = sys.argv[1], sys.argv[2], sys.argv[3]
W, H = 1920, 1080
f_cap = ImageFont.truetype(os.path.join(font_dir, "kr700.ttf"), 40)
f_big = ImageFont.truetype(os.path.join(font_dir, "kr700.ttf"), 76)
f_mid = ImageFont.truetype(os.path.join(font_dir, "kr500.ttf"), 38)
tmp = os.path.join(os.path.dirname(out), "frames"); os.makedirs(tmp, exist_ok=True)
rows = [l.split("|", 2) for l in open(os.path.join(src, "list.txt"), encoding="utf-8").read().splitlines() if l]
seq = []
def card(name, lines, sec):
    im = Image.new("RGB", (W, H), (247, 247, 247)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((160, 330, 260, 430), 22, fill=(22, 163, 127)); d.text((210, 380), "H2", font=f_mid, fill="white", anchor="mm")
    y = 470
    for i, (t, f, c) in enumerate(lines):
        d.text((160, y), t, font=f, fill=c); y += (100 if f is f_big else 60)
    p = os.path.join(tmp, name); im.save(p); seq.append((p, sec))
card("a_intro.png", [("H2 회랑 기사", f_big, (5, 5, 5)), ("현대 엑시언트 기사를 위한 차량 내 배차·충전 도우미 · 플레오스 Connect", f_mid, (60, 60, 60)),
                     ("시연: 울산–부산신항 컨테이너 운송, 엑시언트 수소전기 트랙터 \"수소 5호\"의 하루(2026-07-16)", f_mid, (107, 107, 107))], 4.0)
# 운전 중 장면: 차량에서는 순정 내비가 앞에 오고 우리 앱은 팝업만 띄운다.
# 바탕은 플레오스 Connect 에뮬레이터(v2.1.0)에서 찍은 순정 내비 화면(scripts/pleos_navi.png, 다른 앱 알림과 시계만 지움)이고,
# 그 위에 앱이 그린 팝업(흰 카드)을 오려 얹고, 상태 표시줄 시계를 시연 시각으로 적는다.
navi_bg = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "pleos_navi.png")).convert("RGB")
f_clk = ImageFont.truetype(os.path.join(font_dir, "kr500.ttf"), 30)
dpath = os.path.join(src, "drive.txt")
drives = dict(l.split("|", 1) for l in open(dpath, encoding="utf-8").read().splitlines() if l) if os.path.exists(dpath) else {}
def on_navi(im, hhmm):
    # 팝업은 왼쪽 아래 흰 카드(가로 520dp = 168~948px 고정). 왼쪽 안쪽 여백 칸(x=185)을 따라 위·아래 끝을 찾는다
    px = im.load(); WH = (255, 255, 255); box = None
    if px[185, 1000] == WH and px[560, 1000] == WH:
        t_ = 1000; b_ = 1000
        while t_ > 0 and px[185, t_ - 1] == WH: t_ -= 1
        while b_ < H - 1 and px[560, b_ + 1] == WH: b_ += 1
        box = (168, t_ - 2, 948, b_ + 1)
    bg = navi_bg.copy()
    if box:
        card = im.crop(box)
        w, h = card.size; x0 = 641 + (1279 - w) // 2; y0 = 96
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0)); ImageDraw.Draw(sh).rounded_rectangle((x0 - 4, y0 + 2, x0 + w + 4, y0 + h + 10), 22, fill=(0, 0, 0, 60))
        bg = Image.alpha_composite(bg.convert("RGBA"), sh).convert("RGB")
        m = Image.new("L", card.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), 18, fill=255)
        bg.paste(card, (x0, y0), m)
    ImageDraw.Draw(bg).text((1905, 33), hhmm, font=f_clk, fill=(20, 20, 20), anchor="rm")
    return bg
for fn, sec, cap in rows:
    im = Image.open(os.path.join(src, fn)).convert("RGB")
    if im.size != (W, H): im = im.resize((W, H), Image.LANCZOS)
    if fn in drives: im = on_navi(im, drives[fn])
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    tw = d.textlength(cap, font=f_cap)
    x0 = (W - tw) / 2 - 36; x1 = (W + tw) / 2 + 36
    d.rounded_rectangle((x0, H - 128, x1, H - 44), 20, fill=(17, 17, 17, 225))
    d.text((W / 2, H - 86), cap, font=f_cap, fill="white", anchor="mm")
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    p = os.path.join(tmp, "f_" + fn); im.save(p); seq.append((p, float(sec)))
card("z_outro.png", [("운전석에서 일감·충전·터미널을 한 화면으로", f_big, (5, 5, 5)),
                     ("접안 지연 확률 · 수소 잔량 · 마감 · 근무시간을 돈으로 따져 일정을 고릅니다", f_mid, (60, 60, 60)),
                     ("큰 버튼 두 개 · 음성 안내 · 일하는 중에는 업무 정보만", f_mid, (107, 107, 107))], 4.0)
lst = os.path.join(tmp, "concat.txt")
with open(lst, "w") as f:
    for p, s in seq: f.write(f"file '{p}'\nduration {s}\n")
    f.write(f"file '{seq[-1][0]}'\n")
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-vf", "fps=30,format=yuv420p",
                "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-movflags", "+faststart", out], check=True)
print(out, sum(s for _, s in seq), "s")
