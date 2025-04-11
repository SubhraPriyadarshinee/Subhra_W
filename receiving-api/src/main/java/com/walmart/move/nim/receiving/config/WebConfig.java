package com.walmart.move.nim.receiving.config;

import com.walmart.move.nim.receiving.core.filter.MTFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Configuration to enable all web related hooks including but not limited to CORS and Swagger on
 * application level
 *
 * @author gok0072
 */
@Configuration
@EnableAsync
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

  private static final String API_VERSION = "1";

  @Autowired private ApplicationContext applicationContext;

  @Bean
  public FilterRegistrationBean<MTFilter> multiTenantFilter() {
    FilterRegistrationBean<MTFilter> registrationBean = new FilterRegistrationBean<>();

    registrationBean.setFilter(new MTFilter());
    registrationBean.addUrlPatterns("/recon/*");
    registrationBean.addUrlPatterns("/test/*");
    registrationBean.addUrlPatterns("/mock/*");
    registrationBean.addUrlPatterns("/receipts/*");
    registrationBean.addUrlPatterns("/deliveries/*");
    registrationBean.addUrlPatterns("/instructions/*");
    registrationBean.addUrlPatterns("/problems/*");
    registrationBean.addUrlPatterns("/v2/api-docs");
    registrationBean.addUrlPatterns("/configuration/*");
    registrationBean.addUrlPatterns("/containers/*");
    registrationBean.addUrlPatterns("/endgame/*");
    registrationBean.addUrlPatterns("/report/*");
    registrationBean.addUrlPatterns("/location-users/*");
    registrationBean.addUrlPatterns("/itemCatalog/*");
    registrationBean.addUrlPatterns("/automated/*");
    registrationBean.addUrlPatterns("/acl-logs/*");
    registrationBean.addUrlPatterns("/label-gen/*");
    registrationBean.addUrlPatterns("/items/*");
    registrationBean.addUrlPatterns("/docktags/*");
    registrationBean.addUrlPatterns("/returns/*");
    registrationBean.addUrlPatterns("/floor-line/*");
    registrationBean.addUrlPatterns("/pbyl/*");
    registrationBean.addUrlPatterns("/rdc/*");
    registrationBean.addUrlPatterns("/pallet/*");
    registrationBean.addUrlPatterns("/printjobs/*");
    registrationBean.addUrlPatterns("/item-catalog/*");
    registrationBean.addUrlPatterns("/locations/*");
    registrationBean.addUrlPatterns("/fixture/*");
    registrationBean.addUrlPatterns("/version/*");
    registrationBean.addUrlPatterns("/mfc/*");
    registrationBean.addUrlPatterns("/store/*");
    registrationBean.addUrlPatterns("/inventory/*");
    registrationBean.addUrlPatterns("/lpn/*");
    registrationBean.addUrlPatterns("/label-data/*");
    registrationBean.addUrlPatterns("/overflow/*");
    registrationBean.addUrlPatterns("/exception/*");
    registrationBean.addUrlPatterns("/audit/*");

    return registrationBean;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("swagger-ui.html")
        .addResourceLocations("classpath:/META-INF/resources/");

    registry
        .addResourceHandler("/webjars/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/");
    registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
    registry.addResourceHandler("/static/**").addResourceLocations("/static/");
  }

  public SpringResourceTemplateResolver templateResolver() {
    SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
    templateResolver.setApplicationContext(this.applicationContext);
    templateResolver.setPrefix("/");
    templateResolver.setSuffix(".html");
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setCacheable(true);
    return templateResolver;
  }

  @Bean
  public SpringTemplateEngine templateEngine() {
    SpringTemplateEngine templateEngine = new SpringTemplateEngine();
    templateEngine.setTemplateResolver(templateResolver());
    templateEngine.setEnableSpringELCompiler(true);
    return templateEngine;
  }

  @Bean
  public ThymeleafViewResolver viewResolver() {
    ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(templateEngine());
    return viewResolver;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
        .allowedOrigins("*")
        .allowedHeaders("*");
  }
}
