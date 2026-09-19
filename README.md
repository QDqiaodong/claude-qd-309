# 洗车房 · 工位与洗车单

洗车房的日常台账：**工位**、**洗车单**、**耗材**、**会员卡**。

业务重点：
- **状态用枚举写**（`enums/WashState` 等），状态机规则也放在枚举上（`canMoveTo`），
  洗车单只能「待洗 → 清洗中 → 已完成」一步步走，跳步和回退都会被拦；
- **耗材出库**封装在实体里（`Supply.consume`），不够就不给用，见底自动标成已用完；
- **会员卡扣费**（`MemberCard.pay`）：停卡或余额不足都扣不动。

## 技术栈

- 后端：Spring Boot 3.3 / Java 17、Spring Data JPA（**状态一律用枚举 + `@Enumerated(EnumType.STRING)`**）、
  MySQL 8、Redis 7
- 前端：Vue 3（Options API，页面逻辑主要靠 computed 串起来）+ Element Plus + Vite
- 一键起：`./start.sh`

## 业务模块

1. **工位**（`bay`）—— 编号名称、同时容纳、空闲/占用/停用
2. **洗车单**（`wash_order`）—— 单号车牌、工位、服务与金额、三态流转
3. **耗材**（`supply`）—— 编号名称单位、库存与预警线、正常/不足/已用完
4. **会员卡**（`member_card`）—— 卡号持卡人、余额与等级、正常与停卡

## 本地跑起来

| | 地址 |
| --- | --- |
| 前端页面 | http://127.0.0.1:8239/ |
| 后端接口 | http://127.0.0.1:8339/api/orders |
| MySQL | 127.0.0.1:3539（库 `car_wash`） |
| Redis | 127.0.0.1:6539 |

容器名统一是 `claude-qd-309-{mysql,redis,backend,frontend}`。

```bash
./start.sh              # 起容器
docker compose ps       # 看状态
docker compose down -v  # 停掉并清数据
```
