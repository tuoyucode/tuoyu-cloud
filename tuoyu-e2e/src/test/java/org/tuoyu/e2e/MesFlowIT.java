package org.tuoyu.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.tuoyu.e2e.support.ApiResponse;
import org.tuoyu.e2e.support.E2eClient;
import org.tuoyu.e2e.support.E2eConfig;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 制造执行（MES）的主流程：主数据 → 工单下达 → 按 BOM 领料 → 逐道报工 → 完工 → 完工检 → 入库 → 序列号 → 追溯 → 安灯 → 看板。
 * <p>
 * MES 是可选业务模块，默认部署中没有，所以只在 {@code -Dtuoyu.e2e.mes=true} 时执行。工单下达后不能删除，
 * 测试数据（编码以 e2e 开头）会留在被测系统中，只对测试环境运行。
 *
 * @author tuoyu
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "tuoyu.e2e.mes", matches = "true")
class MesFlowIT {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String suffix = random(6);

    private E2eClient admin;

    private long adminUserId;

    private long factoryId;
    private long workshopId;
    private long productId;
    private long rawId;
    private long routeId;
    private long rawWarehouseId;
    private long finishedWarehouseId;
    private long workorderId;
    private String batchCode;
    private final String materialLot = "LOT-E2E-" + suffix;

    @BeforeAll
    void setUp() {
        admin = E2eClient.anonymous(E2eConfig.load()).loginAsAdmin();
        adminUserId = ApiResponse.id(admin.get("/system/user/getInfo").assertOk("当前用户").data().path("user").path("userId"));
    }

    @AfterAll
    void tearDown() {
        if (admin != null) {
            admin.logoutQuietly();
        }
    }

    @Test
    @Order(1)
    void createMasterData() {
        admin.post("/mes/factory", Map.of("factoryCode", "E2E-F" + suffix, "factoryName", "E2E工厂" + suffix)).assertOk("新增工厂");
        factoryId = findId("/mes/factory/list", Map.of("factoryCode", "E2E-F" + suffix), "factoryId");

        admin.post("/mes/workshop", Map.of("factoryId", factoryId, "workshopCode", "E2E-W" + suffix,
            "workshopName", "E2E车间" + suffix)).assertOk("新增车间");
        workshopId = findId("/mes/workshop/list", Map.of("workshopCode", "E2E-W" + suffix), "workshopId");

        admin.post("/mes/material", Map.of("materialCode", "E2E-P" + suffix, "materialName", "E2E成品" + suffix,
            "materialType", "product", "unit", "台", "traceMode", "sn")).assertOk("新增成品");
        productId = findId("/mes/material/list", Map.of("materialCode", "E2E-P" + suffix), "materialId");
        admin.post("/mes/material", Map.of("materialCode", "E2E-R" + suffix, "materialName", "E2E原料" + suffix,
            "materialType", "raw", "unit", "个", "traceMode", "batch")).assertOk("新增原料");
        rawId = findId("/mes/material/list", Map.of("materialCode", "E2E-R" + suffix), "materialId");

        admin.post("/mes/bom", Map.of("materialId", productId, "bomCode", "E2E-BOM" + suffix, "bomVersion", "V1",
            "baseQty", 1, "isDefault", "Y",
            "items", List.of(Map.of("materialId", rawId, "qty", 2, "lossRate", 0)))).assertOk("新增物料清单");

        admin.post("/mes/route", Map.of("materialId", productId, "routeCode", "E2E-RT" + suffix, "routeName", "E2E工艺",
            "routeVersion", "V1", "isDefault", "Y",
            "operations", List.of(
                Map.of("seqNo", 10, "operationCode", "OP10", "operationName", "装配", "standardMinutes", 5, "firstInspection", "N"),
                Map.of("seqNo", 20, "operationCode", "OP20", "operationName", "包装", "standardMinutes", 2, "firstInspection", "N"))))
            .assertOk("新增工艺路线");
        routeId = findId("/mes/route/list", Map.of("routeCode", "E2E-RT" + suffix), "routeId");

        admin.post("/mes/warehouse", Map.of("factoryId", factoryId, "warehouseCode", "E2E-WR" + suffix,
            "warehouseName", "E2E原料仓", "warehouseType", "raw")).assertOk("新增原料仓");
        rawWarehouseId = findId("/mes/warehouse/list", Map.of("warehouseCode", "E2E-WR" + suffix), "warehouseId");
        admin.post("/mes/warehouse", Map.of("factoryId", factoryId, "warehouseCode", "E2E-WF" + suffix,
            "warehouseName", "E2E成品仓", "warehouseType", "finished")).assertOk("新增成品仓");
        finishedWarehouseId = findId("/mes/warehouse/list", Map.of("warehouseCode", "E2E-WF" + suffix), "warehouseId");

        assertThat(admin.get("/mes/workshop/options", Map.of("factoryId", factoryId)).assertOk("车间下拉").list())
            .extracting(node -> ApiResponse.id(node.path("id"))).contains(workshopId);
    }

