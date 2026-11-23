#!/bin/sh
# Publish an anonymous report: copy it from the private intake repository to the public one, then
# link and close the private issue. The reporter's app then shows "Published" with the public link.
#
#   reports/publish.sh <issue number> [--keep-device]
#
# Needs the GitHub CLI (gh), logged in as the maintainer. Read the report first: remove anything personal.
set -eu
INTAKE=0x0SegFault/guitarnest-reports
PUBLIC=0x0SegFault/guitarnest
N="${1:?issue number in $INTAKE}"
KEEP_DEVICE="${2:-}"

TITLE=$(gh issue view "$N" -R "$INTAKE" --json title --jq .title)
LABELS=$(gh issue view "$N" -R "$INTAKE" --json labels --jq '[.labels[].name | select(. != "anonymous")] | join(",")')
# Public text: without the hidden follow-up hash and (unless asked) without the device line.
BODY=$(gh issue view "$N" -R "$INTAKE" --json body --jq .body | grep -v '^<!-- follow-up:' | \
  if [ "$KEEP_DEVICE" = "--keep-device" ]; then cat; else grep -v '^Device:'; fi)

URL=$(gh issue create -R "$PUBLIC" --title "$TITLE" --body "$BODY

_From an anonymous report._" ${LABELS:+--label "$LABELS"})
echo "Public issue: $URL"

# The status endpoint finds this line and gives the link to the reporter's app.
CURRENT=$(gh issue view "$N" -R "$INTAKE" --json body --jq .body)
gh issue edit "$N" -R "$INTAKE" --body "$CURRENT

Public issue: $URL"
gh issue close "$N" -R "$INTAKE" --comment "Published as $URL"
