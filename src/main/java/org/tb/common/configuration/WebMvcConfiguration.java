package org.tb.common.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.ContentVersionStrategy;
import org.springframework.web.servlet.resource.VersionResourceResolver;

@Configuration
public class WebMvcConfiguration extends WebMvcConfigurerAdapter {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    VersionResourceResolver versionResourceResolver = new VersionResourceResolver()
        .addVersionStrategy(new ContentVersionStrategy(), "/**");

    registry.addResourceHandler("/style/*.css")
        .addResourceLocations("/style/")
        .setCachePeriod(60 * 60 * 24 * 365) /* one year */
        .resourceChain(true)
        .addResolver(versionResourceResolver);

    registry.addResourceHandler("/scripts/*.js")
        .addResourceLocations("/scripts/")
        .setCachePeriod(60 * 60 * 24 * 365) /* one year */
        .resourceChain(true)
        .addResolver(versionResourceResolver);

  }

}
