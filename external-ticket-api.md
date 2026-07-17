# 外部系统建单 API 接口文档

本文档定义外部系统调用社区智能运维协同平台创建工单的接口规范。接口用于第三方系统将事件、投诉、巡检异常或业务线索同步为本系统工单。

> 状态：接口设计稿，待后端按本文档实现。

## 1. 接口概览

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 外部系统创建工单 |
| 请求方法 | `POST` |
| 接口路径 | `/api/external/v1/tickets` |
| Content-Type | `application/json;charset=UTF-8` |
| 是否需要 JWT | 不需要 |
| 鉴权方式 | 外部系统 `UID` 请求头校验 |
| 返回格式 | 项目统一 `R<T>` 格式 |

基础地址按部署环境决定：

- 本地：`http://localhost:8188`
- 测试：`https://communityhub.slaicreativity.com`

## 2. 鉴权规则

外部系统调用时必须携带以下请求头：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `X-External-Uid` | 是 | 外部系统唯一标识，如 `legal-evaluation`。服务端配置中存在且启用即认为校验通过。 |
| `X-Request-Id` | 否 | 调用方请求追踪 ID，便于排查日志。 |

`UID` 同时用于识别调用方和访问校验。修改配置文件中的 `UID`、`enabled` 或小区白名单即可新增、停用或调整某个外部系统的访问权限。

推荐配置结构：

```yaml
external:
  ticket:
    clients:
      - uid: legal-evaluation
        name: 法治评估系统
        enabled: true
        allowed-community-codes:
          - COMM_BL
        default-reporter-name: 法治评估系统
        confirm-required: true
```

配置含义：

| 配置项 | 说明 |
| --- | --- |
| `uid` | 外部系统唯一标识，对应请求头 `X-External-Uid`。 |
| `name` | 外部系统显示名称，用于日志和追踪。 |
| `enabled` | 是否启用该外部系统。 |
| `allowed-community-codes` | 允许该外部系统建单的小区编码白名单。 |
| `default-reporter-name` | 请求未传上报人时的兜底上报人名称。 |
| `confirm-required` | 是否进入人工确认状态。建议默认 `true`。 |

## 3. 小区归属规则

外部建单必须明确小区归属，否则系统无法判断工单属于哪个小区。

请求体使用 `communityCode` 传入小区编码：

```json
{
  "communityCode": "COMM_BL"
}
```

服务端处理规则：

1. 根据 `communityCode` 查询 `t_community.code`。
2. 小区不存在时返回参数错误。
3. 小区状态不是 `ACTIVE` 时返回参数错误或禁止访问。
4. 校验 `communityCode` 是否在当前外部系统配置的 `allowed-community-codes` 中。
5. 校验通过后，将对应 `t_community.id` 写入工单 `t_ticket.community_id`。

不建议外部系统直接传 `communityId`，因为数据库 ID 在不同环境中可能不一致；`communityCode` 更适合作为跨系统集成字段。

## 4. 请求参数

### 4.1 请求体字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `externalTicketNo` | string | 是 | 外部系统工单号或事件号。用于幂等，建议同一外部系统内唯一。最大 128 字符。 |
| `communityCode` | string | 是 | 小区编码，对应 `t_community.code`，如 `COMM_BL`。 |
| `title` | string | 是 | 工单标题。最大 256 字符。 |
| `description` | string | 否 | 工单描述。最大 4000 字符。 |
| `location` | string | 是 | 位置，如 `幸福小区3栋2单元电梯`。最大 256 字符。 |
| `deptCode` | string | 否 | 责任部门编码，对应 `t_department.code`。推荐传。 |
| `deptId` | number | 否 | 责任部门 ID。仅在调用方能确认环境一致时使用。 |
| `deptName` | string | 否 | 责任部门名称。无 `deptCode/deptId` 时可作为显示值保存。 |
| `reporterId` | number | 否 | 本系统上报人用户 ID。传入时必须能在 `t_user` 找到。 |
| `reporterName` | string | 条件必填 | 上报人名称。未传 `reporterId` 时必填；若也未传，则使用外部系统配置的 `default-reporter-name`。 |
| `ticketType` | string | 否 | 工单类型，如 `设备维修`、`环境卫生`。最大 64 字符。 |
| `riskLevel` | string | 否 | 风险等级：`HIGH`、`MEDIUM`、`LOW`。 |
| `priority` | string | 否 | 优先级：`P0`、`P1`、`P2`、`P3`。 |
| `deadline` | string | 否 | 截止时间，推荐 ISO-8601 本地时间格式：`2026-05-28T18:00:00`。 |
| `remark` | string | 否 | 外部系统备注。可拼接到工单描述或记录到外部建单日志。 |

