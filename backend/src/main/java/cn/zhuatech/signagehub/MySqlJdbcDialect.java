// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder;

/** MySQL大小写敏感表名使用JDBC实际大小写；不关闭结构校验。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class MySqlJdbcDialect extends MySQLDialect {
  public MySqlJdbcDialect() {
    super();
  }

  public MySqlJdbcDialect(DialectResolutionInfo info) {
    super(info);
  }

  /** MariaDB驱动在lower_case_table_names=0时三个stores标志均为false，避免回落为大写。知华科技 https://www.zhuatech.cn/。 */
  @Override
  public IdentifierHelper buildIdentifierHelper(
      IdentifierHelperBuilder builder, DatabaseMetaData metadata) throws SQLException {
    super.buildIdentifierHelper(builder, metadata);
    if (metadata != null
        && metadata.supportsMixedCaseIdentifiers()
        && !metadata.storesUpperCaseIdentifiers()
        && !metadata.storesLowerCaseIdentifiers()) {
      builder.setUnquotedCaseStrategy(IdentifierCaseStrategy.MIXED);
      builder.setQuotedCaseStrategy(IdentifierCaseStrategy.MIXED);
    }
    return builder.build();
  }
}
