# SignageHub 部署、启动与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

先运行`python3 scripts/init-env.py`生成私有.env，再校验Compose并以独立项目名构建启动。数据库MySQL8.4为专属持久卷，媒体为另一个专属卷；健康检查实际通过TCP执行SELECT 1。后端等待数据库，前端等待后端。应用同时执行Flyway和JPA结构校验；Maven镜像构建保留全部测试。

默认服务仅绑定127.0.0.1:8170。公网应在前方配置可信HTTPS反向代理，并将COOKIE_SECURE设为true；只有完成TLS后才对外开放。BIND_ADDRESS和WEB_PORT可覆盖，保持数据库和本机播放器设置端口私有。不要把其他设备上的localhost当成服务器地址。

前端健康检查通过Nginx访问后端及数据库的就绪接口。启动或重启后运行`python3 scripts/readiness.py`，确认实际网关已就绪再登录；自定义端口用`--base http://127.0.0.1:端口`。该检查只重试健康GET，不重试业务写入。

电脑播放器运行`node src/launcher.js`，需要Node24.19+和现代浏览器。稳定设置PLAYER_DATA_DIR；启动位置改变后不要意外使用另一份缓存。运行无需npm安装，源码测试需要npm ci。设置页输入明确HTTPS后台地址和一次性设备令牌；媒体默认静音，画布方向只改变逻辑画布，不控制显示器旋转。

操作系统服务文件生成示例（在项目目录运行，替换为目标机器的真实路径）：

```sh
python3 scripts/player-service.py --platform macos --node /absolute/path/to/node --player-dir /absolute/path/to/player --data-dir /absolute/private/player-data --output output/player.plist
python3 scripts/player-service.py --platform systemd --node /absolute/path/to/node --player-dir /absolute/path/to/player --data-dir /absolute/private/player-data --output output/player.service
```

生成器不安装服务、不改变OS设置、不保存系统密码。先按相同PLAYER_DATA_DIR手动运行播放器一次，确认私有缓存目录已创建且可写，再安装服务；macOS需在启动前打开该目录内的日志文件。macOS文件包含RunAtLoad/KeepAlive，Linux为用户级systemd服务；安装/卸载请由设备操作员按平台文档执行并实际验收。Windows需在目标Windows机器上生成任务XML，使用player/player-data及8171默认端口。浏览器登录启动、无人值守全屏及设备电源另按实际设备配置；仅有启动文件不能证明这些已经完成。

升级先做一致性备份，再更新镜像并启动等待健康。后端重启不会覆盖账号或修改已发布快照；播放器进程重启校验磁盘缓存。发布内容与媒体应保持在同一备份点。

`backup.py --project ... --output ...`在停止后台写入期间导出SQL和媒体，最终恢复后台运行。文件0600，不含.env或设备端config.json；环境文件须另行安全保留。数据库与媒体完整性摘要记录在归档中。单成员支持最多1GB。

`restore.py backup.zip --project signagehub-restore --env-file restore.env`要求新项目、新空数据库/媒体卷、独立端口与127.0.0.1绑定。先检查ZIP/TAR成员、路径、大小、类型及摘要，并构建目标项目镜像，启动MySQL并等正式TCP服务就绪，再还原SQL和素材，最后启动完整系统。后台账号/设备摘要随数据库恢复，私有播放器配置单独保留。使用`verify-persistence.py --base ... --state ...`核对原有节目、设备与实际素材。

软件源下载失败应读取构建日志。Dockerfile使用Ubuntu官方HTTPS源并保留签名检查和有限重试，不关闭验证、不跳过测试、不在未启动时发布。清理只针对自己的测试项目；勿删除共享缓存、其他项目或生产卷。
