#!/usr/bin/env bash
# 端到端测试的 CI 辅助脚本（.github/workflows/ci.yml 的 e2e-microservice、e2e-monolith 两个 job 使用）。
# 在仓库根目录执行，需要 JDK 21、Docker、curl。只在 CI 中使用：会改写工作区中的 .env 与 Nacos 配置文件。
#
#   tools/ci/e2e.sh prepare                         生成 .env（随机口令）、管理员口令，关闭登录图形验证码
#   tools/ci/e2e.sh middleware [nacos]              启动 MySQL、Redis（以及 Nacos）容器并等待就绪
#   tools/ci/e2e.sh init-db                         建库、执行全新安装脚本、设置管理员口令
#   tools/ci/e2e.sh import-nacos                    导入 Nacos 配置并确认验证码已关闭
#   tools/ci/e2e.sh start <名称> <模块目录> [java 参数...]   后台启动一个服务，日志写到 e2e-logs/<名称>.log
#   tools/ci/e2e.sh wait-health <名称> <端口> [秒]   等待服务的 actuator 就绪探针为 UP
#   tools/ci/e2e.sh wait-ready <名称> <地址> [秒]    等待公钥接口可用（经网关或单体）
#   tools/ci/e2e.sh collect-logs                    收集中间件容器日志（失败时上传）
#
# 口令全部在运行时随机生成（GenerateEnv），只在本次 CI 中有效，并用 ::add-mask:: 从日志中隐藏。
set -euo pipefail

LOG_DIR="${E2E_LOG_DIR:-e2e-logs}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4.9}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:8.6.3}"
# 导入工具（Nacos v1 接口）与本地开发验证过的是 Nacos 2.x
NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.5.1}"

log() { echo "[e2e] $*"; }
die() { echo "::error::$*"; exit 1; }

# 读取 .env 并导出给后续命令（服务也会自己读取 .env，这里导出是为了不依赖它：单体等场景同样生效）
load_env() {
  [ -f .env ] || die "缺少 .env，请先执行 tools/ci/e2e.sh prepare"
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
}

github_env() {
  if [ -n "${GITHUB_ENV:-}" ]; then
    echo "$1=$2" >> "$GITHUB_ENV"
  fi
}

mask() {
  if [ -n "${GITHUB_ACTIONS:-}" ] && [ -n "$1" ]; then
    echo "::add-mask::$1"
  fi
}

random_alnum() {
  local length="$1" value=""
  while [ "${#value}" -lt "$length" ]; do
    value+=$(head -c 256 /dev/urandom | tr -dc 'A-Za-z0-9')
  done
  echo "${value:0:$length}"
}

mysql_exec() {
  # 口令用环境变量传给容器内的客户端，不出现在命令行
  docker exec -i -e MYSQL_PWD="$TUOYU_DB_PASSWORD" tuoyu-mysql mysql -uroot -h127.0.0.1 --default-character-set=utf8mb4 "$@"
}

cmd_prepare() {
  [ -f pom.xml ] && [ -d deploy ] || die "请在仓库根目录执行"
  java tools/secrets/GenerateEnv.java --force > /dev/null

  # 生产安全的默认值：去掉 GenerateEnv 写入的开发便利项（全部端点、健康详情、接口文档、服务发现自动路由）
  sed -i -E '/^TUOYU_(ACTUATOR_EXPOSURE|HEALTH_SHOW_DETAILS|API_DOCS_ENABLED|GATEWAY_DISCOVERY_LOCATOR)=/d' .env
  # 中间件都在本机（容器映射端口）
  sed -i -E 's/^TUOYU_DB_HOST=.*/TUOYU_DB_HOST=127.0.0.1/; s/^TUOYU_REDIS_HOST=.*/TUOYU_REDIS_HOST=127.0.0.1/' .env
  # 仍用 dev profile：CI 关闭了登录图形验证码，Nacos 未开鉴权，prod 下启动自检 P6、P7 会拒绝启动
  grep -q '^TUOYU_PROFILES_ACTIVE=dev$' .env || die ".env 中的 TUOYU_PROFILES_ACTIVE 应为 dev"

  # 从日志中隐藏所有口令与密钥
  local line key
  while IFS= read -r line; do
    key="${line%%=*}"
    case "$key" in
      *PASSWORD*|*SECRET*|*TOKEN*|*KEY*|*IDENTITY_VALUE*) mask "${line#*=}" ;;
    esac
  done < <(grep -E '^TUOYU_[A-Z0-9_]+=' .env)

  # 管理员口令：随机生成，init-db 把它的 BCrypt 哈希写进数据库，端到端测试从环境变量读取
  local admin_password
  admin_password="E2e$(random_alnum 21)"
  mask "$admin_password"
  github_env TUOYU_E2E_PASSWORD "$admin_password"

  # 关闭登录图形验证码（P7 在 dev profile 下只警告）。两道保险：
  # 1) 改写将要导入 Nacos（微服务）或直接读取（单体，TUOYU_CONFIG_DIR）的配置文件，不受配置优先级影响；
  # 2) 环境变量 SECURITY_CAPTCHA_ENABLED=false：Spring Boot 中环境变量的优先级高于 spring.config.import 导入的配置。
  # 端到端测试的第一步（/auth/code）会确认最终生效的值
  local auth_config=deploy/config/nacos/tuoyu-auth.yml
  sed -i -E '/^  captcha:/,/^    enabled:/ s/^(    enabled:)[[:space:]]*true/\1 false/' "$auth_config"
  grep -A4 -E '^  captcha:' "$auth_config" | grep -qE '^    enabled: false' || die "未能在 $auth_config 中关闭验证码"
  github_env SECURITY_CAPTCHA_ENABLED false
  github_env TUOYU_CONFIG_DIR "$PWD/deploy/config/nacos"

  mkdir -p "$LOG_DIR"
  log "已生成 .env 与管理员口令，已关闭登录图形验证码"
}

