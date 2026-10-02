#!/bin/bash
# usage: wf.sh <workflow file>  — waits for its latest run and prints failure annotations
R=https://api.github.com/repos/Sohaib020/myfit-tracker
W=$1
sleep 15
for i in $(seq 1 90); do
  read id num st con < <(curl -s "$R/actions/workflows/$W/runs?per_page=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['workflow_runs'][0];print(d['id'],d['run_number'],d['status'],d['conclusion'])")
  [ "$st" = "completed" ] && break; sleep 20
done
echo "$W run $num: $con"
for j in $(curl -s "$R/actions/runs/$id/jobs" | python3 -c "import sys,json;[print(j['id']) for j in json.load(sys.stdin)['jobs']]"); do
  curl -s "$R/check-runs/$j/annotations?per_page=50" | python3 -c "
import sys,json
for a in json.load(sys.stdin):
    if a['annotation_level']=='failure' and 'Node.js' not in a['message']: print(a['title'],'::',a['message'])"
done
