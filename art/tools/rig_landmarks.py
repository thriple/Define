"""Finds body landmarks in each hero stage from its silhouette (cape removed by colour):
neck (narrowest point under the head), shoulders (width jumps), armpits (arms separate from torso),
hands (arms end), crotch (legs split), feet. Writes rig.json + an overlay image to check."""
import numpy as np, json, sys
from PIL import Image, ImageDraw
def hsv(rgb):
    r,g,b=[rgb[...,i] for i in range(3)]
    mx=rgb.max(-1); mn=rgb.min(-1); d=mx-mn+1e-6
    h=np.where(mx==r,((g-b)/d)%6,np.where(mx==g,(b-r)/d+2,(r-g)/d+4))*60
    return h, np.where(mx>0,d/np.maximum(mx,1e-6),0), mx
def runs(row):
    idx=np.flatnonzero(row)
    if idx.size==0: return []
    br=np.flatnonzero(np.diff(idx)>1)
    return [(int(a),int(b)) for a,b in zip(np.r_[idx[0],idx[br+1]], np.r_[idx[br],idx[-1]]) if b-a>=3]
def stable(cond, start, end, n=6):
    for y in range(start, end):
        if all(cond(yy) for yy in range(y, min(y+n,end))): return y
    return None
out={}
tiles=[]
for st in range(1,9):
    im=np.asarray(Image.open(f'hero_out/hero_stage{st}.webp').convert('RGBA')).astype(np.float32)/255
    a=im[...,3]>0.5; h,s,v=hsv(im[...,:3])
    cape=a&(h>8)&(h<42)&(s>0.62)&(v>0.35)
    from scipy import ndimage
    body=ndimage.binary_opening(a&~cape, iterations=1)
    filled=ndimage.binary_fill_holes(ndimage.binary_closing(a&~cape, iterations=2))
    H,W=body.shape
    RF=[runs(filled[y]) for y in range(H)]
    R=[runs(body[y]) for y in range(H)]
    ys=np.flatnonzero(body.any(1)); top,bot=int(ys[0]),int(ys[-1])
    cx=float(np.mean(np.nonzero(body)[1]))
    def central(y):
        for r in R[y]:
            if r[0]-4<=cx<=r[1]+4: return r
        return None
    def width(y):
        c=central(y); return (c[1]-c[0]) if c else 0
    def fwidth(y):
        for r in RF[y]:
            if r[0]-4<=cx<=r[1]+4: return r[1]-r[0]
        return 0
    L=bot-top
    neck=min(range(top+int(.10*L), top+int(.24*L)), key=lambda y: fwidth(y) if fwidth(y)>0 else 1e9)
    nw=fwidth(neck)
    shoulder=stable(lambda y: fwidth(y)>2.2*nw, neck, top+int(.40*L), 3)
    sw=fwidth(shoulder+15)
    big=lambda y, w=12: [r for r in R[y] if r[1]-r[0]>=w]
    armpit=stable(lambda y: len(big(y))>=3 and central(y) is not None and width(y)<0.75*sw, shoulder+int(.08*L), top+int(.60*L), 8)
    c=central(armpit+10); tl,tr=c
    crotch=stable(lambda y: len([r for r in big(y,22) if r[1]>tl-5 and r[0]<tr+5])>=2, armpit+int(.06*L), top+int(.80*L), 12)
    def has_arms(y):
        bb=big(y)
        return any(r[1]<tl-2 for r in bb) and any(r[0]>tr+2 for r in bb)
    hand=armpit
    for y in range(armpit, min(H, crotch+int(.15*L))):
        if has_arms(y): hand=y
    lm=dict(top=top,neck=neck,shoulder=shoulder,armpit=armpit,hand=hand,crotch=crotch,feet=bot,cx=round(cx,1),torsoL=tl,torsoR=tr,W=W,H=H)
    out[st]=lm
    print(st,lm)
    vis=Image.open(f'hero_out/hero_stage{st}.webp').convert('RGBA')
    bg=Image.new('RGBA',vis.size,(20,18,50,255)); bg.alpha_composite(vis); d=ImageDraw.Draw(bg)
    for k,col in [('neck','cyan'),('shoulder','yellow'),('armpit','lime'),('hand','magenta'),('crotch','orange'),('feet','white')]:
        d.line([(0,lm[k]),(W,lm[k])],fill=col,width=3)
    d.line([(cx,0),(cx,H)],fill='white',width=1); d.line([(tl,armpit),(tl,crotch)],fill='lime',width=2); d.line([(tr,armpit),(tr,crotch)],fill='lime',width=2)
    tiles.append(bg)
sheet=Image.new('RGBA',(tiles[0].width*8,tiles[0].height))
for i,t in enumerate(tiles): sheet.paste(t,(i*t.width,0))
sheet.convert('RGB').resize((sheet.width//2,sheet.height//2)).save('rig_overlay.png')
json.dump(out,open('rig.json','w'),indent=1)