责任部门至少建议传 `deptCode`、`deptId`、`deptName` 之一。优先级建议为：`deptCode` > `deptId` > `deptName`。

### 4.2 请求示例

```json
{
  "externalTicketNo": "LAW-20260527-0001",
  "communityCode": "COMM_BL",
  "title": "3栋2单元电梯异响",
  "description": "外部系统上报：电梯运行时有明显异响，需要尽快排查。",
  "location": "幸福小区3栋2单元电梯",
  "deptCode": "EQUIPMENT_MAINTENANCE",
  "deptName": "设备维修部",
  "reporterName": "张三",
  "ticketType": "设备维修",
  "riskLevel": "MEDIUM",
  "priority": "P2",
  "deadline": "2026-05-28T18:00:00",
  "remark": "来自法治评估系统"
}
```

### 4.3 cURL 示例

```bash
curl -X POST 'https://example.com/api/external/v1/tickets' \
  -H 'Content-Type: application/json;charset=UTF-8' \
  -H 'X-External-Uid: legal-evaluation' \
  -H 'X-Request-Id: req-20260527-0001' \
  -d '{
    "externalTicketNo": "LAW-20260527-0001",
    "communityCode": "COMM_BL",
    "title": "3栋2单元电梯异响",
    "description": "外部系统上报：电梯运行时有明显异响，需要尽快排查。",
    "location": "幸福小区3栋2单元电梯",
    "deptCode": "EQUIPMENT_MAINTENANCE",
    "reporterName": "张三",
    "ticketType": "设备维修",
    "riskLevel": "MEDIUM",
    "priority": "P2",
    "deadline": "2026-05-28T18:00:00"
  }'
```

## 5. 响应格式

### 5.1 成功响应

建议返回外部建单包装对象，包含是否新建、外部单号和本系统工单信息。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "created": true,
    "externalUid": "legal-evaluation",
    "externalTicketNo": "LAW-20260527-0001",
    "ticket": {
      "id": 10086,
      "communityId": 1,
      "ticketNo": "TK-20260527-0001",
      "title": "3栋2单元电梯异响",
      "status": "PENDING_CONFIRM",
      "statusLabel": "待确认",
      "ticketType": "设备维修",
      "riskLevel": "MEDIUM",
      "priority": "P2",
      "deptId": 3,
      "deptName": "设备维修部",
      "reporterName": "张三",
      "location": "幸福小区3栋2单元电梯",
      "deadline": "2026-05-28T18:00:00"
    }
  }
}
```

### 5.2 幂等响应

当同一个 `X-External-Uid + externalTicketNo` 已经创建过工单时，建议直接返回已有工单，不重复创建。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "created": false,
    "externalUid": "legal-evaluation",
    "externalTicketNo": "LAW-20260527-0001",
    "ticket": {
      "id": 10086,
      "ticketNo": "TK-20260527-0001",
      "status": "PENDING_CONFIRM",
      "statusLabel": "待确认"
    }
  }
}
```

## 6. 错误码

| HTTP 状态 | `code` | 场景 | 示例 message |
| --- | --- | --- | --- |
| 400 | 400 | 请求参数缺失或格式错误 | `communityCode不能为空` |
| 401 | 401 | 缺少或错误的外部系统 UID | `外部系统UID无效` |
| 403 | 403 | 外部系统被禁用或无权访问该小区 | `外部系统无权在该小区建单` |
| 404 | 404 | 小区、部门或用户不存在 | `小区不存在：COMM_BL` |
| 409 | 409 | 外部单号冲突且请求内容不一致 | `外部工单号已存在` |
| 500 | 500 | 服务端异常 | `系统繁忙，请稍后重试` |

错误响应示例：

```json
{
  "code": 403,
  "message": "外部系统无权在该小区建单",
  "data": null
}
```

## 7. 业务规则

