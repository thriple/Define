"""Per-stage rig maps (R = cape weight, G = glow: cyan eye lenses + emblem) and a deformation preview that
uses exactly the displacement math the app's shader uses."""
import json, numpy as np
from PIL import Image
from scipy import ndimage
rig = json.load(open('rig_clean.json'))
def hsv(rgb):
    r,g,b=[rgb[...,i] for i in range(3)]
    mx=rgb.max(-1); mn=rgb.min(-1); d=mx-mn+1e-6
    h=np.where(mx==r,((g-b)/d)%6,np.where(mx==g,(b-r)/d+2,(r-g)/d+4))*60
    return h, np.where(mx>0,d/np.maximum(mx,1e-6),0), mx
def smooth(e0,e1,x):
    t=np.clip((x-e0)/(e1-e0),0,1); return t*t*(3-2*t)

def displacement(X, Y, R, breath, sway, pump):
    H=R['H']; sh=max(R['shoulder'], R['neck']+18); arm=R['armpit']; hand=R['hand']; cr=R['crotch']; ft=R['feet']; cx=R['cx']; th=R['torsoHalf']
    A=0.0065*H*(breath+0.6*pump); B=0.016*breath+0.05*pump
    chestY=sh+0.55*(arm-sh)
    ax=np.abs(X-cx); side=np.sign(X-cx)
    armBlend=smooth(th-6, th+12, ax)
    armFade=1-smooth(hand, hand+0.05*H, Y)
    lift=(1-armBlend)*(1-smooth(sh, cr, Y)) + armBlend*armFade
    dy=-A*lift
    g=np.exp(-((Y-chestY)/(0.55*(arm-sh)))**2)
    gArm=smooth(sh, chestY, Y)*armFade
    dx=(1-armBlend)*B*(X-cx)*g + armBlend*side*B*th*gArm
    swayW=1-smooth(cr, ft, Y)
    dx=dx+0.004*H*sway*swayW
    return dx, dy

for st in range(1,9):
    R=rig[str(st)]
    im=np.asarray(Image.open(f'hero_out/hero_stage{st}.webp').convert('RGBA')).astype(np.float32)/255
    a=im[...,3]; h,s,v=hsv(im[...,:3])
    cape=(a>0.5)&(h>8)&(h<42)&(s>0.62)&(v>0.35)
    # keep only cape below the shoulders and outside the face; soften
    cape[:int(R['shoulder'])]=False
    cape=ndimage.binary_opening(cape,iterations=2)
    capeW=ndimage.gaussian_filter(cape.astype(np.float32),3)
    glow=(a>0.5)&(h>170)&(h<200)&(s>0.45)&(v>0.6)
    glowW=ndimage.gaussian_filter(glow.astype(np.float32),1.5)
    m=np.zeros(im.shape[:2]+(3,),np.uint8)
    m[...,0]=np.clip(capeW*255,0,255); m[...,1]=np.clip(glowW*255*1.4,0,255)
    Image.fromarray(m).save(f'hero_out/hero_rig{st}.png', optimize=True)

# Preview: stage 1 and 8, exhale vs exaggerated inhale+pump (x4) and the |difference|
tiles=[]
for st in (1,8):
    R=rig[str(st)]
    src=np.asarray(Image.open(f'hero_out/hero_stage{st}.webp').convert('RGBA')).astype(np.float32)
    H,W=src.shape[:2]; Y,X=np.mgrid[0:H,0:W].astype(np.float32)
    def warp(breath,sway,pump,k):
        dx,dy=displacement(X,Y,R,breath,sway,pump)
        return np.stack([ndimage.map_coordinates(src[...,c],[Y-k*dy,X-k*dx],order=1,mode='constant') for c in range(4)],-1)
    ex=warp(0,0,0,1); inh=warp(1,0,1,4)
    diff=np.abs(inh[...,:3]-ex[...,:3]).mean(-1)
    def comp(img):
        bg=Image.new('RGBA',(W,H),(20,18,50,255)); bg.alpha_composite(Image.fromarray(np.clip(img,0,255).astype(np.uint8))); return bg.convert('RGB')
    heat=Image.fromarray(np.clip(diff*3,0,255).astype(np.uint8)).convert('RGB')
    tiles+= [comp(ex), comp(inh), heat]
    m=Image.open(f'hero_out/hero_rig{st}.png'); tiles.append(m)
sheet=Image.new('RGB',(tiles[0].width*len(tiles),tiles[0].height))
for i,t in enumerate(tiles): sheet.paste(t,(i*t.width,0))
sheet.resize((sheet.width//2,sheet.height//2)).save('rig_preview.png')
print('ok')
