import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import sharp from "sharp";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const assetsRoot = path.resolve(scriptDirectory, "..");
const repositoryRoot = path.resolve(assetsRoot, "..");

const colors = Object.freeze({
  teal: "#0E7C73",
  softWhite: "#F2F6F4",
  black: "#000000",
});

const outerPath = [
  "M372 206",
  "H652",
  "C743.679 206 818 280.321 818 372",
  "V652",
  "C818 743.679 743.679 818 652 818",
  "H372",
  "C280.321 818 206 743.679 206 652",
  "V372",
  "C206 280.321 280.321 206 372 206",
  "Z",
].join(" ");

const seamSegments = [
  [
    { x: 180, y: 346 },
    { x: 342, y: 321 },
    { x: 399, y: 413 },
    { x: 483, y: 514 },
  ],
  [
    { x: 483, y: 514 },
    { x: 571, y: 619 },
    { x: 656, y: 692 },
    { x: 844, y: 665 },
  ],
];

function cubicPoint([p0, p1, p2, p3], t) {
  const mt = 1 - t;
  return {
    x: mt ** 3 * p0.x + 3 * mt ** 2 * t * p1.x + 3 * mt * t ** 2 * p2.x + t ** 3 * p3.x,
    y: mt ** 3 * p0.y + 3 * mt ** 2 * t * p1.y + 3 * mt * t ** 2 * p2.y + t ** 3 * p3.y,
  };
}

function cubicDerivative([p0, p1, p2, p3], t) {
  const mt = 1 - t;
  return {
    x: 3 * mt ** 2 * (p1.x - p0.x) + 6 * mt * t * (p2.x - p1.x) + 3 * t ** 2 * (p3.x - p2.x),
    y: 3 * mt ** 2 * (p1.y - p0.y) + 6 * mt * t * (p2.y - p1.y) + 3 * t ** 2 * (p3.y - p2.y),
  };
}

function buildSeamOutlinePath(strokeWidth = 68, samplesPerSegment = 48) {
  const samples = seamSegments.flatMap((segment, segmentIndex) =>
    Array.from({ length: samplesPerSegment + 1 }, (_, index) => {
      if (segmentIndex > 0 && index === 0) return null;
      const t = index / samplesPerSegment;
      const point = cubicPoint(segment, t);
      const derivative = cubicDerivative(segment, t);
      const length = Math.hypot(derivative.x, derivative.y);
      return {
        point,
        normal: { x: -derivative.y / length, y: derivative.x / length },
      };
    }).filter(Boolean),
  );

  const radius = strokeWidth / 2;
  const left = samples.map(({ point, normal }) => ({
    x: point.x + normal.x * radius,
    y: point.y + normal.y * radius,
  }));
  const right = samples.toReversed().map(({ point, normal }) => ({
    x: point.x - normal.x * radius,
    y: point.y - normal.y * radius,
  }));
  const polygon = [...left, ...right];
  return polygon
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(3)} ${point.y.toFixed(3)}`)
    .concat("Z")
    .join(" ");
}

const seamOutlinePath = buildSeamOutlinePath();
const markPath = `${outerPath} ${seamOutlinePath}`;
const androidAdaptiveCanvasDp = 108;
const androidForegroundTargetSizeDp = 49;
const markSourceSize = 818 - 206;
const markSourceSizeDp = markSourceSize / 1024 * androidAdaptiveCanvasDp;
const androidForegroundScale = (androidForegroundTargetSizeDp / markSourceSizeDp).toFixed(4);

function markSvg(color = colors.teal) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <defs><mask id="mark-mask" maskUnits="userSpaceOnUse"><rect width="1024" height="1024" fill="black"/><path d="${outerPath}" fill="white"/></mask></defs>
  <path fill="${color}" fill-rule="evenodd" mask="url(#mark-mask)" d="${markPath}"/>
</svg>
`;
}

function appIconSvg() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="${colors.teal}"/>
  <defs><mask id="mark-mask" maskUnits="userSpaceOnUse"><rect width="1024" height="1024" fill="black"/><path d="${outerPath}" fill="white"/></mask></defs>
  <path fill="${colors.softWhite}" fill-rule="evenodd" mask="url(#mark-mask)" d="${markPath}"/>
</svg>
`;
}

function windowsPlatedSvg() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect x="52" y="52" width="920" height="920" rx="224" fill="${colors.teal}"/>
  <defs><mask id="mark-mask" maskUnits="userSpaceOnUse"><rect width="1024" height="1024" fill="black"/><path d="${outerPath}" fill="white"/></mask></defs>
  <path fill="${colors.softWhite}" fill-rule="evenodd" mask="url(#mark-mask)" d="${markPath}"/>
</svg>
`;
}

function androidAdaptivePreviewSvg() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
  <rect width="1024" height="1024" fill="${colors.teal}"/>
  <defs><mask id="mark-mask" maskUnits="userSpaceOnUse"><rect width="1024" height="1024" fill="black"/><path d="${outerPath}" fill="white"/></mask></defs>
  <g transform="translate(512 512) scale(${androidForegroundScale}) translate(-512 -512)">
    <path fill="${colors.softWhite}" fill-rule="evenodd" mask="url(#mark-mask)" d="${markPath}"/>
  </g>
