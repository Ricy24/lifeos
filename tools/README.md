# Branding Tools

This directory contains scripts to automate the generation of Android launcher icons.

## Generating Icons

To regenerate all icons from the `logo.jpg` at the project root, run:
```bash
pip install Pillow
python tools/generate_icons.py
```

### Monochrome Icon (Not Supported yet)
The `<monochrome>` tag in `ic_launcher.xml` has been removed. The current source `logo.jpg` is a flat raster image which often has anti-aliasing artifacts when trying to compute a single-tone silhouette computationally. For a clean monochrome icon (used in themed icons on Android 13+), you must manually create a Vector Drawable (`ic_launcher_monochrome.xml`) containing a single path outline of the logo and place it in the `mipmap-anydpi-v26` folder. Once created, add the `<monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>` tag back to the adaptive icon XMLs.
