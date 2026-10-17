#!/usr/bin/env bash
# r24: publishes the normal (non-.txt) sources to the user's private GitHub repo standimaoff-oss/aimods.
# Needs ~/.git-credentials with the user's fine-grained token (NOT stored in the workspace - lost on sandbox reset).
# usage: bash tools/gh_push.sh "commit message"
set -euo pipefail
H=/home/user
R=$H/.cache/aimods
URL=https://github.com/standimaoff-oss/aimods.git
MSG=${1:-update}
if [ ! -d $R/.git ]; then rm -rf $R; git clone -q $URL $R; fi
cd $R && git pull -q --rebase origin main || true
mkdir -p $R/mods/aiarsenal $R/tools $R/notes $R/reports
rsync -a --delete --exclude build/ --exclude .gradle/ --exclude 'run*/' --exclude src/generated/ --exclude src/test/ $H/mods/aiarsenal/ $R/mods/aiarsenal/
rsync -a --delete --exclude __pycache__/ $H/tools/ $R/tools/
rsync -a --delete $H/notes/ $R/notes/
rsync -a --delete $H/reports/ $R/reports/
# GLB models live in the jar while stashed: put them back from the release jar if the tree has them stripped
J=$H/release/aiarsenal-1.0.0.jar
(cd $R/mods/aiarsenal/src/main/resources && unzip -qo $J 'assets/*' 'data/*')
cd $R
git add -A
if git diff --cached --quiet; then echo "nothing to commit"; else git commit -qm "$MSG"; fi
git push -q origin main
echo "pushed $(git rev-parse --short HEAD) $(git ls-files | wc -l) files"
