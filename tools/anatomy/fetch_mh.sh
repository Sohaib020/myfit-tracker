#!/bin/sh
# MakeHuman CC0 assets (base mesh, muscular-male targets, default skeleton + weights).
set -e
B=https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data
D=$(dirname "$0")/mh; mkdir -p "$D"
curl -fsSL -o "$D/base.obj" $B/3dobjs/base.obj
curl -fsSL -o "$D/male-maxmuscle.target" $B/targets/macrodetails/universal-male-young-maxmuscle-averageweight.target
curl -fsSL -o "$D/male-maxmuscle-minweight.target" $B/targets/macrodetails/universal-male-young-maxmuscle-minweight.target
for e in caucasian african asian; do curl -fsSL -o "$D/$e-male-young.target" $B/targets/macrodetails/$e-male-young.target; done
curl -fsSL -o "$D/default.mhskel" $B/rigs/default.mhskel
curl -fsSL -o "$D/default_weights.mhw" $B/rigs/default_weights.mhw
