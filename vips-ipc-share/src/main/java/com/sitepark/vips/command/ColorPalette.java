package com.sitepark.vips.command;

import java.util.List;

public record ColorPalette(List<ColorPaletteEntry> colors) {

  public ColorPalette(List<ColorPaletteEntry> colors) {
    this.colors = List.copyOf(colors);
  }

  public List<ColorPaletteEntry> colors() {
    return List.copyOf(colors);
  }
}
