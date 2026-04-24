package com.sitepark.vips.worker.command;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Result;

@FunctionalInterface
public interface CommandHandler<T extends Command<?>> {

  Result handle(T command);
}
