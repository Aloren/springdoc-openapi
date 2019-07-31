package org.springdoc.core;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springdoc.core.Constants.DEFAULT_TITLE;
import static org.springdoc.core.Constants.DEFAULT_VERSION;

@Component
public class InfoBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(InfoBuilder.class);

	@Autowired
	private ApplicationContext context;
	@Autowired
	private Optional<OpenAPI> openAPIBean;

	private InfoBuilder() {
		super();
	}

	public void build(OpenAPI openAPI) {
		Optional<OpenAPIDefinition> apiDef = getOpenAPIDefinition();
		if (apiDef.isPresent()) {
			buildOpenAPIWithOpenAPIDefinition(openAPI, apiDef.get());
		} else if(openAPIBean.isPresent()) {
			buildOpenAPIWithOpenAPIBean(openAPI, openAPIBean.get());
		} else {
			Info infos = new Info().title(DEFAULT_TITLE).version(DEFAULT_VERSION);
			openAPI.setInfo(infos);
		}
	}

	private Optional<OpenAPIDefinition> getOpenAPIDefinition() {
		// Look for OpenAPIDefinition in a spring managed bean
		Map<String, Object> openAPIDefinitionMap = context.getBeansWithAnnotation(OpenAPIDefinition.class);
		OpenAPIDefinition apiDef = null;
		if (openAPIDefinitionMap.size() > 1)
			LOGGER.warn(
					"found more than one OpenAPIDefinition class. springdoc-openapi will be using the first one found.");
		if (openAPIDefinitionMap.size() > 0) {
			Map.Entry<String, Object> entry = openAPIDefinitionMap.entrySet().iterator().next();
			Class<?> objClz = entry.getValue().getClass();
			apiDef = ReflectionUtils.getAnnotation(objClz, OpenAPIDefinition.class);
		}

		// Look for OpenAPIDefinition in the spring classpath
		else {
			ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
					false);
			scanner.addIncludeFilter(new AnnotationTypeFilter(OpenAPIDefinition.class));
			if (AutoConfigurationPackages.has(context)) {
				List<String> packagesToScan = AutoConfigurationPackages.get(context);
				apiDef = getApiDefClass(scanner, packagesToScan);
			}

		}
		return Optional.ofNullable(apiDef);
	}

	private static void buildOpenAPIWithOpenAPIDefinition(OpenAPI openAPI, OpenAPIDefinition apiDef) {
		// info
		AnnotationsUtils.getInfo(apiDef.info()).ifPresent(openAPI::setInfo);
		// OpenApiDefinition security requirements
		SecurityParser.getSecurityRequirements(apiDef.security()).ifPresent(openAPI::setSecurity);
		// OpenApiDefinition external docs
		AnnotationsUtils.getExternalDocumentation(apiDef.externalDocs()).ifPresent(openAPI::setExternalDocs);
		// OpenApiDefinition tags
		AnnotationsUtils.getTags(apiDef.tags(), false).ifPresent(tags -> openAPI.setTags(new ArrayList<>(tags)));
		// OpenApiDefinition servers
		AnnotationsUtils.getServers(apiDef.servers()).ifPresent(openAPI::setServers);
		// OpenApiDefinition extensions
		if (apiDef.extensions().length > 0) {
			openAPI.setExtensions(AnnotationsUtils.getExtensions(apiDef.extensions()));
		}
	}

	private static void buildOpenAPIWithOpenAPIBean(OpenAPI destinationOpenAPI, OpenAPI sourceOpenAPI) {
		destinationOpenAPI.setInfo(sourceOpenAPI.getInfo());
		destinationOpenAPI.setSecurity(sourceOpenAPI.getSecurity());
		destinationOpenAPI.setExternalDocs(sourceOpenAPI.getExternalDocs());
		destinationOpenAPI.setTags(sourceOpenAPI.getTags());
		destinationOpenAPI.setServers(sourceOpenAPI.getServers());
		destinationOpenAPI.setExtensions(sourceOpenAPI.getExtensions());
	}

	private OpenAPIDefinition getApiDefClass(ClassPathScanningCandidateComponentProvider scanner,
			List<String> packagesToScan) {
		for (String pack : packagesToScan) {
			for (BeanDefinition bd : scanner.findCandidateComponents(pack)) {
				// first one found is ok
				try {
					return AnnotationUtils.findAnnotation(Class.forName(bd.getBeanClassName()),
							OpenAPIDefinition.class);
				} catch (ClassNotFoundException e) {
					LOGGER.error("Class Not Found in classpath : {}", e.getMessage());
				}
			}
		}
		return null;
	}

}