cmd_middleware() {
  load_env
  local with_nacos="${1:-}"
  docker run -d --name tuoyu-mysql -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD="$TUOYU_DB_PASSWORD" -e TZ=Asia/Shanghai \
    "$MYSQL_IMAGE" \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci \
    --explicit_defaults_for_timestamp=true --lower_case_table_names=1 > /dev/null
  docker run -d --name tuoyu-redis -p 6379:6379 -e TZ=Asia/Shanghai \
    "$REDIS_IMAGE" redis-server --requirepass "$TUOYU_REDIS_PASSWORD" > /dev/null
  if [ "$with_nacos" = "nacos" ]; then
    # Nacos 不开鉴权（与本地开发环境一致）：启动自检 P6 在 dev profile 下只警告
    docker run -d --name tuoyu-nacos -p 8848:8848 -p 9848:9848 \
      -e MODE=standalone -e TZ=Asia/Shanghai -e JVM_XMS=256m -e JVM_XMX=512m -e JVM_XMN=128m \
      -e NACOS_AUTH_TOKEN="$TUOYU_NACOS_AUTH_TOKEN" \
      -e NACOS_AUTH_IDENTITY_KEY="$TUOYU_NACOS_IDENTITY_KEY" \
      -e NACOS_AUTH_IDENTITY_VALUE="$TUOYU_NACOS_IDENTITY_VALUE" \
      "$NACOS_IMAGE" > /dev/null
  fi

  # MySQL 初始化期间的临时实例不监听 TCP，能用 127.0.0.1 连上说明正式实例已就绪
  wait_for "MySQL" 180 mysql_exec -e 'select 1'
  wait_for "Redis" 60 bash -c "docker exec tuoyu-redis redis-cli -a \"\$TUOYU_REDIS_PASSWORD\" --no-auth-warning ping | grep -q PONG"
  if [ "$with_nacos" = "nacos" ]; then
    wait_for "Nacos" 240 curl -fsS -o /dev/null http://127.0.0.1:8848/nacos/v1/console/health/readiness
  fi
}

wait_for() {
  local name="$1" timeout="$2"
  shift 2
  local started=$SECONDS
  until "$@" > /dev/null 2>&1; do
    if [ $((SECONDS - started)) -ge "$timeout" ]; then
      die "$name 在 ${timeout} 秒内未就绪"
    fi
    sleep 3
  done
  log "$name 已就绪（用时约 $((SECONDS - started)) 秒）"
}

