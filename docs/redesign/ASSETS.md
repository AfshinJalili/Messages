# Design asset provenance

`Messages-Signal.pen` contains fictional message content and names for visual demonstration.

The family-album image in the media message and full-screen viewer was generated in Pendev with this prompt:

> Documentary overhead photograph of a well-loved family photo album open on a wooden dining table, a few candid printed photographs of ordinary family gatherings tucked into the pages, soft afternoon window light, realistic subtle wear and paper texture, warm natural colors, no readable text, no logos, landscape composition with the album centered.

The generated asset is saved as [`generated.png`](../../generated.png) beside the `.pen` file so the image fills can resolve when the canvas is reopened.

The interface icons are editable vector paths from `lucide-static` 1.48.0 (ISC license), replacing font-backed icon nodes that failed to render in the headless Pendev preview.

The app bundles the design's fonts in `app/src/main/res/font/`, copied unmodified from [google/fonts](https://github.com/google/fonts) under the SIL Open Font License 1.1: Funnel Sans (variable), Atkinson Hyperlegible Regular and Bold, and Vazirmatn (variable). The Library nav icon `ic_library_vector.xml` is Lucide `layers` (ISC).
