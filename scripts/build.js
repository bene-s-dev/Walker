const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '..');
const wwwDir = path.resolve(rootDir, 'www');

if (!fs.existsSync(wwwDir)) {
  fs.mkdirSync(wwwDir, { recursive: true });
}

const filesToCopy = [
  'index.html',
  'manifest.json',
  'sw.js',
  'icon-192.png',
  'icon-512.png',
  'icon.jpg'
];

filesToCopy.forEach(file => {
  const src = path.join(rootDir, file);
  const dest = path.join(wwwDir, file);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
    console.log(`Copied ${file} to www/`);
  }
});

console.log('Build completed successfully!');
