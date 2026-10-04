package com.fc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Scanner;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TMhTriaRuleMapper;
import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.service.impl.TMhTriaRuleServiceImpl;
import com.fc.v2.service.impl.TMhVisitRowServiceImpl;

/**
 * 只装配 MyBatis-Plus + H2 内存库（MODE=MySQL），不启 Web/Shiro/Redis 容器，
 * 每个用例前重建库并灌固定种子。
 */
public abstract class AbstractH2Test {

    protected SqlSessionFactory factory;
    protected SqlSession session;
    protected TMhTriaRuleMapper triaRuleMapper;
    protected TMhVisitRowMapper visitRowMapper;
    protected TMhTriaRuleServiceImpl triaRuleService;
    protected TMhVisitRowServiceImpl visitRowService;

    @BeforeEach
    void setUp() throws Exception {
        DataSource ds = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:triage;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

        runScript(ds, "sql/schema-h2.sql");
        runScript(ds, "sql/data-h2.sql");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TMhTriaRuleMapper.class);
        configuration.addMapper(TMhVisitRowMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());

        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);
        bean.setConfiguration(configuration);
        bean.setGlobalConfig(globalConfig);
        this.factory = bean.getObject();

        this.session = factory.openSession(true);
        this.triaRuleMapper = session.getMapper(TMhTriaRuleMapper.class);
        this.visitRowMapper = session.getMapper(TMhVisitRowMapper.class);

        this.triaRuleService = new TMhTriaRuleServiceImpl();
        inject(this.triaRuleService, "mhTriaRuleMapper", this.triaRuleMapper);

        this.visitRowService = new TMhVisitRowServiceImpl();
        inject(this.visitRowService, "mhVisitRowMapper", this.visitRowMapper);
        inject(this.visitRowService, "triaRuleService", this.triaRuleService);
    }

    @AfterEach
    void tearDown() {
        if (this.session != null) {
            this.session.close();
        }
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void runScript(DataSource ds, String classpath) throws Exception {
        String sql;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("缺少测试脚本：" + classpath);
            }
            try (Scanner sc = new Scanner(in, StandardCharsets.UTF_8.name())) {
                sql = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
            }
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.trim().isEmpty()) {
                    st.execute(stmt);
                }
            }
        }
    }
}
