/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.github.malonetalk.annotation.LoginRequired;
import io.github.malonetalk.annotation.RequirePermission;
import io.github.malonetalk.config.WebMvcConfig;
import io.github.malonetalk.controller.SysRoleController;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.exception.GlobalExceptionHandler;
import io.github.malonetalk.mapper.RoleCapabilityMapper;
import io.github.malonetalk.mapper.RoleHiddenColumnMapper;
import io.github.malonetalk.mapper.RoleTablePermissionMapper;
import io.github.malonetalk.mapper.SysRoleMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.dto.RoleDataScopeDto;
import io.github.malonetalk.model.dto.SaveColumnPermissionRequest;
import io.github.malonetalk.model.dto.SaveTablePermissionRequest;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.service.PermissionService;
import io.github.malonetalk.service.RolePermissionService;
import io.github.malonetalk.service.SysRoleService;
import io.github.malonetalk.service.SysUserService;
import io.github.malonetalk.utils.JwtUtil;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PermissionIntegrationTest {

    @Test
    void legacyAndAtomicGrantEndpointsRemainProtectedAndCompatible() throws Exception {
        var users = mock(SysUserService.class);
        when(users.selectAuthProjection(7))
                .thenReturn(
                        Optional.of(UserContextBo.builder().userId(7).superAdmin(true).build()));
        var jwt = mock(JwtUtil.class);
        when(jwt.parseUserId("valid")).thenReturn(7);
        var grants = mock(RolePermissionService.class);
        var permissions = new PermissionService(users, mock(RoleCapabilityMapper.class));
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new SysRoleController(mock(SysRoleService.class), grants))
                        .addInterceptors(
                                new AuthInterceptor(permissions, jwt, new UserContextHolder()))
                        .setControllerAdvice(
                                new GlobalExceptionHandler(new ExceptionResponseMapper()))
                        .defaultRequest(
                                put("/").header("Authorization", "Bearer valid")
                                        .contentType(MediaType.APPLICATION_JSON))
                        .build();
        String tableBody = "{\"datasourceId\":1,\"tableNames\":[\"orders\"]}";
        String columnBody =
                "{\"datasourceId\":1,\"tableName\":\"orders\",\"columnNames\":[\"phone\"]}";
        String scopeBody =
                "{\"datasourceId\":1,\"tableNames\":[\"orders\"],\"hiddenColumns\":{\"orders\":[\"phone\"]}}";
        mvc.perform(put("/api/sys/role/2/permissions").content(tableBody))
                .andExpect(status().isOk());
        mvc.perform(put("/api/sys/role/2/columns").content(columnBody)).andExpect(status().isOk());
        mvc.perform(put("/api/sys/role/2/data-scope").content(scopeBody))
                .andExpect(status().isOk());
        verify(grants).savePermissions(2, new SaveTablePermissionRequest(1, List.of("orders")));
        verify(grants)
                .saveColumnPermissions(
                        2, new SaveColumnPermissionRequest(1, "orders", List.of("phone")));
        verify(grants)
                .saveDataScope(
                        2,
                        new RoleDataScopeDto(
                                1, List.of("orders"), Map.of("orders", List.of("phone"))));
        clearInvocations(grants);
        when(users.selectAuthProjection(7))
                .thenReturn(
                        Optional.of(
                                UserContextBo.builder()
                                        .userId(7)
                                        .roleId(2)
                                        .superAdmin(false)
                                        .build()));
        mvc.perform(put("/api/sys/role/2/permissions").content(tableBody))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/sys/role/2/columns").content(columnBody))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/sys/role/2/data-scope").content(scopeBody))
                .andExpect(status().isForbidden());
        verifyNoInteractions(grants);
    }

    @Test
    void webConfigurationEnforcesPermissionsAndDefaultDenial() throws Exception {
        var users = mock(SysUserService.class);
        var capabilities = mock(RoleCapabilityMapper.class);
        var jwt = mock(JwtUtil.class);
        var holder = new UserContextHolder();
        when(jwt.parseUserId("valid")).thenReturn(7);
        when(users.selectAuthProjection(7))
                .thenReturn(
                        Optional.of(
                                UserContextBo.builder()
                                        .userId(7)
                                        .roleId(2)
                                        .superAdmin(false)
                                        .build()));
        when(capabilities.selectCodes(2)).thenReturn(List.of("sys:user:read"));
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(
                    MvcConfiguration.class,
                    WebMvcConfig.class,
                    AuthInterceptor.class,
                    TestController.class,
                    GlobalExceptionHandler.class,
                    ExceptionResponseMapper.class);
            context.addBeanFactoryPostProcessor(
                    factory -> {
                        factory.registerSingleton(
                                "permissions", new PermissionService(users, capabilities));
                        factory.registerSingleton("jwtUtil", jwt);
                        factory.registerSingleton("userContextHolder", holder);
                    });
            context.refresh();
            var mvc =
                    MockMvcBuilders.webAppContextSetup(context)
                            .defaultRequest(get("/").header("Authorization", "Bearer valid"))
                            .build();
            mvc.perform(get("/api/test/read")).andExpect(status().isOk());
            assertNull(holder.get());
            mvc.perform(get("/api/test/write")).andExpect(status().isForbidden());
            assertNull(holder.get());
            mvc.perform(get("/api/test/missing")).andExpect(status().isForbidden());
            mvc.perform(get("/api/test/profile")).andExpect(status().isOk());
            mvc.perform(get("/api/auth/login")).andExpect(status().isOk());
        }
    }

    @Test
    void configuredMapperPatternLoadsPermissionNamespacesAndStatements() throws Exception {
        var properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(input);
        }
        var configuration = new MybatisConfiguration();
        var mapperTypes =
                Set.of(
                        SysRoleMapper.class,
                        RoleTablePermissionMapper.class,
                        RoleHiddenColumnMapper.class);
        var names =
                Set.of(
                        "SysRoleMapper.xml",
                        "RoleTablePermissionMapper.xml",
                        "RoleHiddenColumnMapper.xml");
        int loaded = 0;
        var resources =
                new PathMatchingResourcePatternResolver()
                        .getResources(properties.getProperty("mybatis-plus.mapper-locations"));
        for (var resource : resources) {
            if (!names.contains(resource.getFilename())) continue;
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(
                                input,
                                configuration,
                                resource.toString(),
                                configuration.getSqlFragments())
                        .parse();
            }
            loaded++;
        }
        assertEquals(3, loaded);
        for (Class<?> mapper : mapperTypes) {
            assertTrue(configuration.hasMapper(mapper), mapper.getName());
            for (Method method : mapper.getDeclaredMethods()) {
                if (!method.isDefault()) {
                    String statement = mapper.getName() + "." + method.getName();
                    assertTrue(configuration.hasStatement(statement), statement);
                }
            }
        }
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfiguration {}

    @RestController
    static class TestController {
        @GetMapping("/api/test/read")
        @RequirePermission(PermissionEnum.SYS_USER_READ)
        String read() {
            return "ok";
        }

        @GetMapping("/api/test/write")
        @RequirePermission(PermissionEnum.SYS_USER_CREATE)
        String write() {
            return "ok";
        }

        @GetMapping({"/api/test/missing", "/api/auth/login"})
        String withoutPolicy() {
            return "ok";
        }

        @GetMapping("/api/test/profile")
        @LoginRequired
        String profile() {
            return "ok";
        }
    }
}
