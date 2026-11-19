"""Generate smooth vector adaptive icons and supersampled legacy PNGs. Requires Pillow."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'composeApp/src/androidMain/res'
# One organic sole; reflected for the other foot. Coordinates in adaptive 108dp canvas.
segments = [((-6,-7),(-9,-3),(-7,3),(-4,6)),((-4,6),(-2,9),(-5,12),(-2,15)),((-2,15),(1,18),(6,16),(6,12)),((6,12),(6,8),(3,6),(4,2)),((4,2),(5,-2),(8,-6),(5,-8)),((5,-8),(2,-11),(-3,-10),(-6,-7))]
toes = [(4,-14,3.3),(-2,-14,2.2),(-6.4,-12.3,1.7),(-9.2,-9.5,1.3)]
paths=[]
polygons=[]
for cx,cy,angle,mirror,color in [(42,58,-18,1,'#8DE8D0'),(65,45,18,-1,'#F1FFF9')]:
    a=math.radians(angle)
    def tx(p):
        x,y=p; x*=mirror
        return (cx+x*math.cos(a)-y*math.sin(a),cy+x*math.sin(a)+y*math.cos(a))
    def fmt(p): return ','.join(f'{v:.3f}' for v in tx(p))
    data='M'+fmt(segments[0][0]); points=[]
    for p0,p1,p2,p3 in segments:
        data+=' C'+ ' '.join(fmt(p) for p in (p1,p2,p3))
        for i in range(41):
            t=i/40; points.append(tx(tuple((1-t)**3*p0[j]+3*(1-t)**2*t*p1[j]+3*(1-t)*t*t*p2[j]+t**3*p3[j] for j in (0,1))))
    paths.append((data+' Z',color)); polygons.append((points,color))
    for x,y,r in toes:
        pts=[tx((x+r*math.cos(i*math.tau/80),y+r*1.15*math.sin(i*math.tau/80))) for i in range(80)]
        paths.append(('M'+' L'.join(','.join(f'{v:.3f}' for v in p) for p in pts)+' Z',color)); polygons.append((pts,color))
fg='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
fg+=''.join(f'    <path android:fillColor="{c}" android:pathData="{p}" />\n' for p,c in paths)+'</vector>\n'
(RES/'drawable-v24/ic_launcher_foreground.xml').write_text(fg)
(RES/'drawable/ic_launcher_background.xml').write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108" android:startColor="#173E49" android:endColor="#091723" />
        </aapt:attr>
    </path>
</vector>
''')
N=1080
im=Image.new('RGB',(N,N)); pixels=im.load()
assert pixels is not None
for y in range(N):
    for x in range(N):
        t=(x+y)/(2*(N-1)); pixels[x,y]=tuple(round(a+(b-a)*t) for a,b in zip((23,62,73),(9,23,35)))
draw=ImageDraw.Draw(im)
for pts,color in polygons: draw.polygon([(x*10,y*10) for x,y in pts],fill=color)
# Adaptive launcher displays the central 72dp, with the artwork inside the safe zone.
icon=im.crop((180,180,900,900)).convert('RGBA')
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder=RES/f'mipmap-{density}'; folder.mkdir(exist_ok=True)
    icon.resize((size,size),Image.Resampling.LANCZOS).save(folder/'ic_launcher.png')
    rounded=icon.copy(); mask=Image.new('L',icon.size); ImageDraw.Draw(mask).ellipse((0,0,719,719),fill=255); rounded.putalpha(mask)
    rounded.resize((size,size),Image.Resampling.LANCZOS).save(folder/'ic_launcher_round.png')
preview=ROOT/'build/icon-preview.png'; preview.parent.mkdir(exist_ok=True)
icon.resize((1024,1024),Image.Resampling.LANCZOS).save(preview)
print(preview)
