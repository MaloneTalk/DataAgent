package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.github.malonetalk.service.DomainService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GetDomainsTool implements MarkAgentTool {

    private final DomainService domainService;

    @Tool(
            name = "get_domains",
            description =
                    "Get available data domains in the datasource. Call this tool first to discover"
                            + " what domains are available before querying tables.")
    public List<String> getDomains() {
        return domainService.listDomainNames();
    }
}
