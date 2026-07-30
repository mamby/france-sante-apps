import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const assetsRoot = path.resolve(scriptDirectory, "..");
const repositoryRoot = path.resolve(assetsRoot, "..");
const manifest = JSON.parse(await fs.readFile(path.join(assetsRoot, "manifest.json"), "utf8"));
const failures = [];

for (const expected of manifest.generatedPngs) {
  const filePath = path.join(assetsRoot, expected.path);
  try {
    const metadata = await sharp(filePath).metadata();
    if (metadata.width !== expected.width || metadata.height !== expected.height) {
      failures.push(`${expected.path}: expected ${expected.width}x${expected.height}, got ${metadata.width}x${metadata.height}`);
    }
    if (Boolean(metadata.hasAlpha) !== expected.alpha) {
      failures.push(`${expected.path}: expected alpha=${expected.alpha}, got alpha=${Boolean(metadata.hasAlpha)}`);
    }
  } catch (error) {
    failures.push(`${expected.path}: ${error.message}`);
  }
}

for (const jsonPath of [
  "brand/personal-health-vault/source/palette.json",
  "platform/ios/AppIcon.appiconset/Contents.json",
  "platform/macos/AppIcon.appiconset/Contents.json",
]) {
  try {
    JSON.parse(await fs.readFile(path.join(assetsRoot, jsonPath), "utf8"));
  } catch (error) {
    failures.push(`${jsonPath}: ${error.message}`);
  }
}

const androidResourcePaths = [
  "drawable/ic_launcher_foreground.xml",
  "drawable/ic_launcher_monochrome.xml",
  "drawable/ic_splash_icon.xml",
  "drawable/ic_notification.xml",
  "mipmap-anydpi-v26/ic_launcher.xml",
  "mipmap-anydpi-v26/ic_launcher_round.xml",
  "values/colors.xml",
];
for (const resourcePath of androidResourcePaths) {
  const canonicalPath = path.join(assetsRoot, "platform/android/res", resourcePath);
  const integratedPath = path.join(repositoryRoot, "src/android/app/src/main/res", resourcePath);
  try {
    const [canonical, integrated] = await Promise.all([
      fs.readFile(canonicalPath),
      fs.readFile(integratedPath),
    ]);
    if (!canonical.equals(integrated)) failures.push(`${resourcePath}: integrated Android resource differs from the canonical asset`);
  } catch (error) {
    failures.push(`${resourcePath}: ${error.message}`);
  }
}

try {
  const ico = await fs.readFile(path.join(assetsRoot, "platform/windows/AppIcon.ico"));
  if (ico.readUInt16LE(0) !== 0 || ico.readUInt16LE(2) !== 1 || ico.readUInt16LE(4) !== 5) {
    failures.push("platform/windows/AppIcon.ico: invalid ICO header or frame count");
  }
} catch (error) {
  failures.push(`platform/windows/AppIcon.ico: ${error.message}`);
}

if (failures.length > 0) {
  const message = failures.join("\n");
  if (typeof process === "undefined") throw new Error(message);
  console.error(message);
  process.exitCode = 1;
} else {
  console.log(`Validated ${manifest.generatedPngs.length} PNG assets, asset catalogs, Android integration, and the Windows ICO.`);
}
