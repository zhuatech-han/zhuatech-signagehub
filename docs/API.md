# SignageHub API

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

员工接口为同源`/api`，使用HttpOnly/SameSite=Strict会话；先GET`/auth/csrf`取得请求头/令牌，写入携带该头。登录后重新取得CSRF。客户端不自动重放写请求，连接丢失时核对持久化结果。

|接口|实际用途|
|---|---|
|GET /auth/csrf；POST /auth/login /auth/logout /auth/password；GET /auth/me|身份及会话|
|GET /catalog|按岗位/组织裁剪的业务选项|
|POST /media|multipart字段departmentId、name、file；格式、大小、配额验证|
|GET /media/{id}/content；PUT/DELETE /media/{id}|受权限保护预览、名称/启停和未引用删除|
|POST /groups /screens；PUT/DELETE /groups/{id} /screens/{id}|同组织配置与历史保护|
|POST /screens/{id}/rotate|携带version，返回一次性token，列表永不返回token或tokenHash|
|POST /playlists；GET/PUT/DELETE /playlists/{id}|完整有序草稿，已有历史不得删除|
|POST /playlists/{id}/publish|requestId、playlistVersion、screenIds、groupIds，可选startsAt/endsAt|
|GET /releases/{id}|不可变内容快照|
|POST /schedules/{id}/cancel|version保护的撤销|
|POST /screens/{id}/rollback|version、releaseId，只接受该屏幕历史版本|
|GET /reports /reports.csv /audit|组织范围内的实际状态、CSV和审计|
|GET/POST /admin/{kind}；PUT /admin/{kind}/{id}|账号/角色/组织/菜单/参数/字典，管理权限及ALL范围|

发布requestId必须规范UUID。相同请求的原文参数、账号及节目绑定相同时恢复原快照；换内容或目标不能复用同一ID。屏幕/分组成员名单在第一次成功发布时冻结。保存/更新版本为服务端当前版本，旧版本返回VERSION_CONFLICT。

`/catalog`中版本摘要的`screenIds`为实际投放历史，用于筛选该屏幕可回滚的版本；它不随分组成员调整而改变。

设备接口为`/api/player`，使用`Authorization: Bearer ...`，不能把令牌放入URL。员工登录不会自动授予设备访问。这些设备接口免会话CSRF，但逐次检查设备摘要、启用状态及组织。

|设备接口|规则|
|---|---|
|GET /manifest|schema=1，screenId/name、revision、serverTime、validUntil、programs；常驻及未来七天排期，最多72小时租期|
|GET /media/{id}|该设备已经收到的发布与素材关联；不得读其他设备或组织的任意文件|
|POST /heartbeat|state、appliedRelease、error；实际版本须是该屏幕已有投放，状态和错误字符串有白名单/长度检查|

节目快照包括id、名称、画布、适配、背景和有序items。媒体项包括assetId、SHA-256、bytes、mime、kind、尺寸、时长及固定相对API地址；文字项为纯文本。客户端拒绝未知版本、重叠时段、超量节目、异站URL和校验失败。

本机播放器仅在127.0.0.1提供`/local/status`、`/local/program`、`/local/config`、`/local/sync`、`/local/ack`。写入需本机CSRF头，Host与Origin受限；状态不返回设备令牌和私有路径。`/media/{sha}.{ext}`仅可读取当前未过期节目，支持受限单段HTTP Range。

错误只返回安全code/error，不返回SQL、路径或凭据。401要求重新登录/配对，403表示授权不足或屏幕禁用，409表示版本、状态、排期和引用冲突。报表或心跳不构成广告曝光量、计费或客户成交证据。
