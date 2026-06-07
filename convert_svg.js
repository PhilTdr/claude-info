const fs = require('fs');
const sharp = require('sharp');
const png2icons = require('png2icons');
const svg2vectordrawable = require('svg2vectordrawable');
const path = require('path');

const svgPath = path.join(__dirname, 'desktopApp/src/main/resources/icon.svg');
const outPngPath = path.join(__dirname, 'desktopApp/src/main/resources/icon.png');
const outIcoPath = path.join(__dirname, 'desktopApp/src/main/resources/icon.ico');
const outIcnsPath = path.join(__dirname, 'desktopApp/src/main/resources/icon.icns');
const outXmlPath = path.join(__dirname, 'desktopApp/src/main/composeResources/drawable/icon.xml');

async function convert() {
    try {
        // 1. Generate 512x512 PNG (für Linux und als Basis)
        const png512Buffer = await sharp(svgPath)
            .resize(512, 512)
            .png()
            .toBuffer();
        
        fs.writeFileSync(outPngPath, png512Buffer);
        console.log('Successfully generated icon.png (512x512)');

        // 2. Generate 128x128 PNG buffer for ICO and ICNS (Win/macOS)
        const png128Buffer = await sharp(svgPath)
            .resize(128, 128)
            .png()
            .toBuffer();

        // Generate ICO
        const icoBuffer = png2icons.createICO(png128Buffer, png2icons.BILINEAR, 0, false);
        if (icoBuffer) {
            fs.writeFileSync(outIcoPath, icoBuffer);
            console.log('Successfully generated icon.ico (128x128)');
        } else {
            console.error('Failed to create ICO');
        }

        // Generate ICNS
        const icnsBuffer = png2icons.createICNS(png128Buffer, png2icons.BILINEAR, 0);
        if (icnsBuffer) {
            fs.writeFileSync(outIcnsPath, icnsBuffer);
            console.log('Successfully generated icon.icns (128x128)');
        } else {
            console.error('Failed to create ICNS');
        }

        // 3. Generate Android Vector Drawable XML for Compose Resources.
        // Strip <filter> / filter="..." references first — Vector Drawables do
        // not support SVG filter effects (drop shadow), and the converter would
        // otherwise warn or emit unusable output.
        const rawSvg = fs.readFileSync(svgPath, 'utf8');
        const svgForVd = rawSvg
            .replace(/<filter[\s\S]*?<\/filter>/g, '')
            .replace(/\sfilter="[^"]*"/g, '');

        const vectorXml = await svg2vectordrawable(svgForVd, { floatPrecision: 2 });
        fs.mkdirSync(path.dirname(outXmlPath), { recursive: true });
        fs.writeFileSync(outXmlPath, vectorXml);
        console.log('Successfully generated icon.xml (Compose Vector Drawable)');

    } catch (err) {
        console.error('Error converting file:', err);
    }
}

convert();
