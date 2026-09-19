"""
Script to generate adaptive launcher icons and Play Store assets from a single logo.jpg.
Dependencies: Pillow (`pip install Pillow`)
Run: `python tools/generate_icons.py`
"""
import os
import glob
import math
from PIL import Image, ImageDraw

LOGO_PATH = "logo.jpg"
RES_DIR = "app/src/main/res"
PLAY_STORE_DIR = "docs/branding"

ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

def clean_old_icons():
    print("Cleaning old icons...")
    for ext in ["*.png", "*.webp", "*.xml"]:
        for f in glob.glob(os.path.join(RES_DIR, "mipmap-*", f"ic_launcher{ext}")):
            os.remove(f)
        for f in glob.glob(os.path.join(RES_DIR, "mipmap-*", f"ic_launcher_round{ext}")):
            os.remove(f)
        for f in glob.glob(os.path.join(RES_DIR, "mipmap-*", f"ic_launcher_foreground{ext}")):
            os.remove(f)
        for f in glob.glob(os.path.join(RES_DIR, "mipmap-*", f"ic_launcher_monochrome{ext}")):
            os.remove(f)

def color_distance(c1, c2):
    return math.sqrt(sum((a - b)**2 for a, b in zip(c1[:3], c2[:3])))

def make_transparent_and_silhouette(img, bg_color, threshold=40):
    data = img.getdata()
    new_data = []
    silhouette_data = []
    
    for pixel in data:
        if pixel[3] == 0:
            new_data.append(pixel)
            silhouette_data.append((0, 0, 0, 0))
            continue
            
        dist = color_distance(pixel, bg_color)
        if dist < threshold:
            new_data.append((pixel[0], pixel[1], pixel[2], 0))
            silhouette_data.append((0, 0, 0, 0))
        else:
            new_data.append(pixel)
            silhouette_data.append((pixel[0], pixel[1], pixel[2], 255))
            
    transparent_img = Image.new("RGBA", img.size)
    transparent_img.putdata(new_data)
    
    silhouette_img = Image.new("RGBA", img.size)
    silhouette_img.putdata(silhouette_data)
    
    return transparent_img, silhouette_img

def generate():
    if not os.path.exists(LOGO_PATH):
        print(f"Error: {LOGO_PATH} not found.")
        return

    img = Image.open(LOGO_PATH).convert("RGBA")
    
    os.makedirs(PLAY_STORE_DIR, exist_ok=True)
    play_store = img.resize((512, 512), Image.Resampling.LANCZOS)
    play_store.save(os.path.join(PLAY_STORE_DIR, "play_store_512.png"), "PNG")
    
    bg_color = img.getpixel((0, 0))
    transparent_img, silhouette_img = make_transparent_and_silhouette(img, bg_color)
    
    for density, size in ADAPTIVE_SIZES.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        logo_size = int(size * 0.61)
        resized_logo = transparent_img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
        offset = (size - logo_size) // 2
        canvas.paste(resized_logo, (offset, offset), resized_logo)
        
        dir_path = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(dir_path, exist_ok=True)
        canvas.save(os.path.join(dir_path, "ic_launcher_foreground.png"), "PNG")
        
        mono_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        resized_silhouette = silhouette_img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
        mono_canvas.paste(resized_silhouette, (offset, offset), resized_silhouette)
        mono_canvas.save(os.path.join(dir_path, "ic_launcher_monochrome.png"), "PNG")

    for density, size in LEGACY_SIZES.items():
        legacy = img.resize((size, size), Image.Resampling.LANCZOS)
        dir_path = os.path.join(RES_DIR, f"mipmap-{density}")
        legacy.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(legacy, (0, 0), mask)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
        
    print("All icons generated successfully.")

if __name__ == "__main__":
    clean_old_icons()
    generate()
