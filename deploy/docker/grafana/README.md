# grafana

- `grafana.ini`：Grafana 配置，由 `compose-extras.yml`（metrics profile）只读挂载；
- 数据保存在 compose 数据卷 `grafana-data` 中，不需要在宿主机建目录或修改权限；
- 管理员口令来自 `.env` 中的 `TUOYU_GRAFANA_ADMIN_PASSWORD`。
