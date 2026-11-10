package org.tuoyu.mes.common.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("单据审批：随模块提供的流程定义文件")
class MesApprovalFlowFilesTest {

    private static final Path DIR = Path.of("deploy", "workflow");

    private static final MesApprovalProperties DEFAULTS = new MesApprovalProperties();

    @ParameterizedTest
    @CsvSource({"mes_defect_disposition.json, defect", "mes_material_supplement.json, supplement"})
    @DisplayName("流程编码与默认配置一致；开始后第一个节点是申请人，审批节点可以通过到结束、退回到申请人")
    void flowFile(String file, String kind) throws Exception {
        JsonNode flow = JsonMapper.builder().build().readTree(Files.readString(DIR.resolve(file)));
        String expected = "defect".equals(kind)
            ? DEFAULTS.getDefectDisposition().getFlowCode()
            : DEFAULTS.getSupplement().getFlowCode();
        assertThat(flow.get("flowCode").asString()).isEqualTo(expected);

        List<JsonNode> nodes = new ArrayList<>();
        flow.get("nodeList").forEach(nodes::add);
        assertThat(nodes).extracting(node -> node.get("nodeType").asInt()).containsExactly(0, 1, 1, 2);
        assertThat(nodes.get(0).get("skipList").get(0).get("nextNodeCode").asString()).isEqualTo("apply");
        assertThat(nodes.get(1).get("permissionFlag").asString()).isEqualTo("${initiator}");

        JsonNode approve = nodes.get(2);
        List<String> skips = new ArrayList<>();
        approve.get("skipList").forEach(skip ->
            skips.add(skip.get("skipType").asString() + ":" + skip.get("nextNodeCode").asString()));
        assertThat(skips).containsExactlyInAnyOrder("PASS:end", "REJECT:apply");
        assertThat(approve.get("permissionFlag").asString()).startsWith("role:");
    }

}
