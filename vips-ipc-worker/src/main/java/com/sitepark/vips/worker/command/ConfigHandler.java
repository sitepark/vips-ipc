package com.sitepark.vips.worker.command;

import com.sitepark.vips.command.Config;
import com.sitepark.vips.worker.WorkerConfig;

public class ConfigHandler implements CommandHandler<Config> {

  private final WorkerConfig config;

  public ConfigHandler(WorkerConfig config) {
    this.config = config;
  }

  @Override
  public void handle(Config cmd) {
    config.apply(cmd);
  }
}
