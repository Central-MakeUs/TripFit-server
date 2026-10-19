package com.tripfit.tripfit.user.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

// Hibernate가 DB로 보내는 SQL을 순서대로 기록한다. 잠금 순서를 실제 실행 순서로 확인하는 테스트에서만 쓴다.
public class RecordingStatementInspector implements StatementInspector {

  static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

  static volatile boolean recording;

  @Override
  public String inspect(String sql) {
    if (recording) {
      STATEMENTS.add(sql);
    }
    return sql;
  }
}
