package com.sitepark.vips.worker;

/** Factory that creates a {@link HandlerRegistry} for the worker process. */
@FunctionalInterface
public interface HandlerRegistryFactory {

  HandlerRegistry create();
}
