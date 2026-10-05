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
import com.fc.v2.mapper.auto.TMhOrderExecMapper;
import com.fc.v2.mapper.auto.TMhOrderMapper;
import com.fc.v2.mapper.auto.TMhOrderTransferMapper;
import com.fc.v2.mapper.auto.TMhTriaRuleMapper;
import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.service.impl.TMhOrderServiceImpl;
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
    protected TMhOrderMapper orderMapper;
    protected TMhOrderExecMapper orderExecMapper;
    protected TMhOrderTransferMapper orderTransferMapper;
    protected TMhTriaRuleServiceImpl triaRuleService;
    protected TMhVisitRowServiceImpl visitRowService;
    protected TMhOrderServiceImpl orderService;

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
        configuration.addMapper(TMhOrderMapper.class);
        configuration.addMapper(TMhOrderExecMapper.class);
        configuration.addMapper(TMhOrderTransferMapper.class);

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
        this.orderMapper = session.getMapper(TMhOrderMapper.class);
        this.orderExecMapper = session.getMapper(TMhOrderExecMapper.class);
        this.orderTransferMapper = session.getMapper(TMhOrderTransferMapper.class);

        this.triaRuleService = new TMhTriaRuleServiceImpl();
        inject(this.triaRuleService, "mhTriaRuleMapper", this.triaRuleMapper);

        this.visitRowService = new TMhVisitRowServiceImpl();
        inject(this.visitRowService, "mhVisitRowMapper", this.visitRowMapper);
        inject(this.visitRowService, "triaRuleService", this.triaRuleService);

        this.orderService = newOrderService(this.orderMapper, this.orderExecMapper, this.orderTransferMapper);
    }

    /** 另起一条会话装一套服务：模拟交接班两个护士各开各的连接同时提交 */
    protected TMhOrderServiceImpl newOrderServiceOnNewSession(SqlSession[] holder) throws Exception {
        SqlSession s = factory.openSession(true);
        holder[0] = s;
        return newOrderService(s.getMapper(TMhOrderMapper.class),
                s.getMapper(TMhOrderExecMapper.class), s.getMapper(TMhOrderTransferMapper.class));
    }

    private static TMhOrderServiceImpl newOrderService(TMhOrderMapper om, TMhOrderExecMapper oem,
                                                       TMhOrderTransferMapper otm) throws Exception {
        TMhOrderServiceImpl svc = new TMhOrderServiceImpl();
        inject(svc, "mhOrderMapper", om);
        inject(svc, "mhOrderExecMapper", oem);
        inject(svc, "mhOrderTransferMapper", otm);
        return svc;
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
