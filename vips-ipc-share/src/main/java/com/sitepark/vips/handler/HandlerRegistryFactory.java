package com.sitepark.vips.handler;

/** Factory that creates a {@link HandlerRegistry} for the worker process. */
@FunctionalInterface
public interface HandlerRegistryFactory {

  HandlerRegistry create();
}
