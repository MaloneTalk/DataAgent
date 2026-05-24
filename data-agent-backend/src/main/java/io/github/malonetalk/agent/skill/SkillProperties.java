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

import io.github.malonetalk.common.Constants;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = Constants.PROPERTIES_PREFIX + ".skill")
public class SkillProperties {

    private List<FileSystemSource> filesystem = new ArrayList<>();
    private List<GitSource> git = new ArrayList<>();
    private List<ClasspathSource> classpath = new ArrayList<>();
    private List<NacosSource> nacos = new ArrayList<>();

    @Data
    public static class FileSystemSource {
        private String path;
        private boolean writeable = true;
        private String source;
    }

    @Data
    public static class GitSource {
        private String url;
        private String branch;
        private String localPath;
        private String source;
        private boolean autoSync = true;
    }

    @Data
    public static class ClasspathSource {
        private String resourcePath;
        private String source;
    }

    @Data
    public static class NacosSource {
        private String serverAddr;
        private String namespace;
        private String username;
        private String password;
        private String skillVersion;
        private String skillLabel;
        private String source;
        // NacosSkillRepository.getAllSkills() returns empty because the Nacos AI API has no
        // list-all endpoint. Users must explicitly specify which skills to load by name.
        private List<String> skillNames = new ArrayList<>();
    }
}
