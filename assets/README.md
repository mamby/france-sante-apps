# Personal Health Vault assets

This folder contains the canonical **Folded square** identity and generated
application-icon resources. It lives at the repository root so branding stays
independent from any one platform implementation.

## Brand

- Primary teal: `#0E7C73`
- Soft white: `#F2F6F4`
- Approved mark: **Folded square**
- Construction: a calm rounded square divided by one flowing negative-space cut

The master artwork is in `brand/personal-health-vault/source`. Do not round the
outer canvas of Apple or Android source artwork; those platforms apply their own
icon masks.

## Generated platform resources

- `platform/android`: adaptive foreground, background, monochrome, notification,
  and launcher resources.
- `platform/ios`: a modern single-size `AppIcon.appiconset` using a 1024px source.
- `platform/macos`: a complete multi-size `AppIcon.appiconset`.
- `platform/windows`: WinUI/MSIX scale and target-size assets, light and dark
  unplated variants, package logos, and a multi-resolution `.ico`.

Store-listing screenshots, promotional banners, and social media artwork are
intentionally outside the current scope.

## Regenerate and verify

From this directory:

```powershell
npm install
npm run generate
npm run check
```

`npm run generate` also updates the native Android resources currently consumed
by `src/android/app`. Generated PNG metadata is recorded in `manifest.json` and
validated by `npm run check`.
