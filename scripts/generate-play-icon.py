#!/usr/bin/env python3
"""Generate 512x512 Play Store icon from Android vector drawables."""

import subprocess
import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
OUTPUT_DIR = os.path.join(PROJECT_DIR, "app", "build", "play-store")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "icon_512.png")
VENV_DIR = os.path.join(PROJECT_DIR, ".venv", "play-icon")
BG_COLOR = "#F5F5F5"
ICON_SIZE = 512
FEATURE_SIZE = (1024, 500)

FEATURE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#F5F5F5" />
      <stop offset="100%" stop-color="#E8E8E8" />
    </linearGradient>
  </defs>

  <rect width="1024" height="500" fill="url(#bg)" />

  <g transform="translate(120, 110) scale(5.2)">
    <path fill="#000000"
          d="M27,2 C13.193,2 2,13.193 2,27 C2,40.807 13.193,52 27,52 C40.807,52 52,40.807 52,27 C52,13.193 40.807,2 27,2Z M27,5 C39.15,5 49,14.85 49,27 C49,39.15 39.15,49 27,49 C14.85,49 5,39.15 5,27 C5,14.85 14.85,5 27,5Z" />
    <path fill="none" stroke="#000000" stroke-width="2.5"
          stroke-linecap="round" stroke-linejoin="round"
          d="M5,27 L17,27 L19.5,19 L23,35 L26.5,13 L30,31 L32.5,27 L49,27" />
  </g>

  <text x="440" y="215" font-family="system-ui, -apple-system, sans-serif" font-size="64" font-weight="700" fill="#1A1A1A">GlucoseSync</text>
  <text x="440" y="280" font-family="system-ui, -apple-system, sans-serif" font-size="30" font-weight="400" fill="#666666">Real-time CGM glucose monitoring</text>
  <text x="440" y="330" font-family="system-ui, -apple-system, sans-serif" font-size="30" font-weight="400" fill="#666666">for Android &amp; Wear OS</text>

  <text x="440" y="410" font-family="system-ui, -apple-system, sans-serif" font-size="22" fill="#999999">Medtrum  ·  LibreLinkUp  ·  and more</text>
</svg>"""

BG_COLOR = "#F5F5F5"
ICON_SIZE = 512

SVG_TEMPLATE = """<svg xmlns="http://www.w3.org/2000/svg"
     width="{size}" height="{size}" viewBox="0 0 108 108">

  <rect width="108" height="108" fill="{bg_color}" rx="0" />

  <g transform="translate(19, 19) scale(1.3)">
    <path fill="#000000"
          d="M27,2 C13.193,2 2,13.193 2,27 C2,40.807 13.193,52 27,52 C40.807,52 52,40.807 52,27 C52,13.193 40.807,2 27,2Z M27,5 C39.15,5 49,14.85 49,27 C49,39.15 39.15,49 27,49 C14.85,49 5,39.15 5,27 C5,14.85 14.85,5 27,5Z" />

    <path fill="none" stroke="#000000" stroke-width="2.5"
          stroke-linecap="round" stroke-linejoin="round"
          d="M5,27 L17,27 L19.5,19 L23,35 L26.5,13 L30,31 L32.5,27 L49,27" />
  </g>

</svg>"""


def ensure_venv():
    if os.path.exists(os.path.join(VENV_DIR, "bin", "python3")):
        return

    print("Setting up virtual environment...")
    subprocess.check_call([sys.executable, "-m", "venv", VENV_DIR])
    subprocess.check_call([os.path.join(VENV_DIR, "bin", "pip"), "install", "cairosvg"])


def generate():
    venv_python = os.path.join(VENV_DIR, "bin", "python3")

    script = """
import cairosvg
import os

svg_content = '''{svg}'''
output_dir = r'{output_dir}'
output_file = r'{output_file}'

os.makedirs(output_dir, exist_ok=True)

cairosvg.svg2png(
    bytestring=svg_content.encode("utf-8"),
    write_to=output_file,
    output_width={size},
    output_height={size},
)

print(f"Generated: {{output_file}}")
""".format(
        svg=SVG_TEMPLATE.format(size=ICON_SIZE, bg_color=BG_COLOR),
        output_dir=OUTPUT_DIR,
        output_file=OUTPUT_FILE,
        size=ICON_SIZE,
    )

    feature_file = os.path.join(OUTPUT_DIR, "feature_1024x500.png")
    feature_script = """
import cairosvg
import os

svg_content = '''{svg}'''
output_dir = r'{output_dir}'
output_file = r'{output_file}'

os.makedirs(output_dir, exist_ok=True)

cairosvg.svg2png(
    bytestring=svg_content.encode("utf-8"),
    write_to=output_file,
    output_width=1024,
    output_height=500,
)

print(f"Generated: {{output_file}}")
""".format(
        svg=FEATURE_SVG,
        output_dir=OUTPUT_DIR,
        output_file=feature_file,
    )

    subprocess.check_call([venv_python, "-c", script])
    subprocess.check_call([venv_python, "-c", feature_script])

    print()
    print(f"App icon (512x512):    {OUTPUT_FILE}")
    print(f"Feature graphic (1024x500): {feature_file}")
    print()
    print("Upload to: Google Play Console > Store presence > Main store listing")


if __name__ == "__main__":
    ensure_venv()
    generate()
