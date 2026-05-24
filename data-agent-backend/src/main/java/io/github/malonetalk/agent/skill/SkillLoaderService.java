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
package io.github.malonetalk.agent.skill;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLoaderService {

    private final SkillProperties skillProperties;
    private final List<AgentSkillRepository> repositories = new ArrayList<>();

    public SkillBox createSkillBox(Toolkit toolkit) {
        SkillBox skillBox = new SkillBox(toolkit);
        List<AgentSkillRepository> repos = createRepositories();

        for (AgentSkillRepository repo : repos) {
            try {
                List<AgentSkill> skills = repo.getAllSkills();
                for (AgentSkill skill : skills) {
                    skillBox.registerSkill(skill);
                    log.info(
                            "Registered skill '{}' from source '{}'",
                            skill.getSkillId(),
                            skill.getSource());
                }
            } catch (Exception e) {
                log.error("Failed to load skills from repository: {}", repo.getRepositoryInfo(), e);
            }
        }

        // NacosSkillRepository.getAllSkills() returns an empty list because the Nacos AI
        // API does not provide a list-all-skills endpoint. Therefore, skills must be
        // loaded individually by name via getSkill(name), requiring the user to
        // explicitly configure the skill-names list in application properties.
        loadNacosSkillsByName(skillBox);

        return skillBox;
    }

    // NacosSkillRepository.getAllSkills() always returns an empty list (the Nacos AI API
    // lacks a list-all endpoint), so we load skills one by one using getSkill(name)
    // based on the user-configured skill-names list.
    private void loadNacosSkillsByName(SkillBox skillBox) {
        for (SkillProperties.NacosSource ns : skillProperties.getNacos()) {
            if (ns.getSkillNames().isEmpty()) {
                continue;
            }
            try {
                AiService aiService = createNacosAiService(ns);
                Properties props = new Properties();
                if (ns.getSkillVersion() != null) {
                    props.setProperty(
                            NacosSkillRepository.SKILL_VERSION_PATH, ns.getSkillVersion());
                }
                if (ns.getSkillLabel() != null) {
                    props.setProperty(NacosSkillRepository.SKILL_LABEL_PATH, ns.getSkillLabel());
                }
                try (NacosSkillRepository repo =
                        new NacosSkillRepository(aiService, ns.getNamespace(), props)) {
                    for (String skillName : ns.getSkillNames()) {
                        try {
                            AgentSkill skill = repo.getSkill(skillName);
                            skillBox.registerSkill(skill);
                            log.info(
                                    "Registered Nacos skill '{}' from namespace '{}'",
                                    skill.getSkillId(),
                                    ns.getNamespace());
                        } catch (Exception e) {
                            log.error(
                                    "Failed to load Nacos skill '{}' from namespace '{}'",
                                    skillName,
                                    ns.getNamespace(),
                                    e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error(
                        "Failed to initialize NacosSkillRepository for namespace '{}'",
                        ns.getNamespace(),
                        e);
            }
        }
    }

    List<AgentSkillRepository> createRepositories() {
        List<AgentSkillRepository> repos = new ArrayList<>();

        for (SkillProperties.FileSystemSource fs : skillProperties.getFilesystem()) {
            try {
                Path resolvedPath = Path.of(fs.getPath()).toAbsolutePath().normalize();
                log.info(
                        "FileSystemSkillRepository path: {} (resolved to: {})",
                        fs.getPath(),
                        resolvedPath);
                FileSystemSkillRepository repo =
                        new FileSystemSkillRepository(
                                resolvedPath, fs.isWriteable(), fs.getSource());
                repos.add(repo);
            } catch (Exception e) {
                log.error("Failed to create FileSystemSkillRepository: {}", fs.getPath(), e);
            }
        }

        for (SkillProperties.GitSource gs : skillProperties.getGit()) {
            try {
                Path localPath = gs.getLocalPath() != null ? Path.of(gs.getLocalPath()) : null;
                GitSkillRepository repo =
                        new GitSkillRepository(
                                gs.getUrl(),
                                gs.getBranch(),
                                localPath,
                                gs.getSource(),
                                gs.isAutoSync());
                repos.add(repo);
                log.info("Created GitSkillRepository: {}", gs.getUrl());
            } catch (Exception e) {
                log.error("Failed to create GitSkillRepository: {}", gs.getUrl(), e);
            }
        }

        for (SkillProperties.ClasspathSource cs : skillProperties.getClasspath()) {
            try {
                ClasspathSkillRepository repo =
                        new ClasspathSkillRepository(cs.getResourcePath(), cs.getSource());
                repos.add(repo);
                log.info("Created ClasspathSkillRepository: {}", cs.getResourcePath());
            } catch (Exception e) {
                log.error("Failed to create ClasspathSkillRepository: {}", cs.getResourcePath(), e);
            }
        }

        repositories.addAll(repos);
        return repos;
    }

    private AiService createNacosAiService(SkillProperties.NacosSource ns) throws NacosException {
        Properties properties = new Properties();

        properties.setProperty(PropertyKeyConst.SERVER_ADDR, ns.getServerAddr());
        if (ns.getUsername() != null) {
            properties.setProperty(PropertyKeyConst.USERNAME, ns.getUsername());
        }
        if (ns.getPassword() != null) {
            properties.setProperty(PropertyKeyConst.PASSWORD, ns.getPassword());
        }
        if (ns.getNamespace() != null) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, ns.getNamespace());
        }
        return AiFactory.createAiService(properties);
    }

    @PreDestroy
    public void destroy() {
        for (AgentSkillRepository repo : repositories) {
            try {
                repo.close();
            } catch (Exception e) {
                log.warn("Failed to close repository: {}", repo.getRepositoryInfo(), e);
            }
        }
        repositories.clear();
    }
}
