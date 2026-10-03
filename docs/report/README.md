# Status reports

A report answers three questions for someone who has not been watching: what was
done, what is pending, and what happens next. It is published as a web page so it
can carry screenshots and charts, which is most of why it is worth making.

## Making one

1. `tools/report-facts.sh --run` — every number in the header and the tiles,
   read out of the repository. Write none of them from memory; the whole point
   of a report is that its figures are checkable.
2. Copy `template.html`, fill the slots, delete what does not apply. The classes
   carry the design, so new prose inherits it.
3. `python3 docs/report/make_charts.py` regenerates the SVGs in `charts/` from
   the data at the top of that script. Edit the data there, not the SVG.
   **Paste a chart inline** rather than linking it — the charts are drawn against
   `--viz-*` custom properties so they follow the reader's theme, which a linked
   file cannot do.
4. Screenshots go beside the page as `img/<name>.png`; take them from a real
   device where the point is that something works.
5. Publish as an artifact, passing the images as supporting files.

## What makes one worth reading

Show the evidence, not the claim. "The gate was wrong" is an assertion; a chart
of the same tone at −26 dBFS through one audio source and −59 dBFS through
another, with the threshold drawn across it, is the argument. Every item in the
last report that was worth anything had a number, a screenshot or a plot under it.

Say plainly which pending items need the reader and which have an owner, and put
the things only they can decide in their own chips. A report that lists twelve
equal-looking items makes the reader find the two that matter.

Keep the costs in. A report that only lists wins is an advertisement, and the
reader learns nothing they can act on.
