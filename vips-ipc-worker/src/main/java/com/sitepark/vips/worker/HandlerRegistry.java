package com.sitepark.vips.worker;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.response.Response;

/** Dispatches a {@link Command} to the appropriate handler and returns the resulting response. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HandlerRegistry {

  /**
   * Dispatches the command to the matching handler.
   *
   * <p>Implementations must never throw; any handler error must be returned as an {@link
   * com.sitepark.vips.response.ErrorResponse}.
   */
  Response dispatch(Command command);
}
