import skia, sys, os, re
D='/home/claude/myfit-tracker/.claude/worktrees/agent-ad1783e462cae5567/app/src/main/assets/themes'
TK='/home/claude/myfit-tracker/.claude/worktrees/agent-ad1783e462cae5567/app/src/main/java/com/myfit/tracker/ui/theme/Theme.kt'
OUT='/tmp/claude-0/-home-claude-myfit-tracker/a3bd9ddc-9b2d-5774-bc12-e1c6a3a44cb1/scratchpad/themes.png'
common=open(f'{D}/common.agsl').read()
# per-theme stillT from Theme.kt (default 7)
still={}
try:
    for m in re.finditer(r'id = "(\w+)".*?\n    \)', open(TK).read(), re.S):
        s=re.search(r'stillT = ([\d.]+)f', m.group(0)); still[m.group(1)]=float(s.group(1)) if s else 7.0
except Exception: pass
W,H=300,650   # phone aspect ~ 1:2.17
COLS=int(os.environ.get('COLS','8'))
names=sys.argv[1:] or sorted(n[:-5] for n in os.listdir(D) if n.endswith('.agsl') and n!='common.agsl')
rows=(len(names)+COLS-1)//COLS
surf=skia.Surface(W*min(COLS,len(names)), (H+24)*rows)
can=surf.getCanvas(); can.clear(skia.ColorGRAY)
font=skia.Font(None,16); ok=[]
for i,n in enumerate(names):
    x=(i%COLS)*W; y=(i//COLS)*(H+24)
    can.drawString(n, x+6, y+17, font, skia.Paint(Color=skia.ColorWHITE))
    try: src=common+"\n"+open(f'{D}/{n}.agsl').read()
    except FileNotFoundError: print("MISSING",n); continue
    eff=skia.RuntimeEffect.MakeForShader(src)
    if eff is None: print("COMPILE FAIL",n); continue
    u=skia.RuntimeEffectBuilder(eff)
    u.setUniform('res',[float(W),float(H)]); u.setUniform('t', still.get(n,7.0))
    can.save(); can.translate(x,y+24); can.drawRect(skia.Rect(0,0,W,H), skia.Paint(Shader=u.makeShader()))
    # mock frosted card to judge readability
    if os.environ.get('CARD','1')=='1':
        can.drawRoundRect(skia.Rect(16,140,W-16,250),18,18,skia.Paint(Color=skia.Color4f(1,1,1,0.10)))
        can.drawRoundRect(skia.Rect(16,264,W-16,330),18,18,skia.Paint(Color=skia.Color4f(1,1,1,0.10)))
    can.restore(); ok.append(n)
surf.makeImageSnapshot().save(OUT, skia.kPNG)
print("ok",len(ok),ok)
