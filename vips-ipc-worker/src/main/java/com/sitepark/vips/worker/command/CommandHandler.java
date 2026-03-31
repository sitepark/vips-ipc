package com.sitepark.vips.worker.command;

import com.sitepark.vips.command.Command;

@FunctionalInterface
public interface CommandHandler<T extends Command> {

  void handle(T command);
}