    @Test
    @Order(2)
    void createAndReleaseWorkorder() {
        admin.post("/mes/workorder", Map.of("materialId", productId, "routeId", routeId, "workshopId", workshopId,
            "planQty", 10)).assertOk("新增工单");
        JsonNode created = admin.get("/mes/workorder/list", Map.of("workshopId", workshopId, "page", 1, "pageSize", 10))
            .assertOk("查询工单").items().get(0);
        workorderId = ApiResponse.id(created.path("workorderId"));
        assertThat(created.path("workorderStatus").asString()).isEqualTo("draft");
        assertThat(created.path("workorderCode").asString()).startsWith("WO");

        admin.post("/mes/workorder/" + workorderId + "/release", Map.of()).assertOk("下达工单");
        JsonNode detail = workorder();
        assertThat(detail.path("workorderStatus").asString()).isEqualTo("released");
        batchCode = detail.path("batchCode").asString();
        assertThat(batchCode).isNotBlank();
        assertThat(detail.path("tasks")).hasSize(2);
    }

    @Test
    @Order(3)
    void issueMaterialsFromBom() {
        long requestId = admin.post("/mes/material-request/generate", Map.of("workorderId", workorderId,
            "warehouseId", rawWarehouseId)).assertOk("按 BOM 生成领料单").data().asLong();
        JsonNode request = admin.get("/mes/material-request/" + requestId).assertOk("领料单详情").data();
        JsonNode item = request.path("items").get(0);
        assertThat(new BigDecimal(item.path("requestQty").asString())).isEqualByComparingTo("20");

        admin.post("/mes/material-request/" + requestId + "/submit", Map.of()).assertOk("提交领料单");
        admin.post("/mes/material-request/" + requestId + "/complete", Map.of("items", List.of(Map.of(
            "itemId", ApiResponse.id(item.path("itemId")), "actualQty", 20, "batchCode", materialLot)))).assertOk("领料完成");
    }

    @Test
    @Order(4)
    void reportOperationsAndComplete() {
        List<JsonNode> tasks = tasks();
        long first = ApiResponse.id(tasks.get(0).path("taskId"));
        long second = ApiResponse.id(tasks.get(1).path("taskId"));

        // 后续工序不能超过上一道工序的合格数
        admin.post("/mes/production-report", Map.of("taskId", second, "qualifiedQty", 1, "defectQty", 0))
            .assertError(400, "末道工序超出上一道合格数");

        admin.post("/mes/production-report", Map.of("taskId", first, "qualifiedQty", 10, "defectQty", 0)).assertOk("首道工序报工");
        admin.post("/mes/production-report", Map.of("taskId", second, "qualifiedQty", 9, "defectQty", 1)).assertOk("末道工序报工");

        JsonNode detail = workorder();
        assertThat(detail.path("workorderStatus").asString()).isEqualTo("in_progress");
        assertThat(new BigDecimal(detail.path("completedQty").asString())).isEqualByComparingTo("9");
        assertThat(new BigDecimal(detail.path("defectQty").asString())).isEqualByComparingTo("1");
        assertThat(admin.get("/mes/defect/list", Map.of("workorderCode", detail.path("workorderCode").asString(),
            "page", 1, "pageSize", 10)).assertOk("不良记录").total()).isEqualTo(1);

        admin.post("/mes/workorder/" + workorderId + "/complete", Map.of()).assertOk("工单完工");
        assertThat(workorder().path("workorderStatus").asString()).isEqualTo("completed");
    }

    @Test
    @Order(5)
    void inspectAndStockIn() {
        admin.post("/mes/inspection", Map.of("inspectionType", "final", "workorderId", workorderId)).assertOk("新增完工检");
        JsonNode inspection = admin.get("/mes/inspection/list", Map.of("workorderId", workorderId, "page", 1, "pageSize", 10))
            .assertOk("查询检验单").items().get(0);
        admin.post("/mes/inspection/" + ApiResponse.id(inspection.path("inspectionId")) + "/inspect", Map.of(
            "inspectQty", 9, "qualifiedQty", 9, "defectQty", 0, "inspectionResult", "passed")).assertOk("完工检");

        // 入库数量不能超过完工数
        admin.post("/mes/stock-in", Map.of("workorderId", workorderId, "warehouseId", finishedWarehouseId, "qty", 10))
            .assertError(400, "入库超过完工数");
        admin.post("/mes/stock-in", Map.of("workorderId", workorderId, "warehouseId", finishedWarehouseId, "qty", 9))
            .assertOk("完工入库");
        JsonNode stockIn = admin.get("/mes/stock-in/list", Map.of("workorderId", workorderId, "page", 1, "pageSize", 10))
            .assertOk("查询入库单").items().get(0);
        admin.post("/mes/stock-in/" + ApiResponse.id(stockIn.path("stockInId")) + "/complete", Map.of()).assertOk("入库完成");
        assertThat(new BigDecimal(workorder().path("stockedQty").asString())).isEqualByComparingTo("9");
    }