</svg>
`;
}

function androidVectorXml(fillColor) {
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <group
        android:pivotX="512"
        android:pivotY="512"
        android:scaleX="${androidForegroundScale}"
        android:scaleY="${androidForegroundScale}">
        <clip-path android:pathData="${outerPath}" />
        <path
            android:fillColor="${fillColor}"
            android:fillType="evenOdd"
            android:pathData="${markPath}" />
    </group>
</vector>
`;
}

function androidNotificationXml() {
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <group
        android:pivotX="512"
        android:pivotY="512"
        android:scaleX="1.2"
        android:scaleY="1.2">
        <clip-path android:pathData="${outerPath}" />
        <path
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd"
            android:pathData="${markPath}" />
    </group>
</vector>
`;
}

const adaptiveIconXml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
`;

const androidColorsXml = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launcher_background">${colors.teal}</color>
    <color name="splash_background">#FFFFFF</color>
</resources>
`;

async function ensureParent(filePath) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
}

async function writeText(relativePath, contents) {
  const filePath = path.join(assetsRoot, relativePath);
  await ensureParent(filePath);
  await fs.writeFile(filePath, contents.replaceAll("\n", "\r\n"), "utf8");
  return filePath;
}

async function writeRepositoryText(relativePath, contents) {
  const filePath = path.join(repositoryRoot, relativePath);
  await ensureParent(filePath);
  await fs.writeFile(filePath, contents.replaceAll("\n", "\r\n"), "utf8");
  return filePath;
}

const generatedPngs = [];

async function renderPng(relativePath, svg, width, height = width, options = {}) {
  const filePath = path.join(assetsRoot, relativePath);
  await ensureParent(filePath);
  let pipeline = sharp(Buffer.from(svg)).resize(width, height, { fit: "fill" });
  if (options.opaque) pipeline = pipeline.flatten({ background: colors.teal }).removeAlpha();
  await pipeline.png({ compressionLevel: 9 }).toFile(filePath);
  generatedPngs.push({
    path: relativePath.replaceAll("\\", "/"),
    width,
    height,
    alpha: !options.opaque,
  });
  return fs.readFile(filePath);
}

function iOSContents() {
  return JSON.stringify({
    images: [{
      filename: "AppIcon-1024.png",
      idiom: "universal",
      platform: "ios",
      size: "1024x1024",
    }],
    info: { author: "xcode", version: 1 },
  }, null, 2) + "\n";
}

function macOSContents() {
  const images = [
    ["16x16", "1x", "AppIcon-16.png"],
    ["16x16", "2x", "AppIcon-32.png"],
    ["32x32", "1x", "AppIcon-32.png"],
    ["32x32", "2x", "AppIcon-64.png"],
    ["128x128", "1x", "AppIcon-128.png"],
    ["128x128", "2x", "AppIcon-256.png"],
    ["256x256", "1x", "AppIcon-256.png"],
    ["256x256", "2x", "AppIcon-512.png"],
    ["512x512", "1x", "AppIcon-512.png"],
    ["512x512", "2x", "AppIcon-1024.png"],
  ].map(([size, scale, filename]) => ({ filename, idiom: "mac", scale, size }));
  return JSON.stringify({ images, info: { author: "xcode", version: 1 } }, null, 2) + "\n";
}

function createIco(images) {
  const headerSize = 6;
  const directorySize = images.length * 16;
  let imageOffset = headerSize + directorySize;
  const header = Buffer.alloc(headerSize);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);

  const entries = images.map(({ size, bytes }) => {
    const entry = Buffer.alloc(16);
    entry.writeUInt8(size === 256 ? 0 : size, 0);
    entry.writeUInt8(size === 256 ? 0 : size, 1);
    entry.writeUInt8(0, 2);
    entry.writeUInt8(0, 3);
    entry.writeUInt16LE(1, 4);
    entry.writeUInt16LE(32, 6);
    entry.writeUInt32LE(bytes.length, 8);
    entry.writeUInt32LE(imageOffset, 12);
    imageOffset += bytes.length;
    return entry;
  });
  return Buffer.concat([header, ...entries, ...images.map(({ bytes }) => bytes)]);
}

async function generateBrandSources() {
  await writeText("brand/personal-health-vault/source/app-icon.svg", appIconSvg());
  await writeText("brand/personal-health-vault/source/mark-teal.svg", markSvg(colors.teal));
  await writeText("brand/personal-health-vault/source/mark-reversed.svg", markSvg(colors.softWhite));
  await writeText("brand/personal-health-vault/source/mark-monochrome.svg", markSvg(colors.black));
  await writeText("brand/personal-health-vault/source/palette.json", JSON.stringify(colors, null, 2) + "\n");
  await renderPng("brand/personal-health-vault/preview/app-icon-1024.png", appIconSvg(), 1024, 1024, { opaque: true });
}

async function generateAndroid(integrateAndroid) {
  const files = new Map([
    ["platform/android/res/drawable/ic_launcher_foreground.xml", androidVectorXml("#FFF2F6F4")],
    ["platform/android/res/drawable/ic_launcher_monochrome.xml", androidVectorXml("#FFFFFFFF")],
    ["platform/android/res/drawable/ic_splash_icon.xml", androidVectorXml("#FF0E7C73")],
    ["platform/android/res/drawable/ic_notification.xml", androidNotificationXml()],
    ["platform/android/res/mipmap-anydpi-v26/ic_launcher.xml", adaptiveIconXml],
    ["platform/android/res/mipmap-anydpi-v26/ic_launcher_round.xml", adaptiveIconXml],
    ["platform/android/res/values/colors.xml", androidColorsXml],
  ]);
  for (const [relativePath, contents] of files) await writeText(relativePath, contents);
  await renderPng("platform/android/preview/adaptive-icon-512.png", androidAdaptivePreviewSvg(), 512, 512, { opaque: true });

  if (!integrateAndroid) return;
  const integrationFiles = new Map([
    ["src/android/app/src/main/res/drawable/ic_launcher_foreground.xml", androidVectorXml("#FFF2F6F4")],
    ["src/android/app/src/main/res/drawable/ic_launcher_monochrome.xml", androidVectorXml("#FFFFFFFF")],
    ["src/android/app/src/main/res/drawable/ic_splash_icon.xml", androidVectorXml("#FF0E7C73")],
    ["src/android/app/src/main/res/drawable/ic_notification.xml", androidNotificationXml()],
    ["src/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", adaptiveIconXml],
    ["src/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", adaptiveIconXml],
    ["src/android/app/src/main/res/values/colors.xml", androidColorsXml],
  ]);
  for (const [relativePath, contents] of integrationFiles) await writeRepositoryText(relativePath, contents);
}

async function generateApple() {
  const iosBase = "platform/ios/AppIcon.appiconset";
  await renderPng(`${iosBase}/AppIcon-1024.png`, appIconSvg(), 1024, 1024, { opaque: true });
  await writeText(`${iosBase}/Contents.json`, iOSContents());

  const macBase = "platform/macos/AppIcon.appiconset";
  for (const size of [16, 32, 64, 128, 256, 512, 1024]) {
    await renderPng(`${macBase}/AppIcon-${size}.png`, appIconSvg(), size, size, { opaque: true });
  }
  await writeText(`${macBase}/Contents.json`, macOSContents());
}

async function generateWindows() {
  const base = "platform/windows/Assets";
  const plated = windowsPlatedSvg();
  const lightUnplated = markSvg(colors.teal);
  const darkUnplated = markSvg(colors.softWhite);
  const targetSizes = [16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 256];
  for (const size of targetSizes) {
    await renderPng(`${base}/Square44x44Logo.targetsize-${size}.png`, plated, size);
    await renderPng(`${base}/Square44x44Logo.targetsize-${size}_altform-unplated.png`, darkUnplated, size);
    await renderPng(`${base}/Square44x44Logo.targetsize-${size}_altform-lightunplated.png`, lightUnplated, size);
  }

  const scales = [100, 125, 150, 200, 250, 300, 400];
  for (const scale of scales) {
    const square44 = Math.ceil(44 * scale / 100);
    const square150 = Math.ceil(150 * scale / 100);
    await renderPng(`${base}/Square44x44Logo.scale-${scale}.png`, plated, square44);
    await renderPng(`${base}/Square150x150Logo.scale-${scale}.png`, plated, square150);
  }

  for (const scale of [100, 125, 150, 200, 400]) {
    const size = Math.ceil(50 * scale / 100);
    await renderPng(`${base}/StoreLogo.scale-${scale}.png`, plated, size);
  }

  const icoImages = [];
  for (const size of [16, 24, 32, 48, 256]) {
    const bytes = await renderPng(`platform/windows/ico/AppIcon-${size}.png`, plated, size);
    icoImages.push({ size, bytes });
  }
  const icoPath = path.join(assetsRoot, "platform/windows/AppIcon.ico");
  await ensureParent(icoPath);
  await fs.writeFile(icoPath, createIco(icoImages));
}

async function writeManifest() {
  const manifest = {
    brand: "Personal Health Vault",
    mark: "Folded square",
    colors,
    generatedPngs: generatedPngs.toSorted((left, right) => left.path.localeCompare(right.path)),
  };
  await writeText("manifest.json", JSON.stringify(manifest, null, 2) + "\n");
}

export async function generateAssets({ integrateAndroid = false } = {}) {
  generatedPngs.length = 0;
  await generateBrandSources();
  await generateAndroid(integrateAndroid);
  await generateApple();
  await generateWindows();
  await writeManifest();
  return { generatedPngCount: generatedPngs.length, integrateAndroid };
}

const commandLine = typeof process === "undefined" ? [] : process.argv;
const isDirectRun = commandLine?.[1] && pathToFileURL(commandLine[1]).href === import.meta.url;
if (isDirectRun) {
  const result = await generateAssets({ integrateAndroid: commandLine.includes("--integrate-android") });
  console.log(`Generated ${result.generatedPngCount} PNG assets${result.integrateAndroid ? " and integrated Android resources" : ""}.`);
}