cmd_init_db() {
  load_env
  [ -n "${TUOYU_E2E_PASSWORD:-}" ] || die "缺少 TUOYU_E2E_PASSWORD（应由 prepare 写入 GITHUB_ENV）"
  # 三个库都执行全新安装脚本（与各自的 Flyway V1 基线逐字一致）。tuoyu-system 启动时按基线 1 接入，
  # 再执行 V2 及以后的迁移；工作流与任务调度库在单体中同样可用
  local db
  for db in tuoyu-cloud tuoyu-workflow tuoyu-job; do
    mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`$db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
    mysql_exec "$db" < "deploy/sql/$db.sql"
    log "已初始化 $db"
  done

  # 管理员口令改为本次随机生成的值（种子数据中的口令不在 CI 中使用）。
  # htpasswd 生成 $2y$ 前缀的 BCrypt，与 $2a$ 算法相同，后端（hutool BCrypt）只识别 $2a$
  command -v htpasswd > /dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq apache2-utils > /dev/null; }
  local hash
  hash=$(htpasswd -nbBC 10 "" "$TUOYU_E2E_PASSWORD" | tr -d ':\r\n' | sed -E 's/^\$2[by]\$/$2a$/')
  [[ "$hash" == '$2a$10$'* ]] || die "生成管理员口令哈希失败"
  printf "UPDATE sys_user SET password = '%s' WHERE user_name = 'admin';\n" "$hash" | mysql_exec tuoyu-cloud
  # 任务调度的令牌须与 .env 一致（GenerateEnv 的提示）
  printf "UPDATE sj_group_config SET token = '%s';\n" "$TUOYU_SNAILJOB_TOKEN" | mysql_exec tuoyu-job
  log "已设置管理员口令与任务调度令牌"
}

cmd_import_nacos() {
  load_env
  java tools/nacos/ImportNacosConfig.java --apply
  # 确认导入后的 tuoyu-auth.yml 中验证码已关闭
  curl -fsS "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=tuoyu-auth.yml&group=DEFAULT_GROUP" \
    | grep -A4 -E '^  captcha:' | grep -qE '^    enabled: false' \
    || die "Nacos 中的 tuoyu-auth.yml 未关闭验证码"
  log "Nacos 配置已导入"
}

# 可执行 jar：优先 <artifactId>-exec.jar（单体需要依赖业务模块时，repackage 可能改用 classifier exec），
# 其次 <artifactId>.jar。需要固定时设置 APP_JAR_SUFFIX（例如 -exec）
resolve_jar() {
  local dir="$1" artifact
  artifact=$(basename "$dir")
  if [ -n "${APP_JAR_SUFFIX:-}" ]; then
    echo "$dir/target/$artifact$APP_JAR_SUFFIX.jar"
  elif [ -f "$dir/target/$artifact-exec.jar" ]; then
    echo "$dir/target/$artifact-exec.jar"
  else
    echo "$dir/target/$artifact.jar"
  fi
}

cmd_start() {
  local name="$1" dir="$2"
  shift 2
  load_env
  local jar
  jar=$(resolve_jar "$dir")
  [ -f "$jar" ] || die "找不到 $name 的可执行 jar：$jar（应由 build job 的 app-jars 制品提供）"
  mkdir -p "$LOG_DIR"
  # 工作目录为仓库根目录（与 IDEA 启动配置一致），服务从这里读取 .env
  nohup java -Xms256m -Xmx768m -XX:+UseSerialGC -Dfile.encoding=UTF-8 \
    -Dmanagement.endpoint.health.probes.enabled=true "$@" -jar "$jar" \
    > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
  log "已启动 $name（$jar，PID $!）"
}

check_alive() {
  local name="$1" pid
  pid=$(cat "$LOG_DIR/$name.pid" 2>/dev/null || true)
  if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    echo "::group::$name 日志（最后 200 行）"
    tail -n 200 "$LOG_DIR/$name.log" 2>/dev/null || true
    echo "::endgroup::"
    die "$name 已退出，启动失败（完整日志见制品 e2e-logs）"
  fi
}

wait_until() {
  local name="$1" timeout="$2" check="$3"
  local deadline=$((SECONDS + timeout))
  until eval "$check"; do
    check_alive "$name"
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "::group::$name 日志（最后 200 行）"
      tail -n 200 "$LOG_DIR/$name.log" 2>/dev/null || true
      echo "::endgroup::"
      die "$name 在 ${timeout} 秒内未就绪"
    fi
    sleep 5
  done
  log "$name 已就绪"
}

cmd_wait_health() {
  local name="$1" port="$2" timeout="${3:-300}"
  load_env
  # 用就绪探针判断“启动完成、可以接收请求”，不受可选组件（MQ、邮件等）健康状态的影响；
  # 探针由 start 时的 management.endpoint.health.probes.enabled=true 开启。actuator 用监控账号做 Basic 认证
  wait_until "$name" "$timeout" \
    "curl -sS -m 5 -u \"\$TUOYU_MONITOR_USERNAME:\$TUOYU_MONITOR_PASSWORD\" http://127.0.0.1:$port/actuator/health/readiness 2>/dev/null | grep -q '\"status\":\"UP\"'"
}

cmd_wait_ready() {
  local name="$1" url="$2" timeout="${3:-300}"
  wait_until "$name" "$timeout" \
    "curl -fsS -m 5 '$url/auth/crypto/public-key' 2>/dev/null | grep -Eq '\"code\":0[,}]'"
}

cmd_collect_logs() {
  mkdir -p "$LOG_DIR"
  local container
  for container in tuoyu-mysql tuoyu-redis tuoyu-nacos; do
    if docker inspect "$container" > /dev/null 2>&1; then
      docker logs "$container" > "$LOG_DIR/$container.log" 2>&1 || true
    fi
  done
  # 服务自己的日志（logback 写到工作目录下的 logs/）
  if [ -d logs ]; then
    cp -r logs "$LOG_DIR/app-logs" || true
  fi
}

main() {
  local command="${1:-}"
  shift || true
  case "$command" in
    prepare) cmd_prepare "$@" ;;
    middleware) cmd_middleware "$@" ;;
    init-db) cmd_init_db "$@" ;;
    import-nacos) cmd_import_nacos "$@" ;;
    start) cmd_start "$@" ;;
    wait-health) cmd_wait_health "$@" ;;
    wait-ready) cmd_wait_ready "$@" ;;
    collect-logs) cmd_collect_logs "$@" ;;
    *) sed -n '2,15p' "$0"; exit 2 ;;
  esac
}

main "$@"
