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
for fn, sec, cap in rows:
    im = Image.open(os.path.join(src, fn)).convert("RGB")
    if im.size != (W, H): im = im.resize((W, H), Image.LANCZOS)
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    tw = d.textlength(cap, font=f_cap)
    x0 = (W - tw) / 2 - 36; x1 = (W + tw) / 2 + 36
    d.rounded_rectangle((x0, H - 128, x1, H - 44), 20, fill=(17, 17, 17, 225))
    d.text((W / 2, H - 86), cap, font=f_cap, fill="white", anchor="mm")
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    p = os.path.join(tmp, "f_" + fn); im.save(p); seq.append((p, float(sec)))
card("z_outro.png", [("운전석에서 일감·충전·터미널을 한 화면으로", f_big, (5, 5, 5)),
                     ("접안 지연 확률 · 수소 잔량 · 마감 · 근무시간을 돈으로 따져 일정을 고릅니다", f_mid, (60, 60, 60)),
                     ("큰 버튼 두 개 · 음성 안내 · 컨테이너·충전소 정보는 시연용 예시 값", f_mid, (107, 107, 107))], 4.0)
lst = os.path.join(tmp, "concat.txt")
with open(lst, "w") as f:
    for p, s in seq: f.write(f"file '{p}'\nduration {s}\n")
    f.write(f"file '{seq[-1][0]}'\n")
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-vf", "fps=30,format=yuv420p",
                "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-movflags", "+faststart", out], check=True)
print(out, sum(s for _, s in seq), "s")
