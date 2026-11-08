package com.planner.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 不需要数据库：解析全部 Mapper XML，检查语法/类型别名能解析，并且每个接口方法都有同名 SQL 语句。 */
class MapperXmlTest {
    private static final List<Class<?>> MAPPERS = List.of(
            UserMapper.class, AssessmentRecordMapper.class, UserProfileMapper.class,
            AdviceRecordMapper.class, ChatSessionMapper.class, ChatMessageMapper.class);

    @Test
    void everyMapperMethodHasAMatchingStatement() throws Exception {
        Configuration c = new Configuration();
        Resource[] xmls = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml");
        assertEquals(MAPPERS.size(), xmls.length, "Mapper XML 数量与接口数量不一致");
        for (Resource r : xmls) {
            try (InputStream in = r.getInputStream()) {
                new XMLMapperBuilder(in, c, r.toString(), c.getSqlFragments()).parse();
            }
        }
        for (Class<?> m : MAPPERS) {
            for (Method method : m.getDeclaredMethods()) {
                if (method.isDefault() || method.isSynthetic()) continue;
                String id = m.getName() + "." + method.getName();
                assertTrue(c.hasStatement(id), "缺少 SQL 语句: " + id);
            }
        }
    }
}
