#!/bin/bash
# usage: wfb.sh <branch> [workflow.yml]  — waits for the latest run of the workflow on that branch, prints result + compile errors
R=https://api.github.com/repos/Sohaib020/myfit-tracker
B=$1; W=${2:-build.yml}
sleep 20
for i in $(seq 1 100); do
  read id num st con sha < <(curl -s "$R/actions/workflows/$W/runs?per_page=1&branch=$B" | python3 -c "import sys,json;d=json.load(sys.stdin)['workflow_runs'][0];print(d['id'],d['run_number'],d['status'],d['conclusion'],d['head_sha'][:7])")
  [ "$st" = "completed" ] && break; sleep 20
done
echo "$W on $B run $num ($sha): $con"
for j in $(curl -s "$R/actions/runs/$id/jobs" | python3 -c "import sys,json;[print(j['id']) for j in json.load(sys.stdin)['jobs']]"); do
  curl -s "$R/check-runs/$j/annotations?per_page=50" | python3 -c "
import sys,json
for a in json.load(sys.stdin):
    if a['annotation_level']=='failure' and 'Node.js' not in a['message']:
        for line in a['message'].splitlines():
            if line.startswith('e: ') or 'What went wrong' in line or 'error:' in line: print(line)"
done