1. 外部建单必须写入小区归属：`communityCode` 解析为 `communityId` 后保存到 `t_ticket.community_id`。
2. 外部建单不依赖 JWT 登录态，不读取 `SecurityContext` 中的当前用户和当前小区。
3. 外部建单默认不直接指派执行人，避免绕过本系统派单权限和调度规则。
4. 初始状态建议由外部系统配置决定：
   - `confirm-required=true`：创建为 `PENDING_CONFIRM`，由本系统人工确认。
   - `confirm-required=false`：创建为 `PENDING_DISPATCH`，进入待派单队列。
5. `externalTicketNo` 在同一 `externalUid` 下必须唯一，用于防止外部系统超时重试导致重复建单。
6. 服务端日志必须记录 `externalUid`、`externalTicketNo`、`communityCode`、`ticketNo` 和耗时。

## 8. 后端实现建议

现有 `TicketCreateDTO` 已包含标题、描述、位置、部门、上报人、截止时间等工单字段，但没有 `communityCode/communityId`。因此外部建单不能直接无改造调用现有 `TicketService.createTicket(TicketCreateDTO)`，否则会走当前登录态小区或默认小区。

推荐实现方式：

1. 新增 `ExternalTicketController`，路径为 `/api/external/v1/tickets`。
2. 在 `SecurityConfig` 中放行 `/api/external/**`，由外部接口内部自行校验 `UID`。
3. 新增 `ExternalTicketCreateDTO`，包含本文档定义的请求字段。
4. 新增 `ExternalTicketProperties` 读取外部系统配置。
5. 新增 `ExternalTicketService`：
   - 校验 `X-External-Uid` 是否存在且已启用。
   - 校验 `communityCode` 是否存在、启用且在调用方白名单中。
   - 解析部门和上报人。
   - 组装内部建单对象。
   - 调用可传入 `communityId` 的工单创建逻辑。
6. 将工单创建核心逻辑从 `TicketServiceImpl.createTicket` 抽取为可复用私有方法，或新增专用方法，例如：

```java
TicketVO createExternalTicket(TicketCreateDTO dto, Long communityId, ExternalTicketContext context);
```

7. 为幂等能力新增数据库字段和唯一索引，建议字段：
   - `external_uid`
   - `external_ticket_no`
   - 唯一索引：`uk_ticket_external_uid_no(external_uid, external_ticket_no)`
8. 如果新增数据库字段，必须通过 Flyway 迁移脚本完成，并同步更新 `config_db_changes.md`。

## 9. 与现有字段映射

| 外部字段 | 内部字段 | 说明 |
| --- | --- | --- |
| `communityCode` | `t_ticket.community_id` | 先查 `t_community.code`，再保存 ID。 |
| `externalTicketNo` | 建议新增 `t_ticket.external_ticket_no` | 用于幂等和追踪。 |
| `title` | `t_ticket.title` | 必填。 |
| `description` | `t_ticket.description` | 可附加外部系统备注。 |
| `location` | `t_ticket.location` | 必填。 |
| `deptCode` | `t_ticket.dept_id` / `t_ticket.dept_name` | 先查 `t_department.code`。 |
| `deptId` | `t_ticket.dept_id` | 可选。 |
| `deptName` | `t_ticket.dept_name` | 可选。 |
| `reporterId` | `t_ticket.reporter_id` | 可选。 |
| `reporterName` | `t_ticket.reporter_name` | 条件必填。 |
| `ticketType` | `t_ticket.ticket_type` | 可选。 |
| `riskLevel` | `t_ticket.risk_level` | 可选枚举。 |
| `priority` | `t_ticket.priority` | 可选枚举。 |
| `deadline` | `t_ticket.deadline` | 可选。 |

## 10. 调用方注意事项

1. 每次请求都应传 `externalTicketNo`，并保证同一外部系统内稳定不变。
2. 超时后可以使用同一个 `externalTicketNo` 重试，服务端应返回已有工单而不是重复创建。
3. 外部系统应从服务端调用本接口，不建议把外部建单能力直接暴露给浏览器页面。
4. 多小区外部系统必须准确传 `communityCode`。
5. 生产环境如需停用某个外部系统，可将该系统配置的 `enabled` 改为 `false`，或修改/删除对应 `uid`。
