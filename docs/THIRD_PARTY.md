# SignageHub 第三方说明

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

知华非商业源码许可只覆盖自有代码。第三方的版权和许可独立适用，不自动转为知华许可，也不因组件的宽松许可而允许免费商用知华自有源码。

|组件|使用方式|许可与来源|
|---|---|---|
|Vue3|浏览器运行依赖|MIT，原文[vue.txt](licenses/vue.txt)，[官方项目](https://github.com/vuejs/core)|
|Vite|前端构建|MIT，原文[vite.txt](licenses/vite.txt)，[官方项目](https://github.com/vitejs/vite)|
|MariaDB Connector/J|JDBC连接MySQL|LGPL2.1+，原文[mariadb-connector-j.txt](licenses/mariadb-connector-j.txt)，[官方项目](https://github.com/mariadb-corporation/mariadb-connector-j)|
|Spring Boot/Security/JPA/Flyway等|Java服务依赖，版本由pom锁定|各自Apache/LGPL等原许可及依赖声明，保留Maven元数据|
|Node.js|独立播放器运行时|官方Node分发自带完整许可，播放器运行代码仅使用标准库|
|ffprobe/FFmpeg|发行版外部进程，验证视频；QA生成测试视频|运行镜像包的原始[copyright](licenses/ffmpeg-package.txt)说明其默认二进制为GPL2+；组件原文及[官方许可说明](https://ffmpeg.org/legal.html)独立适用，再分发二进制时须履行对应源码和版权义务|
|MySQL、Nginx、Maven、Ubuntu基础镜像|Docker运行及构建环境|官方发行包的独立许可证；不得删除版权或以知华许可覆盖|

仓库提供源码和Dockerfile。FFmpeg通过发行版包安装，未修改或链接其源码；二进制再分发须保留实际构建的许可、对应源包与版权，不得据自有非商业许可宣称对第三方拥有排他权。发行版包许可保留在运行镜像的/usr/share/doc内。

正式LOGO和两个中文咨询二维码放在frontend/public/brand；图片内容保持原样。节目播放内容由操作者提供，系统不往业务文本、导出或日志插入品牌广告。
