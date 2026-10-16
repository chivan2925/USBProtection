package com.group.usbshield.server.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.util.List;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("USBShield Admin Server").version("0.2")
        .description("Session Admin auth + CSRF; per-endpoint Agent credentials. Policy uses whitelistedDevices. See docs/API.md."))
        .components(new Components()
            .addSecuritySchemes("adminSession",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID"))
            .addSecuritySchemes("agentEndpoint",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Endpoint-Id"))
            .addSecuritySchemes("agentToken",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Agent-Token"))
            .addSecuritySchemes("csrf",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")));}
    @Bean OpenApiCustomizer authDocs(){return api->api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,op)->{
        boolean mutation=method!=PathItem.HttpMethod.GET;
        if(path.startsWith("/api/agent/") && !path.endsWith("/enroll"))op.setSecurity(List.of(new SecurityRequirement().addList("agentEndpoint").addList("agentToken")));
        else if(path.startsWith("/api/admin/") || path.endsWith("/me") || path.endsWith("/logout")){
            var requirement=new SecurityRequirement().addList("adminSession");if(mutation)requirement.addList("csrf");op.setSecurity(List.of(requirement));
        } else if(path.endsWith("/login"))op.setSecurity(List.of(new SecurityRequirement().addList("csrf")));
        for(String code:List.of("400","401","403","404","409","503"))op.getResponses().addApiResponse(code,new io.swagger.v3.oas.models.responses.ApiResponse().description("See API.md error contract; status/message/timestamp"));
    }));}
}
