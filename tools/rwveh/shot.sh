#!/bin/bash
# shot.sh name [wait] : screenshot of the Xvfb client, cropped, jpg -> /home/user/tmp/name.jpg and copy in ~/
sleep ${2:-6}
DISPLAY=:99 import -window root /home/user/tmp/$1.png
python3 -c "
from PIL import Image; Image.open('/home/user/tmp/$1.png').convert('RGB').crop((0,60,800,540)).save('/home/user/tmp/$1.jpg',quality=90)"
cp /home/user/tmp/$1.jpg /home/user/shot_$1.jpg