    @Test
    @Order(6)
    void serialNumbersAndTrace() {
        admin.post("/mes/sn/generate", Map.of("workorderId", workorderId, "count", 2))
            .assertError(400, "已完工的工单不能生成序列号");

        JsonNode byBatch = admin.get("/mes/trace", Map.of("keyword", batchCode)).assertOk("按生产批次追溯").data();
        assertThat(byBatch.path("matchType").asString()).isEqualTo("batch");
        JsonNode traced = byBatch.path("workorders").get(0);
        assertThat(ApiResponse.id(traced.path("workorderId"))).isEqualTo(workorderId);
        assertThat(traced.path("tasks")).hasSize(2);
        assertThat(traced.path("materials")).isNotEmpty();
        assertThat(traced.path("stockIns")).isNotEmpty();
        assertThat(byBatch.path("timeline")).isNotEmpty();

        JsonNode byLot = admin.get("/mes/trace", Map.of("keyword", materialLot)).assertOk("按物料批次追溯").data();
        assertThat(byLot.path("matchType").asString()).isEqualTo("material_batch");
        assertThat(byLot.path("workorders")).extracting(node -> ApiResponse.id(node.path("workorderId"))).contains(workorderId);
    }

    @Test
    @Order(7)
    void andonLifecycle() {
        admin.post("/mes/andon", Map.of("eventType", "equipment", "eventLevel", "critical", "title", "E2E设备停机" + suffix,
            "workshopId", workshopId)).assertOk("上报安灯");
        JsonNode event = admin.get("/mes/andon/list", Map.of("workshopId", workshopId, "page", 1, "pageSize", 10))
            .assertOk("查询安灯").items().get(0);
        long eventId = ApiResponse.id(event.path("eventId"));
        assertThat(event.path("eventStatus").asString()).isEqualTo("reported");

        admin.post("/mes/andon/" + eventId + "/assign", Map.of("handlerUserId", adminUserId)).assertOk("派单");
        admin.post("/mes/andon/" + eventId + "/process", Map.of()).assertOk("开始处理");
        admin.post("/mes/andon/" + eventId + "/resolve", Map.of("resolveRemark", "已更换传感器")).assertOk("解决");
        admin.post("/mes/andon/" + eventId + "/close", Map.of()).assertOk("关闭");
        JsonNode closed = admin.get("/mes/andon/" + eventId).assertOk("安灯详情").data();
        assertThat(closed.path("eventStatus").asString()).isEqualTo("closed");
        assertThat(closed.path("downtimeMinutes").isNull()).isFalse();
        assertThat(admin.get("/mes/andon/" + eventId + "/logs").assertOk("安灯履历").list()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @Order(8)
    void dashboardAndHistory() {
        JsonNode overview = admin.get("/mes/dashboard/overview", Map.of("workshopId", workshopId, "days", 7))
            .assertOk("生产看板").data();
        assertThat(overview.path("trend")).hasSize(7);
        assertThat(overview.path("defectTop")).isNotEmpty();

        List<String> actions = admin.get("/mes/workorder/" + workorderId + "/logs").assertOk("工单履历").list().stream()
            .map(node -> node.path("action").asString()).toList();
        assertThat(actions).contains("create", "release", "complete");
    }

    private JsonNode workorder() {
        return admin.get("/mes/workorder/" + workorderId).assertOk("工单详情").data();
    }

    private List<JsonNode> tasks() {
        List<JsonNode> tasks = new java.util.ArrayList<>();
        workorder().path("tasks").forEach(tasks::add);
        tasks.sort((a, b) -> Integer.compare(a.path("seqNo").asInt(), b.path("seqNo").asInt()));
        return tasks;
    }

    private long findId(String path, Map<String, ?> query, String idField) {
        Map<String, Object> params = new LinkedHashMap<>(query);
        params.put("page", 1);
        params.put("pageSize", 10);
        List<JsonNode> items = admin.get(path, params).assertOk("查询 " + path).items();
        assertThat(items).as(path + " 按 " + query + " 查询").hasSize(1);
        return ApiResponse.id(items.get(0).path(idField));
    }

    private static String random(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }

}
