package com.sitepark.vips.worker;

import com.sitepark.vips.command.Config;

/** Mutable global encoding configuration for the worker process. */
@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName", "PMD.RedundantFieldInitializer"})
public final class WorkerConfig {

  private boolean jpegInterlace = true;
  private boolean strip = false;

  public boolean jpegInterlace() {
    return jpegInterlace;
  }

  public boolean strip() {
    return strip;
  }

  /** Applies non-null fields from {@code cmd}, leaving all other settings unchanged. */
  public void apply(Config cmd) {
    if (cmd.jpegInterlace() != null) {
      this.jpegInterlace = cmd.jpegInterlace();
    }
    if (cmd.strip() != null) {
      this.strip = cmd.strip();
    }
  }
}
