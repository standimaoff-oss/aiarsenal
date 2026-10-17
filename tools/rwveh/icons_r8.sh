#!/bin/bash
# reply-8 vehicle item icons: Blender render of each .blend (icons.py) -> 64x64 PNG
B=/home/user/.cache/blender/blender-5.2.2-linux-x64/blender
O=/home/user/.cache/icons; mkdir -p $O
T=/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/item
for v in pz2 pz4 tiger sturmtiger maus ratte t34 kv2 is2 kv5 bm13; do
  $B -b /home/user/models/rw_$v.blend --python /home/user/tools/blender/icons.py -- $O/$v.png 215 28 > $O/$v.log 2>&1
  python3 -c "
from PIL import Image; im=Image.open('$O/$v.png').convert('RGBA'); bb=im.getbbox(); im=im.crop(bb); w,h=im.size; k=62/max(w,h)
im=im.resize((max(1,int(w*k)),max(1,int(h*k))),Image.LANCZOS); o=Image.new('RGBA',(64,64)); o.paste(im,((64-im.size[0])//2,(64-im.size[1])//2),im); o.save('$T/${v}_vehicle.png')" && echo "OK $v"
done
echo ICONS_DONE
